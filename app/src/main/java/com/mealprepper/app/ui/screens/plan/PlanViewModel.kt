package com.mealprepper.app.ui.screens.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.data.repo.RecipeFilter
import com.mealprepper.app.domain.NutritionCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Shared plan math (also used by the shopping list builder for its estimate).
// ---------------------------------------------------------------------------------------------

/**
 * One cooking session: a non-leftover entry plus the leftover entries of the same recipe that
 * follow it (until the next time that recipe is cooked).
 *
 * Works with either convention for [com.mealprepper.app.data.model.MealPlanEntryEntity.servings]
 * on the cooking entry: "portions cooked for the whole batch" or "portions eaten at that meal".
 */
internal data class CookBatch(val cook: PlannedMeal, val leftovers: List<PlannedMeal>) {
    private val leftoverServings: Int get() = leftovers.sumOf { it.entry.servings.coerceAtLeast(1) }

    /** Servings eaten at the cooking meal itself. */
    val eatenAtCook: Int get() = (cook.entry.servings - leftoverServings).coerceAtLeast(1)

    /** Total portions to cook (and shop for) in this session. */
    val portions: Int get() = maxOf(cook.entry.servings, eatenAtCook + leftoverServings)
}

private val mealOrder: Comparator<PlannedMeal> =
    compareBy<PlannedMeal>({ it.entry.epochDay }, { it.entry.slot.ordinal })

internal fun cookBatches(meals: List<PlannedMeal>): List<CookBatch> {
    val result = mutableListOf<CookBatch>()
    meals.groupBy { it.entry.recipeId }.values.forEach { group ->
        var cook: PlannedMeal? = null
        val lefts = mutableListOf<PlannedMeal>()
        group.sortedWith(mealOrder).forEach { m ->
            if (m.entry.isLeftover) {
                if (cook != null) lefts.add(m)
            } else {
                val previous = cook
                if (previous != null) result.add(CookBatch(previous, lefts.toList()))
                cook = m
                lefts.clear()
            }
        }
        val last = cook
        if (last != null) result.add(CookBatch(last, lefts.toList()))
    }
    return result.sortedWith(compareBy<CookBatch>({ it.cook.entry.epochDay }, { it.cook.entry.slot.ordinal }))
}

/** entryId → servings actually eaten at that meal (for nutrition totals). */
internal fun eatenServings(meals: List<PlannedMeal>): Map<Long, Int> {
    val map = HashMap<Long, Int>()
    meals.forEach { map[it.entry.id] = it.entry.servings.coerceAtLeast(1) }
    cookBatches(meals).forEach { map[it.cook.entry.id] = it.eatenAtCook }
    return map
}

/** Estimated grocery cost of a batch given the full-recipe ingredient cost. */
internal fun batchCost(batch: CookBatch, fullRecipeCostUsd: Double): Double =
    fullRecipeCostUsd * batch.portions / batch.cook.recipe.servings.coerceAtLeast(1)

/** Full-recipe ingredient cost for each id (live). */
internal fun MealRepository.recipeCosts(ids: Set<String>): Flow<Map<String, Double>> =
    if (ids.isEmpty()) {
        flowOf(emptyMap())
    } else {
        combine(
            ids.map { id ->
                recipe(id).map { rwi -> id to (rwi?.ingredients?.sumOf { it.estPriceUsd } ?: 0.0) }
            }
        ) { pairs: Array<Pair<String, Double>> -> pairs.toMap() }
    }

internal fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

