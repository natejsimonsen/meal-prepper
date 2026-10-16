package com.mealprepper.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.data.repo.ShoppingSummary
import com.mealprepper.app.domain.NutritionCalculator
import com.mealprepper.app.ui.screens.progress.swimStreak
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class MacroTotals(
    val calories: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
)

data class TodayUiState(
    val loading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val targets: NutritionTargets? = null,
    /** Today's plan entries, breakfast → snack. */
    val meals: List<PlannedMeal> = emptyList(),
    /** Sum of every planned entry × servings. */
    val planned: MacroTotals = MacroTotals(),
    /** Sum of entries marked cooked/eaten × servings. */
    val eaten: MacroTotals = MacroTotals(),
    val todayLog: DailyLogEntity? = null,
    val swimStreak: Int = 0,
    val shopping: ShoppingSummary? = null,
    val picks: List<RecipeEntity> = emptyList(),
    val generating: Boolean = false,
)

private fun List<PlannedMeal>.totals(): MacroTotals = MacroTotals(
    calories = sumOf { it.recipe.calories * it.portionsEaten },
    proteinG = sumOf { it.recipe.proteinG * it.portionsEaten },
    carbsG = sumOf { it.recipe.carbsG * it.portionsEaten },
    fatG = sumOf { it.recipe.fatG * it.portionsEaten },
)

class TodayViewModel(
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val generating = MutableStateFlow(false)
    private val logLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val shopping: Flow<ShoppingSummary> = profiles.profile
        .map { it.weeklyBudgetUsd }
        .distinctUntilChanged()
        .flatMapLatest { budget -> meals.shoppingSummary(budget) }

    val state: StateFlow<TodayUiState> = combine(
        profiles.profile,
        meals.plan(today, today),
        meals.logs(today.minusDays(30), today),
        meals.tonightPicks(8),
        shopping,
    ) { profile, plan, logs, picks, shop ->
        val todays = plan
            .filter { it.entry.epochDay == today.toEpochDay() }
            .sortedWith(compareBy<PlannedMeal>({ it.entry.slot.ordinal }, { it.entry.id }))
        TodayUiState(
            loading = false,
            profile = profile,
            targets = NutritionCalculator.targets(profile),
            meals = todays,
            planned = todays.totals(),
            eaten = todays.filter { it.entry.cooked }.totals(),
            todayLog = logs.firstOrNull { it.epochDay == today.toEpochDay() },
            swimStreak = swimStreak(logs, today),
            shopping = shop,
            picks = picks,
        )
    }
        .combine(generating) { s, g -> s.copy(generating = g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun setEaten(entryId: Long, eaten: Boolean) {
        viewModelScope.launch { meals.setCooked(entryId, eaten) }
    }

    fun generateWeek() {
        if (generating.value) return
        viewModelScope.launch {
            generating.value = true
            try {
                val profile = profiles.profile.first()
                meals.generatePlan(LocalDate.now(), 7, profile)
            } finally {
                generating.value = false
            }
        }
    }

    /** One tap: log the default swim from the profile (1000 yd / 30 min by default). */
    fun logSwim() = updateToday { log, p ->
        log.copy(swimYards = p.swimYardsPerDay, swimMinutes = p.swimMinutesPerDay)
    }

    fun undoSwim() = updateToday { log, _ -> log.copy(swimYards = 0, swimMinutes = 0) }

    private fun updateToday(transform: (DailyLogEntity, UserProfile) -> DailyLogEntity) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                logLock.withLock {
                    val profile = profiles.profile.first()
                    val current = meals.log(today).first() ?: DailyLogEntity(epochDay = today.toEpochDay())
                    meals.upsertLog(transform(current, profile))
                }
            }
        }
    }
}

/** Portions eaten today. A dinner's servings is the batch size (extra portions are tomorrow's leftovers). */
internal val com.mealprepper.app.data.model.PlannedMeal.portionsEaten: Int
    get() = if (entry.slot == com.mealprepper.app.data.model.MealType.DINNER) 1 else entry.servings
