package com.mealprepper.app.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.data.model.Units
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Shared list helpers (also used by Store mode)
// ---------------------------------------------------------------------------------------------

data class AisleGroup(
    val aisle: Aisle,
    /** Unchecked first (alphabetical), then checked (alphabetical). */
    val items: List<ShoppingItemEntity>,
) {
    val remaining: Int get() = items.count { !it.checked }
}

internal fun groupByAisle(items: List<ShoppingItemEntity>): List<AisleGroup> {
    val byAisle = items.groupBy { it.aisle }
    return Aisle.entries.mapNotNull { aisle ->
        val list = byAisle[aisle] ?: return@mapNotNull null
        AisleGroup(
            aisle = aisle,
            items = list.sortedWith(compareBy<ShoppingItemEntity>({ it.checked }, { it.name.lowercase() })),
        )
    }
}

/** A quick-add entry parsed from text like "2 lb chicken breast" or "3 avocados". */
data class ParsedItem(val name: String, val quantity: Double, val unit: String, val aisle: Aisle)

private val unitAliases: Map<String, String> = mapOf(
    "lbs" to "lb", "pound" to "lb", "pounds" to "lb",
    "ounce" to "oz", "ounces" to "oz",
    "gram" to "g", "grams" to "g",
    "kilogram" to "kg", "kilograms" to "kg", "kilo" to "kg", "kilos" to "kg",
    "milliliter" to "ml", "milliliters" to "ml",
    "liter" to "l", "liters" to "l", "litre" to "l", "litres" to "l",
    "teaspoon" to "tsp", "teaspoons" to "tsp",
    "tablespoon" to "tbsp", "tablespoons" to "tbsp", "tbs" to "tbsp",
    "cups" to "cup",
    "cans" to "can",
    "cloves" to "clove",
    "slices" to "slice",
    "bunches" to "bunch",
    "pinches" to "pinch",
    "packs" to "pack", "package" to "pack", "packages" to "pack", "pkg" to "pack",
    "jars" to "jar",
)

private fun normalizeUnit(token: String): String? {
    val t = token.lowercase().trimEnd('.')
    val mapped = unitAliases[t] ?: t
    return if (mapped in Units.ALL) mapped else null
}

private val numberRegex = Regex("""^(\d+(?:[.,]\d+)?|\d+/\d+)([a-zA-Z]*)$""")

private fun parseNumber(text: String): Double? {
    if (text.contains('/')) {
        val parts = text.split('/')
        val a = parts.getOrNull(0)?.toDoubleOrNull() ?: return null
        val b = parts.getOrNull(1)?.toDoubleOrNull() ?: return null
        return if (b == 0.0) null else a / b
    }
    return text.replace(',', '.').toDoubleOrNull()
}

private fun singularizeLastWord(name: String): String {
    val words = name.split(' ').toMutableList()
    val last = words.lastOrNull() ?: return name
    val keep = setOf("hummus", "asparagus", "couscous", "oats", "greens", "molasses", "swiss", "brussels")
    val singular = when {
        last in keep -> last
        last.endsWith("ies") && last.length > 4 -> last.dropLast(3) + "y"
        last.endsWith("oes") && last.length > 4 -> last.dropLast(2)
        last.endsWith("s") && !last.endsWith("ss") && !last.endsWith("us") && !last.endsWith("is") &&
            last.length > 3 -> last.dropLast(1)
        else -> last
    }
    words[words.size - 1] = singular
    return words.joinToString(" ")
}

