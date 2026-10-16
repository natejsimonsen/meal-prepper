package com.mealprepper.app.ui.screens.recipes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.ui.components.FoodImageKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

/** Ids for recipes created on this device. */
internal fun newUserRecipeId(): String = "user-" + UUID.randomUUID().toString()

data class IngredientDraft(
    val key: Long,
    val name: String = "",
    val quantity: String = "1",
    val unit: String = "whole",
    val aisle: Aisle = Aisle.PRODUCE,
    val price: String = "",
    val note: String = "",
    val pantryStaple: Boolean = false,
)

data class StepDraft(val key: Long, val text: String = "")

data class EditorForm(
    val title: String = "",
    val subtitle: String = "",
    val description: String = "",
    val mealType: MealType = MealType.DINNER,
    val cuisine: String = "",
    val prepMinutes: String = "10",
    val cookMinutes: String = "15",
    val servings: String = "2",
    val calories: String = "",
    val protein: String = "",
    val carbs: String = "",
    val fat: String = "",
    val fiber: String = "",
    val difficulty: Int = 1,
    val imageKey: FoodImageKey = FoodImageKey.BOWL_GRAIN,
    val accentColor: String = ACCENT_SWATCHES.first(),
    val tags: List<String> = listOf("high-protein"),
    val mealPrepFriendly: Boolean = true,
    val fridgeDays: Int = 4,
    val ingredients: List<IngredientDraft> = emptyList(),
    val steps: List<StepDraft> = emptyList(),
) {
    /** kcal implied by the macros (4/4/9). */
    val computedCalories: Int?
        get() {
            val p = protein.toIntOrNullLenient()
            val c = carbs.toIntOrNullLenient()
            val f = fat.toIntOrNullLenient()
            if (p == null && c == null && f == null) return null
            return (p ?: 0) * 4 + (c ?: 0) * 4 + (f ?: 0) * 9
        }
}

/** Field → message, for the fields that are currently invalid. */
enum class EditorField { TITLE, SERVINGS, PREP, COOK, CALORIES, PROTEIN, CARBS, FAT, FIBER, INGREDIENTS, STEPS }

val ACCENT_SWATCHES = listOf(
    "#1E7A5A", "#F29E4C", "#6D5BD0", "#E5533D", "#F6C945",
    "#2A9D8F", "#3A86FF", "#E76F51", "#EC4899", "#8D6E63",
)

val SUGGESTED_TAGS = listOf(
    "high-protein", "quick", "meal-prep", "one-pan", "budget", "vegetarian",
    "low-carb", "spicy", "no-cook", "freezer-friendly", "post-swim",
)

