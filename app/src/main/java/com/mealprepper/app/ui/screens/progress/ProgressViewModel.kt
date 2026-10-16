package com.mealprepper.app.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.domain.NutritionCalculator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

data class DayStat(val date: LocalDate, val yards: Int, val weightLb: Double?)

data class ProgressUiState(
    val loading: Boolean = true,
    val profile: UserProfile = UserProfile(),
    val targets: NutritionTargets? = null,
    /** Exactly [ProgressViewModel.WINDOW_DAYS] days, oldest → today. */
    val days: List<DayStat> = emptyList(),
    val today: DailyLogEntity? = null,
    val yardsWeek: Int = 0,
    val yardsMonth: Int = 0,
    val streak: Int = 0,
    val latestWeight: Double? = null,
    /** Latest minus earliest logged weight in the window; null with fewer than 2 weigh-ins. */
    val weightChange: Double? = null,
    val projectedGoalDate: LocalDate? = null,
    val goalReached: Boolean = false,
)

/** Consecutive days with a swim, ending today (or yesterday if today isn't logged yet). */
internal fun swimStreak(logs: List<DailyLogEntity>, today: LocalDate): Int {
    val swam = logs.filter { it.swimYards > 0 }.map { it.epochDay }.toHashSet()
    var day = today.toEpochDay()
    if (day !in swam) day -= 1
    var streak = 0
    while (day in swam) {
        streak++
        day--
    }
    return streak
}

class ProgressViewModel(
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    companion object {
        const val WINDOW_DAYS = 30
    }

    private val today: LocalDate = LocalDate.now()
    private val start: LocalDate = today.minusDays((WINDOW_DAYS - 1).toLong())
    private val writeLock = Mutex()

    val state: StateFlow<ProgressUiState> =
        combine(profiles.profile, meals.logs(start, today)) { profile, logs ->
            val byDay = logs.associateBy { it.epochDay }
            val days = (0 until WINDOW_DAYS).map { i ->
                val date = start.plusDays(i.toLong())
                val log = byDay[date.toEpochDay()]
                DayStat(date, log?.swimYards ?: 0, log?.weightLb)
            }
            val weights = days.mapNotNull { it.weightLb }
            val latest = weights.lastOrNull()
            val current = latest ?: profile.weightLb
            val remaining = current - profile.goalWeightLb
            val reached = remaining <= 0.0
            val projected = if (!reached && profile.pace.lbPerWeek > 0.0) {
                today.plusDays(ceil(remaining / profile.pace.lbPerWeek * 7.0).toLong())
            } else null
            ProgressUiState(
                loading = false,
                profile = profile,
                targets = NutritionCalculator.targets(profile),
                days = days,
                today = byDay[today.toEpochDay()],
                yardsWeek = days.takeLast(7).sumOf { it.yards },
                yardsMonth = days.sumOf { it.yards },
                streak = swimStreak(logs, today),
                latestWeight = latest,
                weightChange = if (weights.size >= 2) weights.last() - weights.first() else null,
                projectedGoalDate = projected,
                goalReached = reached,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    private fun mutateToday(transform: (DailyLogEntity, UserProfile) -> DailyLogEntity) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                writeLock.withLock {
                    val profile = profiles.profile.first()
                    val current = meals.log(today).first() ?: DailyLogEntity(epochDay = today.toEpochDay())
                    meals.upsertLog(transform(current, profile))
                }
            }
        }
    }

    /** One tap: today's default swim (only if nothing is logged yet). */
    fun logDefaultSwim() = mutateToday { log, p ->
        if (log.swimYards > 0) log
        else log.copy(swimYards = p.swimYardsPerDay, swimMinutes = p.swimMinutesPerDay)
    }

    fun addYards(yards: Int) = mutateToday { log, p ->
        val minPerYard = if (p.swimYardsPerDay > 0) p.swimMinutesPerDay.toDouble() / p.swimYardsPerDay else 0.03
        log.copy(
            swimYards = log.swimYards + yards,
            swimMinutes = log.swimMinutes + (yards * minPerYard).roundToInt(),
        )
    }

    fun clearSwim() = mutateToday { log, _ -> log.copy(swimYards = 0, swimMinutes = 0) }

    fun changeWater(delta: Int) = mutateToday { log, _ ->
        log.copy(waterCups = (log.waterCups + delta).coerceIn(0, 30))
    }

    /** Logs today's weight and keeps the profile weight (and so the targets) current. */
    fun logWeight(lb: Double) {
        mutateToday { log, _ -> log.copy(weightLb = lb) }
        viewModelScope.launch {
            withContext(NonCancellable) { profiles.update { it.copy(weightLb = lb) } }
        }
    }
}
