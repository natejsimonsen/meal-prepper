package com.mealprepper.app.ui.screens.pantry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PantryUiState(
    val loading: Boolean = true,
    val items: List<String> = emptyList(),
    /** Suggested staples not yet in the pantry. */
    val suggestions: List<String> = emptyList(),
)

class PantryViewModel(
    private val meals: MealRepository,
    @Suppress("UNUSED_PARAMETER") profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<PantryUiState> = meals.pantry()
        .map { list ->
            val names = list.map { it.name }.sortedBy { it.lowercase() }
            val have = names.map { it.lowercase() }.toSet()
            PantryUiState(
                loading = false,
                items = names,
                suggestions = SUGGESTED_STAPLES.filter { it !in have },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PantryUiState())

    /** Returns false when the name is blank. */
    fun add(raw: String): Boolean {
        val name = normalize(raw)
        if (name.isEmpty()) return false
        viewModelScope.launch { meals.addPantry(name) }
        return true
    }

    fun remove(name: String) {
        viewModelScope.launch { meals.removePantry(name) }
    }

    companion object {
        /** Pantry names are canonical lowercase, matching ingredient names. */
        fun normalize(raw: String): String = raw.trim().lowercase().replace(Regex("\\s+"), " ")

        val SUGGESTED_STAPLES: List<String> = listOf(
            "olive oil", "kosher salt", "black pepper", "garlic powder", "soy sauce", "honey",
            "cumin", "paprika", "chili flakes", "rice vinegar", "onion powder", "dried oregano",
            "cinnamon", "sesame oil", "hot sauce", "dijon mustard", "cooking spray", "chili powder",
        )
    }
}