class RecipeEditorViewModel(
    private val repo: MealRepository,
    private val recipeId: String?,
) : ViewModel() {

    var form by mutableStateOf(EditorForm())
        private set
    var loading by mutableStateOf(recipeId != null)
        private set
    var notFound by mutableStateOf(false)
        private set
    var showErrors by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set

    /** The recipe being edited, if any (keeps fields the form doesn't expose). */
    private var original: RecipeEntity? = null
    private var nextKey = 1L

    val availableTags: StateFlow<List<String>> = repo.allTags()
        .map { (SUGGESTED_TAGS + it).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SUGGESTED_TAGS)

    val isEditing: Boolean get() = original?.isUserCreated == true

    /** True when saving will create a copy (editing a built-in recipe). */
    val willCopy: Boolean get() = original != null && original?.isUserCreated != true

    init {
        if (recipeId == null) {
            form = form.copy(
                ingredients = listOf(newIngredient()),
                steps = listOf(newStep()),
            )
        } else {
            viewModelScope.launch {
                val data = runCatching { repo.recipe(recipeId).first() }.getOrNull()
                if (data == null) {
                    notFound = true
                } else {
                    val r = data.recipe
                    original = r
                    form = EditorForm(
                        title = r.title,
                        subtitle = r.subtitle,
                        description = r.description,
                        mealType = r.mealType,
                        cuisine = r.cuisine,
                        prepMinutes = r.prepMinutes.toString(),
                        cookMinutes = r.cookMinutes.toString(),
                        servings = r.servings.toString(),
                        calories = r.calories.toString(),
                        protein = r.proteinG.toString(),
                        carbs = r.carbsG.toString(),
                        fat = r.fatG.toString(),
                        fiber = r.fiberG.toString(),
                        difficulty = r.difficulty.coerceIn(1, 3),
                        imageKey = FoodImageKey.from(r.imageKey),
                        accentColor = r.accentColor,
                        tags = r.tags,
                        mealPrepFriendly = r.mealPrepFriendly,
                        fridgeDays = r.fridgeDays,
                        ingredients = data.ingredients.sortedBy { it.position }.map { ing ->
                            IngredientDraft(
                                key = nextKey++,
                                name = ing.name,
                                quantity = trimDecimal(ing.quantity),
                                unit = ing.unit,
                                aisle = ing.aisle,
                                price = if (ing.estPriceUsd > 0) String.format(Locale.US, "%.2f", ing.estPriceUsd) else "",
                                note = ing.note.orEmpty(),
                                pantryStaple = ing.pantryStaple,
                            )
                        }.ifEmpty { listOf(newIngredient()) },
                        steps = r.steps.map { StepDraft(nextKey++, it) }.ifEmpty { listOf(newStep()) },
                    )
                }
                loading = false
            }
        }
    }

    // ---- Simple field updates -------------------------------------------------------------

    fun update(transform: (EditorForm) -> EditorForm) {
        form = transform(form)
    }

    fun useComputedCalories() {
        val kcal = form.computedCalories ?: return
        form = form.copy(calories = kcal.toString())
    }

    fun toggleTag(tag: String) {
        val t = tag.trim().lowercase(Locale.getDefault())
        if (t.isEmpty()) return
        form = form.copy(tags = if (t in form.tags) form.tags - t else form.tags + t)
    }

    fun addCustomTag(raw: String) {
        val t = raw.trim().lowercase(Locale.getDefault()).replace(Regex("\\s+"), "-").removePrefix("#")
        if (t.isEmpty() || t in form.tags) return
        form = form.copy(tags = form.tags + t)
    }

    // ---- Ingredients ----------------------------------------------------------------------

    fun addIngredient() {
        form = form.copy(ingredients = form.ingredients + newIngredient())
    }

    fun updateIngredient(key: Long, transform: (IngredientDraft) -> IngredientDraft) {
        form = form.copy(ingredients = form.ingredients.map { if (it.key == key) transform(it) else it })
    }

    fun removeIngredient(key: Long) {
        form = form.copy(ingredients = form.ingredients.filterNot { it.key == key })
    }

    // ---- Steps ----------------------------------------------------------------------------

    fun addStep() {
        form = form.copy(steps = form.steps + newStep())
    }

    fun updateStep(key: Long, text: String) {
        form = form.copy(steps = form.steps.map { if (it.key == key) it.copy(text = text) else it })
    }

    fun removeStep(key: Long) {
        form = form.copy(steps = form.steps.filterNot { it.key == key })
    }

    fun moveStep(key: Long, delta: Int) {
        val list = form.steps.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        val item = list.removeAt(from)
        list.add(to, item)
        form = form.copy(steps = list)
    }

    // ---- Validation & save ----------------------------------------------------------------

    fun errors(f: EditorForm = form): Map<EditorField, String> {
        val e = linkedMapOf<EditorField, String>()
        if (f.title.isBlank()) e[EditorField.TITLE] = "Give your recipe a name"
        val servings = f.servings.toIntOrNullLenient()
        if (servings == null || servings !in 1..50) e[EditorField.SERVINGS] = "1–50"
        if (!f.prepMinutes.isValidInt(0, 1440)) e[EditorField.PREP] = "Minutes"
        if (!f.cookMinutes.isValidInt(0, 1440)) e[EditorField.COOK] = "Minutes"
        if (!f.calories.isValidInt(0, 5000, required = true)) e[EditorField.CALORIES] = "Required"
        if (!f.protein.isValidInt(0, 500, required = true)) e[EditorField.PROTEIN] = "Required"
        if (!f.carbs.isValidInt(0, 1000, required = true)) e[EditorField.CARBS] = "Required"
        if (!f.fat.isValidInt(0, 500, required = true)) e[EditorField.FAT] = "Required"
        if (!f.fiber.isValidInt(0, 200)) e[EditorField.FIBER] = "Number"
        val named = f.ingredients.filter { it.name.isNotBlank() }
        if (named.isEmpty()) {
            e[EditorField.INGREDIENTS] = "Add at least one ingredient"
        } else if (named.any { parseQuantity(it.quantity) == null }) {
            e[EditorField.INGREDIENTS] = "Check ingredient quantities (e.g. 1, 0.5, 1 1/2)"
        }
        if (f.steps.none { it.text.isNotBlank() }) e[EditorField.STEPS] = "Add at least one step"
        return e
    }

    fun save(onSaved: (String) -> Unit) {
        if (saving) return
        val f = form
        if (errors(f).isNotEmpty()) {
            showErrors = true
            return
        }
        saving = true
        saveError = null
        val orig = original
        val keepIdentity = orig != null && orig.isUserCreated
        val id = if (keepIdentity && orig != null) orig.id else newUserRecipeId()
        val entity = RecipeEntity(
            id = id,
            title = f.title.trim(),
            subtitle = f.subtitle.trim(),
            description = f.description.trim(),
            mealType = f.mealType,
            cuisine = f.cuisine.trim(),
            prepMinutes = f.prepMinutes.toIntOrNullLenient() ?: 0,
            cookMinutes = f.cookMinutes.toIntOrNullLenient() ?: 0,
            servings = f.servings.toIntOrNullLenient()?.coerceAtLeast(1) ?: 1,
            calories = f.calories.toIntOrNullLenient() ?: 0,
            proteinG = f.protein.toIntOrNullLenient() ?: 0,
            carbsG = f.carbs.toIntOrNullLenient() ?: 0,
            fatG = f.fat.toIntOrNullLenient() ?: 0,
            fiberG = f.fiber.toIntOrNullLenient() ?: 0,
            imageKey = f.imageKey.name.lowercase(Locale.US),
            accentColor = f.accentColor,
            tags = f.tags.distinct(),
            steps = f.steps.map { it.text.trim() }.filter { it.isNotEmpty() },
            difficulty = f.difficulty.coerceIn(1, 3),
            mealPrepFriendly = f.mealPrepFriendly,
            fridgeDays = f.fridgeDays.coerceIn(0, 14),
            sourceName = orig?.sourceName?.takeIf { it.isNotBlank() } ?: "My kitchen",
            sourceUrl = orig?.sourceUrl.orEmpty(),
            isFavorite = if (keepIdentity) orig?.isFavorite == true else false,
            isUserCreated = true,
            userPhotoPath = if (keepIdentity) orig?.userPhotoPath else null,
        )
        val ingredients = f.ingredients
            .filter { it.name.isNotBlank() }
            .mapIndexed { i, d ->
                IngredientEntity(
                    recipeId = id,
                    name = d.name.trim().lowercase(Locale.getDefault()),
                    quantity = parseQuantity(d.quantity) ?: 1.0,
                    unit = d.unit,
                    aisle = d.aisle,
                    estPriceUsd = d.price.replace("$", "").trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                    note = d.note.trim().ifEmpty { null },
                    pantryStaple = d.pantryStaple,
                    position = i,
                )
            }
        viewModelScope.launch {
            runCatching { repo.saveUserRecipe(entity, ingredients) }
                .onSuccess { savedId ->
                    saving = false
                    onSaved(savedId)
                }
                .onFailure {
                    saving = false
                    saveError = "Couldn't save — please try again."
                }
        }
    }

    fun clearSaveError() {
        saveError = null
    }

    private fun newIngredient() = IngredientDraft(key = nextKey++)
    private fun newStep() = StepDraft(key = nextKey++)
}