/** Ordered: multi-word phrases first so "peanut butter" beats "butter", "black pepper" beats "pepper". */
private val aisleKeywords: List<Pair<String, Aisle>> = listOf(
    "frozen" to Aisle.FROZEN,
    "ice cream" to Aisle.FROZEN,
    "peanut butter" to Aisle.PANTRY,
    "almond butter" to Aisle.PANTRY,
    "protein powder" to Aisle.PANTRY,
    "olive oil" to Aisle.PANTRY,
    "black pepper" to Aisle.SPICES,
    "pepper flakes" to Aisle.SPICES,
    "chili flakes" to Aisle.SPICES,
    "chili powder" to Aisle.SPICES,
    "garlic powder" to Aisle.SPICES,
    "soy sauce" to Aisle.SPICES,
    "hot sauce" to Aisle.SPICES,
    "tomato sauce" to Aisle.CANNED,
    "tomato paste" to Aisle.CANNED,
    "canned" to Aisle.CANNED,
    "bell pepper" to Aisle.PRODUCE,
    "sweet potato" to Aisle.PRODUCE,
    "green onion" to Aisle.PRODUCE,
    "almond milk" to Aisle.DAIRY_EGGS,
    "greek yogurt" to Aisle.DAIRY_EGGS,
    "cottage cheese" to Aisle.DAIRY_EGGS,
    "ground beef" to Aisle.MEAT_SEAFOOD,
    "ground turkey" to Aisle.MEAT_SEAFOOD,
    // Single words
    "chicken" to Aisle.MEAT_SEAFOOD, "beef" to Aisle.MEAT_SEAFOOD, "turkey" to Aisle.MEAT_SEAFOOD,
    "pork" to Aisle.MEAT_SEAFOOD, "salmon" to Aisle.MEAT_SEAFOOD, "tuna" to Aisle.MEAT_SEAFOOD,
    "shrimp" to Aisle.MEAT_SEAFOOD, "steak" to Aisle.MEAT_SEAFOOD, "fish" to Aisle.MEAT_SEAFOOD,
    "cod" to Aisle.MEAT_SEAFOOD, "tilapia" to Aisle.MEAT_SEAFOOD, "sausage" to Aisle.MEAT_SEAFOOD,
    "bacon" to Aisle.MEAT_SEAFOOD, "ham" to Aisle.MEAT_SEAFOOD, "lamb" to Aisle.MEAT_SEAFOOD,
    "milk" to Aisle.DAIRY_EGGS, "yogurt" to Aisle.DAIRY_EGGS, "egg" to Aisle.DAIRY_EGGS,
    "cheese" to Aisle.DAIRY_EGGS, "butter" to Aisle.DAIRY_EGGS, "cream" to Aisle.DAIRY_EGGS,
    "feta" to Aisle.DAIRY_EGGS, "parmesan" to Aisle.DAIRY_EGGS, "mozzarella" to Aisle.DAIRY_EGGS,
    "kefir" to Aisle.DAIRY_EGGS, "tofu" to Aisle.DAIRY_EGGS,
    "bread" to Aisle.BAKERY, "tortilla" to Aisle.BAKERY, "bagel" to Aisle.BAKERY,
    "bun" to Aisle.BAKERY, "pita" to Aisle.BAKERY, "wrap" to Aisle.BAKERY,
    "rice" to Aisle.GRAINS, "pasta" to Aisle.GRAINS, "quinoa" to Aisle.GRAINS, "oats" to Aisle.GRAINS,
    "oat" to Aisle.GRAINS, "noodle" to Aisle.GRAINS, "couscous" to Aisle.GRAINS,
    "cereal" to Aisle.GRAINS, "granola" to Aisle.GRAINS, "spaghetti" to Aisle.GRAINS,
    "bean" to Aisle.CANNED, "chickpea" to Aisle.CANNED, "lentil" to Aisle.CANNED,
    "broth" to Aisle.CANNED, "stock" to Aisle.CANNED, "salsa" to Aisle.CANNED,
    "salt" to Aisle.SPICES, "cumin" to Aisle.SPICES, "paprika" to Aisle.SPICES,
    "oregano" to Aisle.SPICES, "cinnamon" to Aisle.SPICES, "sauce" to Aisle.SPICES,
    "mustard" to Aisle.SPICES, "ketchup" to Aisle.SPICES, "vinegar" to Aisle.SPICES,
    "seasoning" to Aisle.SPICES, "sriracha" to Aisle.SPICES, "spice" to Aisle.SPICES,
    "oil" to Aisle.PANTRY, "flour" to Aisle.PANTRY, "sugar" to Aisle.PANTRY, "honey" to Aisle.PANTRY,
    "almond" to Aisle.PANTRY, "walnut" to Aisle.PANTRY, "nut" to Aisle.PANTRY, "syrup" to Aisle.PANTRY,
    "water" to Aisle.BEVERAGES, "coffee" to Aisle.BEVERAGES, "tea" to Aisle.BEVERAGES,
    "juice" to Aisle.BEVERAGES, "soda" to Aisle.BEVERAGES, "seltzer" to Aisle.BEVERAGES,
    "avocado" to Aisle.PRODUCE, "banana" to Aisle.PRODUCE, "apple" to Aisle.PRODUCE,
    "berry" to Aisle.PRODUCE, "blueberry" to Aisle.PRODUCE, "strawberry" to Aisle.PRODUCE,
    "spinach" to Aisle.PRODUCE, "lettuce" to Aisle.PRODUCE, "kale" to Aisle.PRODUCE,
    "tomato" to Aisle.PRODUCE, "onion" to Aisle.PRODUCE, "garlic" to Aisle.PRODUCE,
    "potato" to Aisle.PRODUCE, "pepper" to Aisle.PRODUCE, "lemon" to Aisle.PRODUCE,
    "lime" to Aisle.PRODUCE, "cucumber" to Aisle.PRODUCE, "carrot" to Aisle.PRODUCE,
    "broccoli" to Aisle.PRODUCE, "zucchini" to Aisle.PRODUCE, "cilantro" to Aisle.PRODUCE,
    "parsley" to Aisle.PRODUCE, "basil" to Aisle.PRODUCE, "ginger" to Aisle.PRODUCE,
    "mushroom" to Aisle.PRODUCE, "celery" to Aisle.PRODUCE, "cabbage" to Aisle.PRODUCE,
    "orange" to Aisle.PRODUCE, "grape" to Aisle.PRODUCE, "mango" to Aisle.PRODUCE,
    "scallion" to Aisle.PRODUCE, "salad" to Aisle.PRODUCE, "eggplant" to Aisle.PRODUCE,
    "asparagus" to Aisle.PRODUCE, "cauliflower" to Aisle.PRODUCE, "peach" to Aisle.PRODUCE,
)

