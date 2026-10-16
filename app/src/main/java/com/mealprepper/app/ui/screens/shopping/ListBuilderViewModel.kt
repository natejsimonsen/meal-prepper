package com.mealprepper.app.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.repo.ListBuildRequest
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.data.repo.RecipeFilter
import com.mealprepper.app.ui.screens.plan.batchCost
import com.mealprepper.app.ui.screens.plan.cookBatches
import com.mealprepper.app.ui.screens.plan.mondayOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

enum class RangePreset(val label: String) {
    THIS_WEEK("This week"),
    NEXT_7("Next 7 days"),
    NEXT_3("Next 3 days"),
    CUSTOM("Custom"),
    NONE("No plan"),
}

data class BuilderOptions(
    val preset: RangePreset,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val skipPantry: Boolean = true,
    val keepManual: Boolean = true,
)

data class BuilderUiState(
    val options: BuilderOptions = BuilderOptions(RangePreset.THIS_WEEK),
    val rangeStart: LocalDate? = null,
    val rangeEnd: LocalDate? = null,
    /** Planned, not-yet-cooked meals in the range (incl. leftovers). */
    val planMeals: Int = 0,
    /** Cooking sessions in the range (what actually needs groceries). */
    val planCookSessions: Int = 0,
    val searchResults: List<RecipeEntity> = emptyList(),
    val extras: Map<String, Int> = emptyMap(),
    /** Recipes for the selected extras (for showing a "selected" summary). */
    val extraRecipes: List<RecipeEntity> = emptyList(),
    val recipeCount: Int = 0,
    val estCostUsd: Double = 0.0,
    val building: Boolean = false,
    val built: Boolean = false,
    val budgetUsd: Int = 200,
) {
    val canBuild: Boolean get() = !building && (planCookSessions > 0 || extras.isNotEmpty())
}

private data class Range(val start: LocalDate?, val end: LocalDate?)

private data class PlanPart(val range: Range, val meals: List<PlannedMeal>)

private data class BuildFlags(val building: Boolean, val built: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class ListBuilderViewModel(
    private val meals: MealRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    private val options = MutableStateFlow(
        BuilderOptions(
            preset = if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) {
                RangePreset.NEXT_7
            } else {
                RangePreset.THIS_WEEK
            },
        )
    )
    private val query = MutableStateFlow("")
    private val extras = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val flags = MutableStateFlow(BuildFlags(building = false, built = false))

    private fun rangeFor(o: BuilderOptions): Range = when (o.preset) {
        RangePreset.THIS_WEEK -> Range(today, mondayOf(today).plusDays(6))
        RangePreset.NEXT_7 -> Range(today, today.plusDays(6))
        RangePreset.NEXT_3 -> Range(today, today.plusDays(2))
        RangePreset.CUSTOM -> {
            val s = o.customStart
            if (s == null) Range(null, null) else Range(s, o.customEnd ?: s)
        }
        RangePreset.NONE -> Range(null, null)
    }

    private val planPart: Flow<PlanPart> = options
        .map { rangeFor(it) }
        .distinctUntilChanged()
        .flatMapLatest { range ->
            val s = range.start
            val e = range.end
            if (s == null || e == null) {
                flowOf(PlanPart(range, emptyList()))
            } else {
                meals.plan(s, e).map { list -> PlanPart(range, list.filter { !it.entry.cooked }) }
            }
        }

    private val searchResults: Flow<List<RecipeEntity>> = query
        .flatMapLatest { q -> meals.recipes(RecipeFilter(query = q.trim())) }

    /** Live recipe+ingredients for every recipe that contributes (plan + extras). */
    private val involvedRecipes: Flow<Map<String, RecipeWithIngredients>> =
        combine(planPart, extras) { plan, ex -> plan.meals.map { it.entry.recipeId }.toSet() + ex.keys }
            .distinctUntilChanged()
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(ids.map { id -> meals.recipe(id) }) { arr: Array<RecipeWithIngredients?> ->
                        arr.filterNotNull().associateBy { it.recipe.id }
                    }
                }
            }

    val state: StateFlow<BuilderUiState> = combine(
        options, planPart, searchResults, extras, involvedRecipes,
    ) { opts, plan, results, ex, recipes ->
        val batches = cookBatches(plan.meals)
        val planCost = batches.sumOf { b ->
            val full = recipes[b.cook.entry.recipeId]?.ingredients?.sumOf { it.estPriceUsd } ?: 0.0
            batchCost(b, full)
        }
        val extrasCost = ex.entries.sumOf { (id, servings) ->
            val r = recipes[id] ?: return@sumOf 0.0
            r.ingredients.sumOf { it.estPriceUsd } * servings / r.recipe.servings.coerceAtLeast(1)
        }
        val uniqueRecipes = batches.map { it.cook.entry.recipeId }.toSet() + ex.keys
        BuilderUiState(
            options = opts,
            rangeStart = plan.range.start,
            rangeEnd = plan.range.end,
            planMeals = plan.meals.size,
            planCookSessions = batches.size,
            searchResults = results,
            extras = ex,
            extraRecipes = ex.keys.mapNotNull { recipes[it]?.recipe },
            recipeCount = uniqueRecipes.size,
            estCostUsd = planCost + extrasCost,
        )
    }
        .combine(flags) { s, f -> s.copy(building = f.building, built = f.built) }
        .combine(profiles.profile) { s, p -> s.copy(budgetUsd = p.weeklyBudgetUsd) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BuilderUiState(options = options.value))

    fun selectPreset(preset: RangePreset) {
        options.update { it.copy(preset = preset) }
    }

    fun setCustomRange(start: LocalDate, end: LocalDate) {
        val (s, e) = if (end.isBefore(start)) end to start else start to end
        options.update { it.copy(preset = RangePreset.CUSTOM, customStart = s, customEnd = e) }
    }

    fun setSkipPantry(value: Boolean) {
        options.update { it.copy(skipPantry = value) }
    }

    fun setKeepManual(value: Boolean) {
        options.update { it.copy(keepManual = value) }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    /** First tap adds one full batch (the recipe's servings), then ±1 serving. */
    fun increment(recipe: RecipeEntity) {
        extras.update { current ->
            val now = current[recipe.id]
            val next: Int = if (now == null) recipe.servings.coerceAtLeast(1) else now + 1
            current + (recipe.id to next)
        }
    }

    fun decrement(recipe: RecipeEntity) {
        extras.update { current ->
            val now = current[recipe.id] ?: return@update current
            if (now <= 1) current - recipe.id else current + (recipe.id to now - 1)
        }
    }

    fun removeExtra(recipeId: String) {
        extras.update { it - recipeId }
    }

    fun build() {
        val s = state.value
        if (!s.canBuild) return
        val opts = options.value
        val range = rangeFor(opts)
        val request = ListBuildRequest(
            planStart = range.start,
            planEnd = range.end,
            extraRecipes = extras.value.filterValues { it > 0 },
            skipPantry = opts.skipPantry,
            keepManualItems = opts.keepManual,
        )
        flags.value = BuildFlags(building = true, built = false)
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                meals.buildShoppingList(request)
                // Keep the loading state visible long enough to register.
                val elapsed = System.currentTimeMillis() - started
                if (elapsed < 600) delay(600 - elapsed)
                flags.value = BuildFlags(building = false, built = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                flags.value = BuildFlags(building = false, built = false)
            }
        }
    }
}