// ---- Parsing helpers ----------------------------------------------------------------------

private fun String.toIntOrNullLenient(): Int? {
    val t = trim()
    if (t.isEmpty()) return null
    return t.toIntOrNull() ?: t.toDoubleOrNull()?.roundToInt()
}

private fun String.isValidInt(min: Int, max: Int, required: Boolean = false): Boolean {
    if (isBlank()) return !required
    val v = toIntOrNullLenient() ?: return false
    return v in min..max
}

/** Parses "1", "0.5", "1/2", "1 1/2", "½", "1½". */
internal fun parseQuantity(raw: String): Double? {
    val vulgar = mapOf('¼' to 0.25, '½' to 0.5, '¾' to 0.75, '⅓' to 1.0 / 3, '⅔' to 2.0 / 3, '⅛' to 0.125)
    var s = raw.trim().replace(',', '.')
    if (s.isEmpty()) return null
    var extra = 0.0
    vulgar.forEach { (ch, v) ->
        if (s.contains(ch)) {
            extra += v
            s = s.replace(ch.toString(), " ")
        }
    }
    s = s.trim()
    if (s.isEmpty()) return extra.takeIf { it > 0 }
    var total = 0.0
    for (part in s.split(Regex("\\s+"))) {
        total += if (part.contains('/')) {
            val bits = part.split('/')
            if (bits.size != 2) return null
            val n = bits[0].toDoubleOrNull() ?: return null
            val d = bits[1].toDoubleOrNull() ?: return null
            if (d == 0.0) return null
            n / d
        } else {
            part.toDoubleOrNull() ?: return null
        }
    }
    val result = total + extra
    return if (result > 0) result else null
}

private fun trimDecimal(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')