internal fun guessAisle(name: String): Aisle {
    val n = name.lowercase()
    val words = n.split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
    for ((kw, aisle) in aisleKeywords) {
        val matches = if (kw.contains(' ')) {
            n.contains(kw)
        } else {
            words.any { w ->
                w == kw || w == kw + "s" || w == kw + "es" ||
                    (kw.endsWith("y") && w == kw.dropLast(1) + "ies")
            }
        }
        if (matches) return aisle
    }
    return Aisle.OTHER
}

/** "2 avocado" → (avocado, 2, whole); "1.5 lb chicken breast" → (chicken breast, 1.5, lb); "2lb rice". */
internal fun parseQuickAdd(input: String): ParsedItem? {
    val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
    if (tokens.isEmpty()) return null
    var quantity = 1.0
    var unit = "whole"
    val first = numberRegex.matchEntire(tokens.first())
    if (first != null && tokens.size > 1) {
        val n = parseNumber(first.groupValues[1])
        val glued = first.groupValues[2]
        val gluedUnit = if (glued.isEmpty()) null else normalizeUnit(glued)
        if (n != null && n > 0.0 && (glued.isEmpty() || gluedUnit != null)) {
            quantity = n
            if (gluedUnit != null) unit = gluedUnit
            tokens.removeAt(0)
        }
    }
    if (tokens.size > 1 && unit == "whole") {
        val u = normalizeUnit(tokens.first())
        if (u != null) {
            unit = u
            tokens.removeAt(0)
        }
    }
    if (tokens.size > 1 && tokens.first().equals("of", ignoreCase = true)) tokens.removeAt(0)
    var name = tokens.joinToString(" ").lowercase().trim()
    if (name.isEmpty()) return null
    if (quantity > 1.0 || unit != "whole") name = singularizeLastWord(name)
    return ParsedItem(name = name, quantity = quantity, unit = unit, aisle = guessAisle(name))
}

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------

data class ShoppingUiState(
    val loading: Boolean = true,
    val groups: List<AisleGroup> = emptyList(),
    val totalItems: Int = 0,
    val checkedItems: Int = 0,
    val estTotalUsd: Double = 0.0,
    val remainingUsd: Double = 0.0,
    val budgetUsd: Int = 200,
) {
    val progress: Float get() = if (totalItems == 0) 0f else checkedItems.toFloat() / totalItems
    val overBudget: Boolean get() = estTotalUsd > budgetUsd
}

class ShoppingViewModel(
    private val meals: MealRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    /** Items swiped away but still undoable. Hidden locally until committed. */
    private val pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())

    val state: StateFlow<ShoppingUiState> = combine(
        meals.shoppingList(), profiles.profile, pendingDeletes,
    ) { items, profile, pending ->
        val visible = items.filter { it.id !in pending }
        ShoppingUiState(
            loading = false,
            groups = groupByAisle(visible),
            totalItems = visible.size,
            checkedItems = visible.count { it.checked },
            estTotalUsd = visible.sumOf { it.estPriceUsd },
            remainingUsd = visible.filter { !it.checked }.sumOf { it.estPriceUsd },
            budgetUsd = profile.weeklyBudgetUsd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingUiState())

    fun toggle(item: ShoppingItemEntity) {
        viewModelScope.launch { meals.setChecked(item.id, !item.checked) }
    }

    /** Returns true if this call started a new pending delete (show the undo snackbar). */
    fun requestDelete(itemId: Long): Boolean {
        var added = false
        pendingDeletes.update { current ->
            added = itemId !in current
            current + itemId
        }
        return added
    }

    fun undoDelete(itemId: Long) {
        pendingDeletes.update { it - itemId }
    }

    fun commitDelete(itemId: Long) {
        if (itemId !in pendingDeletes.value) return
        viewModelScope.launch {
            meals.deleteItem(itemId)
            pendingDeletes.update { it - itemId }
        }
    }

    /** Parses and adds; returns the parsed item (for feedback) or null when the text is blank. */
    fun quickAdd(text: String): ParsedItem? {
        val parsed = parseQuickAdd(text) ?: return null
        viewModelScope.launch {
            meals.addManualItem(parsed.name, parsed.quantity, parsed.unit, parsed.aisle)
        }
        return parsed
    }

    fun clearChecked() {
        viewModelScope.launch { meals.clearChecked() }
    }

    fun clearAll() {
        viewModelScope.launch { meals.clearList() }
    }

    suspend fun shareText(): String = meals.shoppingListAsText()
}
