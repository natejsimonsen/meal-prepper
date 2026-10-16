package com.mealprepper.app.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.domain.NutritionCalculator
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class OnboardingUiState(
    /** False until the stored profile (or defaults) has been read once. */
    val loaded: Boolean = false,
    val draft: UserProfile = UserProfile(),
    val targets: NutritionTargets? = null,
    val building: Boolean = false,
)

class OnboardingViewModel(
    private val meals: MealRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {

    private val draft = MutableStateFlow(UserProfile())
    private val loaded = MutableStateFlow(false)
    private val building = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            draft.value = profiles.profile.first()
            loaded.value = true
        }
    }

    val state: StateFlow<OnboardingUiState> = combine(draft, loaded, building) { d, l, b ->
        OnboardingUiState(loaded = l, draft = d, targets = NutritionCalculator.targets(d), building = b)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState())

    /** Edits stay in memory until "Build my first week". */
    fun edit(transform: (UserProfile) -> UserProfile) {
        draft.update(transform)
    }

    fun finish(onDone: () -> Unit) {
        if (building.value) return
        viewModelScope.launch {
            building.value = true
            try {
                val profile = draft.value.copy(onboardingDone = true)
                meals.generatePlan(LocalDate.now(), 7, profile)
                // Navigate first, then persist without cancellation: flipping onboardingDone
                // changes the start destination in MainActivity, which may dispose this screen.
                onDone()
                withContext(NonCancellable) { profiles.update { profile } }
            } finally {
                building.value = false
            }
        }
    }
}
