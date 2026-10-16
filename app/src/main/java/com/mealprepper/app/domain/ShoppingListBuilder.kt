package com.mealprepper.app.domain

import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.model.ShoppingItemEntity
import kotlin.math.ceil
import kotlin.math.roundToLong

/** One cooked batch to shop for: [servings] of [recipe] (leftover meals must NOT be passed). */
data class CookRequest(val recipe: RecipeWithIngredients, val servings: Int)

/**
 * Merges ingredients of many recipes into one consolidated shopping list. Pure Kotlin.
 *
 * - Quantities are scaled by `servings / recipe.servings` (ingredient amounts are per recipe).
 * - Names are normalized (trimmed, lowercased, whitespace collapsed).
 * - Compatible units are summed in a base unit (mass → grams, volume → millilitres) and shown in
 *   a friendly unit (oz → lb at 16 oz, tsp → tbsp at 3 tsp, tbsp → cup at 4 tbsp; metric stays
 *   metric if every source was metric). Count units are summed per unit and rounded up.
 * - Incompatible units for the same name (e.g. garlic "clove" vs "tsp") stay separate lines.
 * - Output is sorted by [Aisle] order, then name.
 */
object ShoppingListBuilder {

    private enum class Dim { MASS, VOLUME, COUNT, OTHER }

    private val massToG = mapOf("g" to 1.0, "kg" to 1000.0, "oz" to 28.349523125, "lb" to 453.59237)
    private val volToMl = mapOf(
        "ml" to 1.0, "l" to 1000.0,
        "tsp" to 4.92892159375, "tbsp" to 14.78676478125, "cup" to 236.5882365,
    )
    private val countUnits = setOf("whole", "clove", "slice", "can", "bunch", "pinch", "pack", "jar")
    private val unitAliases = mapOf(
        "gram" to "g", "grams" to "g", "kilogram" to "kg", "kilograms" to "kg",
        "ounce" to "oz", "ounces" to "oz", "pound" to "lb", "pounds" to "lb", "lbs" to "lb",
        "milliliter" to "ml", "milliliters" to "ml", "liter" to "l", "liters" to "l",
        "teaspoon" to "tsp", "teaspoons" to "tsp", "tablespoon" to "tbsp", "tablespoons" to "tbsp",
        "cups" to "cup", "cloves" to "clove", "slices" to "slice", "cans" to "can",
        "bunches" to "bunch", "packs" to "pack", "jars" to "jar", "pinches" to "pinch",
        "each" to "whole", "piece" to "whole", "pieces" to "whole", "" to "whole",
    )

