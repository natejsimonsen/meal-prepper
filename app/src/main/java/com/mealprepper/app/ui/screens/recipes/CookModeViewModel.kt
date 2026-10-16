package com.mealprepper.app.ui.screens.recipes

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.repo.MealRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

data class CookUiState(
    val loading: Boolean = true,
    val data: RecipeWithIngredients? = null,
    /** Durations detected in each step, same indexing as recipe.steps. */
    val stepTimers: List<List<ParsedDuration>> = emptyList(),
)

/** A duration found in step text, e.g. "8–10 min" → 600 s. */
data class ParsedDuration(val label: String, val seconds: Int)

data class CookTimer(
    val id: String,
    val stepIndex: Int,
    val label: String,
    val totalSeconds: Int,
    val remainingSeconds: Int,
    val running: Boolean,
    val finished: Boolean,
)

class CookModeViewModel(
    repo: MealRepository,
    recipeId: String,
) : ViewModel() {

    val state: StateFlow<CookUiState> = repo.recipe(recipeId)
        .map { data ->
            CookUiState(
                loading = false,
                data = data,
                stepTimers = data?.recipe?.steps?.map { parseDurations(it) } ?: emptyList(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CookUiState())

    private val _timers = MutableStateFlow<Map<String, CookTimer>>(emptyMap())
    val timers: StateFlow<Map<String, CookTimer>> = _timers.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    /** Starts (or resumes) the timer for duration [index] of step [stepIndex]. */
    fun start(stepIndex: Int, index: Int, duration: ParsedDuration) {
        val id = timerId(stepIndex, index)
        val existing = _timers.value[id]
        val timer = if (existing == null || existing.finished) {
            CookTimer(
                id = id,
                stepIndex = stepIndex,
                label = duration.label,
                totalSeconds = duration.seconds,
                remainingSeconds = duration.seconds,
                running = true,
                finished = false,
            )
        } else {
            existing.copy(running = true)
        }
        _timers.update { it + (id to timer) }
        jobs[id]?.cancel()
        jobs[id] = viewModelScope.launch {
            val endAt = SystemClock.elapsedRealtime() + timer.remainingSeconds * 1000L
            while (isActive) {
                val leftMs = endAt - SystemClock.elapsedRealtime()
                val left = ((leftMs + 999) / 1000).toInt().coerceAtLeast(0)
                if (left <= 0) {
                    _timers.update { map ->
                        val t = map[id] ?: return@update map
                        map + (id to t.copy(remainingSeconds = 0, running = false, finished = true))
                    }
                    break
                }
                _timers.update { map ->
                    val t = map[id] ?: return@update map
                    if (t.remainingSeconds == left) map else map + (id to t.copy(remainingSeconds = left))
                }
                delay(200)
            }
        }
    }

    fun pause(id: String) {
        jobs.remove(id)?.cancel()
        _timers.update { map ->
            val t = map[id] ?: return@update map
            map + (id to t.copy(running = false))
        }
    }

    /** Stops and forgets a timer (also used to dismiss a finished one). */
    fun reset(id: String) {
        jobs.remove(id)?.cancel()
        _timers.update { it - id }
    }

    override fun onCleared() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        super.onCleared()
    }

    companion object {
        fun timerId(stepIndex: Int, index: Int): String = "$stepIndex:$index"
    }
}

private val DURATION_REGEX = Regex(
    """(\d+(?:\.\d+)?)(?:\s*(?:-|–|—|to)\s*(\d+(?:\.\d+)?))?[\s -]*(hours?|hrs?|minutes?|mins?|seconds?|secs?)\b""",
    RegexOption.IGNORE_CASE,
)

/** Finds durations like "10 minutes", "8–10 min", "30 sec", "1.5 hours", "a 5-minute rest". */
fun parseDurations(text: String): List<ParsedDuration> {
    val out = mutableListOf<ParsedDuration>()
    for (m in DURATION_REGEX.findAll(text)) {
        val low = m.groupValues[1].toDoubleOrNull() ?: continue
        val high = m.groupValues[2].takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        val unit = m.groupValues[3].lowercase(Locale.US)
        val (unitSeconds, short) = when {
            unit.startsWith("h") -> 3600 to "hr"
            unit.startsWith("m") -> 60 to "min"
            else -> 1 to "sec"
        }
        val value = high ?: low
        val seconds = (value * unitSeconds).roundToInt()
        if (seconds < 5 || seconds > 6 * 3600) continue
        val label = if (high != null) {
            "${trimNumber(low)}–${trimNumber(high)} $short"
        } else {
            "${trimNumber(low)} $short"
        }
        if (out.none { it.seconds == seconds }) out += ParsedDuration(label, seconds)
    }
    return out
}

private fun trimNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

/** 605 → "10:05", 3725 → "1:02:05". */
fun formatCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.US, "%d:%02d", m, sec)
    }
}