internal fun shortDayName(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

// ---------------------------------------------------------------------------------------------
// UI state
// ---------------------------------------------------------------------------------------------

data class PlanDay(
    val date: LocalDate,
    val mealCount: Int,
    val cookedCount: Int,
    val isToday: Boolean,
)

data class DayTotals(val calories: Int = 0, val proteinG: Int = 0, val carbsG: Int = 0, val fatG: Int = 0)

data class PrepTask(
    val id: String,
    val recipe: RecipeEntity,
    val portions: Int,
    /** e.g. "Tue lunch, Wed lunch, Thu lunch". */
    val covers: String,
    /** True when the last portion would be eaten after the recipe's fridge life. */
    val freezeSome: Boolean,
    val done: Boolean,
)

data class PlanUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = mondayOf(LocalDate.now()),
    val selectedDay: LocalDate = LocalDate.now(),
    val days: List<PlanDay> = emptyList(),
    val slots: Map<MealType, List<PlannedMeal>> = emptyMap(),
    val dayTotals: DayTotals = DayTotals(),
    val targets: NutritionTargets? = null,
    val weekMealCount: Int = 0,
    val plannedDays: Int = 0,
    val avgProteinPerDay: Int = 0,
    val uniqueRecipes: Int = 0,
    val estCostUsd: Double = 0.0,
    val budgetUsd: Int = 200,
    val prepTasks: List<PrepTask> = emptyList(),
    val generating: Boolean = false,
) {
    val weekEnd: LocalDate get() = weekStart.plusDays(6)
    val isCurrentWeek: Boolean get() = weekStart == mondayOf(today)
}

data class PickerState(
    val slot: MealType? = null,
    val recipes: List<RecipeEntity> = emptyList(),
)