    fun normalizeName(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun normalizeUnit(unit: String): String {
        val u = unit.trim().lowercase()
        return unitAliases[u] ?: u
    }

    /** True if [name] (normalized) is in [pantry] (normalized), tolerating a plural "s". */
    fun inPantry(name: String, pantry: Set<String>): Boolean {
        if (pantry.isEmpty()) return false
        val n = normalizeName(name)
        return n in pantry || "${n}s" in pantry || (n.endsWith("s") && n.dropLast(1) in pantry)
    }

    /**
     * Builds the consolidated list. Items keep `id = 0` (new rows).
     *
     * @param pantry pantry item names; matching ingredients are skipped when [skipPantry].
     */
    fun build(
        requests: List<CookRequest>,
        pantry: Set<String> = emptySet(),
        skipPantry: Boolean = true,
    ): List<ShoppingItemEntity> {
        val normalizedPantry = pantry.map { normalizeName(it) }.toSet()
        val acc = LinkedHashMap<String, Acc>()
        for (req in requests) {
            if (req.servings <= 0) continue
            val recipe = req.recipe.recipe
            val scale = req.servings.toDouble() / recipe.servings.coerceAtLeast(1)
            for (ing in req.recipe.ingredients.sortedBy { it.position }) {
                val name = normalizeName(ing.name)
                if (name.isEmpty()) continue
                if (skipPantry && inPantry(name, normalizedPantry)) continue
                add(acc, name, ing, scale, recipe.title)
            }
        }
        return acc.values.map { it.toItem() }.sortedWith(itemOrder)
    }

    /**
     * Merges [additions] into [existing] (e.g. "add this recipe to my list"). Lines with the same
     * name and a compatible unit are summed; a merged line is un-checked since more is needed.
     * Existing ids are kept so the caller can update rows in place; new lines have `id = 0`.
     */
    fun merge(
        existing: List<ShoppingItemEntity>,
        additions: List<ShoppingItemEntity>,
    ): List<ShoppingItemEntity> {
        val result = existing.toMutableList()
        for (add in additions) {
            val addName = normalizeName(add.name)
            val addKey = key(addName, normalizeUnit(add.unit))
            val idx = result.indexOfFirst {
                !it.isManual && normalizeName(it.name) == addName &&
                    key(addName, normalizeUnit(it.unit)) == addKey
            }
            if (idx < 0) {
                result += add
                continue
            }
            val cur = result[idx]
            val a = Acc(addName, cur.aisle, cur.note)
            a.addRaw(cur.quantity, normalizeUnit(cur.unit), cur.estPriceUsd, cur.forRecipes)
            a.addRaw(add.quantity, normalizeUnit(add.unit), add.estPriceUsd, add.forRecipes)
            val merged = a.toItem()
            result[idx] = cur.copy(
                name = merged.name,
                quantity = merged.quantity,
                unit = merged.unit,
                estPriceUsd = merged.estPriceUsd,
                forRecipes = merged.forRecipes,
                checked = false,
            )
        }
        return result.sortedWith(itemOrder)
    }

    val itemOrder: Comparator<ShoppingItemEntity> =
        compareBy<ShoppingItemEntity>({ it.aisle.ordinal }, { normalizeName(it.name) }, { it.unit })

    // ---------------------------------------------------------------- internals

    private fun dimOf(unit: String): Dim = when (unit) {
        in massToG -> Dim.MASS
        in volToMl -> Dim.VOLUME
        in countUnits -> Dim.COUNT
        else -> Dim.OTHER
    }

    private fun key(name: String, unit: String): String = when (dimOf(unit)) {
        Dim.MASS -> "$name|mass"
        Dim.VOLUME -> "$name|volume"
        Dim.COUNT, Dim.OTHER -> "$name|$unit"
    }

    private fun add(acc: MutableMap<String, Acc>, name: String, ing: IngredientEntity, scale: Double, title: String) {
        val unit = normalizeUnit(ing.unit)
        val a = acc.getOrPut(key(name, unit)) { Acc(name, ing.aisle, ing.note) }
        if (a.note == null && ing.note != null) a.note = ing.note
        a.addRaw(ing.quantity * scale, unit, ing.estPriceUsd * scale, listOf(title))
    }

    private class Acc(val name: String, val aisle: Aisle, var note: String?) {
        var base = 0.0 // grams, millilitres, or the count/other unit itself
        var unit: String? = null
        var allMetric = true
        var price = 0.0
        val recipes = LinkedHashSet<String>()

        fun addRaw(quantity: Double, unit: String, priceUsd: Double, forRecipes: List<String>) {
            val q = if (quantity.isFinite() && quantity > 0) quantity else 0.0
            when (dimOf(unit)) {
                Dim.MASS -> {
                    base += q * massToG.getValue(unit)
                    if (unit != "g" && unit != "kg") allMetric = false
                }
                Dim.VOLUME -> {
                    base += q * volToMl.getValue(unit)
                    if (unit != "ml" && unit != "l") allMetric = false
                }
                Dim.COUNT, Dim.OTHER -> base += q
            }
            if (this.unit == null) this.unit = unit
            if (priceUsd.isFinite()) price += priceUsd
            recipes.addAll(forRecipes)
        }

        fun toItem(): ShoppingItemEntity {
            val u = unit ?: "whole"
            val (qty, displayUnit) = when (dimOf(u)) {
                Dim.MASS -> displayMass(base, allMetric)
                Dim.VOLUME -> displayVolume(base, allMetric)
                Dim.COUNT -> ceilTo(base, 1.0).coerceAtLeast(1.0) to u
                Dim.OTHER -> ceilTo(base, 0.25) to u
            }
            return ShoppingItemEntity(
                name = name,
                quantity = qty,
                unit = displayUnit,
                aisle = aisle,
                estPriceUsd = (price * 100.0).roundToLong() / 100.0,
                forRecipes = recipes.toList(),
                note = note,
            )
        }
    }

    private fun displayMass(grams: Double, metric: Boolean): Pair<Double, String> {
        if (metric) {
            return if (grams >= 1000.0) ceilTo(grams / 1000.0, 0.05) to "kg" else ceilTo(grams, 5.0) to "g"
        }
        val oz = grams / massToG.getValue("oz")
        return if (oz >= 16.0 - EPS) ceilTo(oz / 16.0, 0.25) to "lb" else ceilTo(oz, 0.5) to "oz"
    }

    private fun displayVolume(ml: Double, metric: Boolean): Pair<Double, String> {
        if (metric) {
            return if (ml >= 1000.0) ceilTo(ml / 1000.0, 0.05) to "l" else ceilTo(ml, 5.0) to "ml"
        }
        val tsp = ml / volToMl.getValue("tsp")
        return when {
            tsp < 3.0 - EPS -> ceilTo(tsp, 0.25) to "tsp"
            tsp < 12.0 - EPS -> ceilTo(tsp / 3.0, 0.25) to "tbsp" // < 4 tbsp
            else -> ceilTo(tsp / 48.0, 0.25) to "cup"
        }
    }

    private const val EPS = 1e-6

    /** Rounds up to a multiple of [step], ignoring floating-point noise (2.0000001 → 2.0). */
    private fun ceilTo(value: Double, step: Double): Double {
        if (value <= 0.0) return 0.0
        val steps = ceil(value / step - 1e-6)
        val r = steps * step
        return (r * 1000.0).roundToLong() / 1000.0
    }
}
