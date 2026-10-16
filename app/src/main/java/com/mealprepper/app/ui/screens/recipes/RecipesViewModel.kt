package com.mealprepper.app.ui.screens.recipes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.RecipeFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Opinionated one-tap filters on top of the meal-type row. */
enum class QuickFilter(val label: String, val emoji: String) {
    QUICK("≤15 min", "⚡"),
    TONIGHT("Tonight", "🌙"),
    HIGH_PROTEIN("High protein", "💪"),
    MEAL_PREP("Meal prep", "🍱"),
    FAVORITES("Favorites", "♥"),
}

data class RecipesUiState(
    val recipes: List<RecipeEntity> = emptyList(),
    val totalCount: Int = 0,
    val loading: Boolean = true,
    val mealType: MealType? = null,
    val quick: Set<QuickFilter> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val allTags: List<String> = emptyList(),
) {
    val hasActiveFilters: Boolean
        get() = mealType != null || quick.isNotEmpty() || selectedTags.isNotEmpty()
}

private data class Selection(
    val mealType: MealType?,
    val quick: Set<QuickFilter>,
    val tags: Set<String>,
)

private data class Controls(
    val query: String,
    val selection: Selection,
) {
    fun toFilter(): RecipeFilter {
        val q = selection.quick
        val maxMinutes: Int? = when {
            QuickFilter.QUICK in q -> 15
            QuickFilter.TONIGHT in q -> 30
            else -> null
        }
        val minProtein: Int? = when {
            QuickFilter.HIGH_PROTEIN in q -> 30
            QuickFilter.TONIGHT in q -> 25
            else -> null
        }
        return RecipeFilter(
            query = query,
            mealType = selection.mealType,
            maxTotalMinutes = maxMinutes,
            tags = selection.tags,
            favoritesOnly = QuickFilter.FAVORITES in q,
            minProteinG = minProtein,
        )
    }

    /** Filters the repository can't express. */
    fun matchesLocally(r: RecipeEntity): Boolean {
        val q = selection.quick
        if (QuickFilter.MEAL_PREP in q && !r.mealPrepFriendly) return false
        if (QuickFilter.TONIGHT in q && r.difficulty > 2) return false
        return true
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class RecipesViewModel(private val repo: MealRepository) : ViewModel() {

    /** Backed by snapshot state so the text field never lags or jumps its cursor. */
    var query by mutableStateOf("")
        private set

    private val mealType = MutableStateFlow<MealType?>(null)
    private val quick = MutableStateFlow<Set<QuickFilter>>(emptySet())
    private val tags = MutableStateFlow<Set<String>>(emptySet())

    private val selection: Flow<Selection> =
        combine(mealType, quick, tags) { m, q, t -> Selection(m, q, t) }

    private val debouncedQuery: Flow<String> = snapshotFlow { query }
        .debounce { q -> if (q.isBlank()) 0L else 220L }
        .map { it.trim() }
        .distinctUntilChanged()

    private val results: Flow<List<RecipeEntity>> =
        combine(debouncedQuery, selection) { q, s -> Controls(q, s) }
            .distinctUntilChanged()
            .flatMapLatest { c ->
                repo.recipes(c.toFilter()).map { list -> list.filter { c.matchesLocally(it) } }
            }

    val state: StateFlow<RecipesUiState> = combine(
        results,
        repo.recipes().map { it.size },
        repo.allTags(),
        selection,
    ) { list, total, allTags, sel ->
        RecipesUiState(
            recipes = list,
            totalCount = total,
            loading = false,
            mealType = sel.mealType,
            quick = sel.quick,
            selectedTags = sel.tags,
            allTags = allTags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun onQueryChange(value: String) {
        query = value
    }

    fun setMealType(type: MealType?) {
        mealType.value = type
    }

    fun toggleQuick(f: QuickFilter) {
        quick.update { if (f in it) it - f else it + f }
    }

    fun toggleTag(tag: String) {
        tags.update { if (tag in it) it - tag else it + tag }
    }

    fun clearAll() {
        query = ""
        mealType.value = null
        quick.value = emptySet()
        tags.value = emptySet()
    }
}
