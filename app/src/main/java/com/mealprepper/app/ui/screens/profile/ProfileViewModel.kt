package com.mealprepper.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.domain.NutritionCalculator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileUiState(
    val loaded: Boolean = false,
    val profile: UserProfile = UserProfile(),
    val targets: NutritionTargets? = null,
)

class ProfileViewModel(
    @Suppress("unused") private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = profiles.profile
        .map { p -> ProfileUiState(loaded = true, profile = p, targets = NutritionCalculator.targets(p)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    /** Auto-saves. Writes are non-cancellable so a quick "back" never loses an edit. */
    fun edit(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            withContext(NonCancellable) { profiles.update(transform) }
        }
    }

    fun toggleAvoidTag(tag: String) = edit { p ->
        p.copy(avoidTags = if (tag in p.avoidTags) p.avoidTags - tag else p.avoidTags + tag)
    }
}