private data class PlanInputs(
    val weekStart: LocalDate,
    val selectedDay: LocalDate,
    val plan: List<PlannedMeal>,
    val profile: UserProfile,
    val costs: Map<String, Double>,
)

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val weekStart = MutableStateFlow(mondayOf(today))
    private val selectedDay = MutableStateFlow(today)
    private val prepDone = MutableStateFlow<Set<String>>(emptySet())
    private val generating = MutableStateFlow(false)
    private val pickerSlot = MutableStateFlow<MealType?>(null)
    private val pickerQuery = MutableStateFlow("")

    private val weekPlan: Flow<List<PlannedMeal>> =
        weekStart.flatMapLatest { start -> meals.plan(start, start.plusDays(6)) }

    private val costs: Flow<Map<String, Double>> = weekPlan
        .map { plan -> plan.filter { !it.entry.isLeftover }.map { it.entry.recipeId }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { ids -> meals.recipeCosts(ids) }

    val state: StateFlow<PlanUiState> = combine(
        weekStart, selectedDay, weekPlan, profiles.profile, costs,
    ) { ws, sel, plan, profile, cost -> PlanInputs(ws, sel, plan, profile, cost) }
        .combine(prepDone) { inputs, done -> inputs to done }
        .combine(generating) { (inputs, done), gen -> buildState(inputs, done, gen) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    val picker: StateFlow<PickerState> = pickerSlot
        .combine(pickerQuery) { slot, query -> slot to query }
        .flatMapLatest { (slot, query) ->
            if (slot == null) {
                flowOf(PickerState())
            } else {
                meals.recipes(RecipeFilter(query = query.trim(), mealType = slot))
                    .map { PickerState(slot, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerState())

    private val pickerOpen = MutableStateFlow(false)
    val isPickerOpen: StateFlow<Boolean> = pickerOpen.asStateFlow()

    private fun buildState(inputs: PlanInputs, done: Set<String>, gen: Boolean): PlanUiState {
        val ws = inputs.weekStart
        val we = ws.plusDays(6)
        val startDay = ws.toEpochDay()
        val endDay = we.toEpochDay()
        // Guard against a stale plan emission from the previous week while switching.
        val plan = inputs.plan.filter { it.entry.epochDay in startDay..endDay }
        val eaten = eatenServings(plan)
        val byDay = plan.groupBy { it.entry.epochDay }

        val days = (0L until 7L).map { offset ->
            val date = ws.plusDays(offset)
            val list = byDay[date.toEpochDay()].orEmpty()
            PlanDay(date, list.size, list.count { it.entry.cooked }, date == today)
        }

        val selected = inputs.selectedDay
        val dayMeals = byDay[selected.toEpochDay()].orEmpty()
        val slots = MealType.entries.associateWith { slot ->
            dayMeals.filter { it.entry.slot == slot }.sortedBy { it.entry.id }
        }

        fun totalsOf(list: List<PlannedMeal>): DayTotals {
            var cal = 0
            var p = 0
            var c = 0
            var f = 0
            list.forEach { m ->
                val s = eaten[m.entry.id] ?: 1
                cal += m.recipe.calories * s
                p += m.recipe.proteinG * s
                c += m.recipe.carbsG * s
                f += m.recipe.fatG * s
            }
            return DayTotals(cal, p, c, f)
        }

        val plannedDays = byDay.size
        val weekProtein = totalsOf(plan).proteinG
        val batches = cookBatches(plan)
        val estCost = batches.sumOf { batchCost(it, inputs.costs[it.cook.entry.recipeId] ?: 0.0) }

        val prepTasks = batches
            .filter { it.cook.recipe.mealPrepFriendly && !it.cook.entry.cooked }
            .map { b ->
                val coveredMeals = listOf(b.cook) + b.leftovers
                val lastDay = coveredMeals.maxOf { it.entry.epochDay }
                val id = "${b.cook.entry.id}"
                PrepTask(
                    id = id,
                    recipe = b.cook.recipe,
                    portions = b.portions,
                    covers = coveredMeals.joinToString(", ") { m ->
                        "${shortDayName(LocalDate.ofEpochDay(m.entry.epochDay))} ${m.entry.slot.label.lowercase()}"
                    },
                    // Prep happens the Sunday before the week starts.
                    freezeSome = lastDay - (startDay - 1) > b.cook.recipe.fridgeDays,
                    done = id in done,
                )
            }

        return PlanUiState(
            loading = false,
            today = today,
            weekStart = ws,
            selectedDay = selected,
            days = days,
            slots = slots,
            dayTotals = totalsOf(dayMeals),
            targets = runCatching { NutritionCalculator.targets(inputs.profile) }.getOrNull(),
            weekMealCount = plan.size,
            plannedDays = plannedDays,
            avgProteinPerDay = if (plannedDays == 0) 0 else weekProtein / plannedDays,
            uniqueRecipes = plan.map { it.entry.recipeId }.distinct().size,
            estCostUsd = estCost,
            budgetUsd = inputs.profile.weeklyBudgetUsd,
            prepTasks = prepTasks,
            generating = gen,
        )
    }

    // --- Week navigation ---------------------------------------------------------------------

    fun previousWeek() = moveWeeks(-1)
    fun nextWeek() = moveWeeks(1)

    fun goToToday() {
        weekStart.value = mondayOf(today)
        selectedDay.value = today
    }

    private fun moveWeeks(delta: Long) {
        val newStart = weekStart.value.plusWeeks(delta)
        weekStart.value = newStart
        selectedDay.value = if (mondayOf(today) == newStart) today else newStart
    }

    fun selectDay(date: LocalDate) {
        selectedDay.value = date
    }

    // --- Meal actions ------------------------------------------------------------------------

    fun swap(entryId: Long) {
        viewModelScope.launch { meals.swapMeal(entryId, profiles.profile.first()) }
    }

    fun remove(entryId: Long) {
        viewModelScope.launch { meals.removeFromPlan(entryId) }
    }

    fun toggleCooked(meal: PlannedMeal) {
        viewModelScope.launch { meals.setCooked(meal.entry.id, !meal.entry.cooked) }
    }

    fun generateWeek() {
        if (generating.value) return
        viewModelScope.launch {
            generating.value = true
            try {
                meals.generatePlan(weekStart.value, 7, profiles.profile.first())
                prepDone.value = emptySet()
            } finally {
                generating.value = false
            }
        }
    }

    fun togglePrep(id: String) {
        prepDone.update { if (id in it) it - id else it + id }
    }

    // --- Recipe picker -----------------------------------------------------------------------

    fun openPicker(slot: MealType) {
        pickerQuery.value = ""
        pickerSlot.value = slot
        pickerOpen.value = true
    }

    fun closePicker() {
        pickerOpen.value = false
        pickerSlot.value = null
    }

    fun setPickerQuery(query: String) {
        pickerQuery.value = query
    }

    fun addFromPicker(recipeId: String) {
        val slot = pickerSlot.value ?: return
        val date = selectedDay.value
        closePicker()
        viewModelScope.launch { meals.addToPlan(date, slot, recipeId) }
    }

    companion object {
        val RangeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val DayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    }
}
