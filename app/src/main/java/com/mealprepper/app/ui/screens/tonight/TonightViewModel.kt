package com.mealprepper.app.ui.screens.tonight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TonightUiState(
    val loading: Boolean = true,
    val picks: List<RecipeEntity> = emptyList(),
    val selectedId: String? = null,
    /** Ingredients of the selected pick, once loaded. */
    val selected: RecipeWithIngredients? = null,
    /** Servings to shop for: 1 (just tonight) or the recipe's full batch. */
    val servings: Int = 1,
    val adding: Boolean = false,
)

class TonightViewModel(
    private val meals: MealRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val selectedId = MutableStateFlow<String?>(null)
    private val servings = MutableStateFlow(1)
    private val adding = MutableStateFlow(false)

    private val picks: Flow<List<RecipeEntity>> =
        combine(meals.tonightPicks(10), profiles.profile) { list, profile ->
            val avoid = profile.avoidTags
            val filtered = if (avoid.isEmpty()) list else list.filter { r -> r.tags.none { it in avoid } }
            // Never filter down to nothing: tonight needs an answer.
            (filtered.ifEmpty { list }).take(6)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selected: Flow<RecipeWithIngredients?> = selectedId.flatMapLatest { id ->
        if (id == null) flowOf<RecipeWithIngredients?>(null) else meals.recipe(id)
    }

    val state: StateFlow<TonightUiState> =
        combine(picks, selectedId, selected, servings, adding) { list, id, sel, s, a ->
            TonightUiState(
                loading = false,
                picks = list,
                selectedId = id,
                selected = sel?.takeIf { it.recipe.id == id },
                servings = s,
                adding = a,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TonightUiState())

    fun toggle(id: String) {
        selectedId.value = if (selectedId.value == id) null else id
        servings.value = 1
    }

    fun setServings(value: Int) {
        servings.value = value.coerceAtLeast(1)
    }

    fun addToListAndShop(onDone: () -> Unit) {
        val id = selectedId.value ?: return
        if (adding.value) return
        viewModelScope.launch {
            adding.value = true
            try {
                meals.addRecipeToList(id, servings.value)
                onDone()
            } finally {
                adding.value = false
            }
        }
    }
}
