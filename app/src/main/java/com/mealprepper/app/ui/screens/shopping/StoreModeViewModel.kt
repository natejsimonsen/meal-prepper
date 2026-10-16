package com.mealprepper.app.ui.screens.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoreUiState(
    val loading: Boolean = true,
    /** Aisles in store-walk order; items in a stable alphabetical order so rows never jump. */
    val groups: List<AisleGroup> = emptyList(),
    val totalItems: Int = 0,
    val checkedItems: Int = 0,
    val remainingUsd: Double = 0.0,
    val hideChecked: Boolean = false,
    val budgetUsd: Int = 200,
) {
    val allDone: Boolean get() = totalItems > 0 && checkedItems == totalItems
    val progress: Float get() = if (totalItems == 0) 0f else checkedItems.toFloat() / totalItems
}

class StoreModeViewModel(
    private val meals: MealRepository,
    profiles: ProfileRepository,
) : ViewModel() {

    private val hideChecked = MutableStateFlow(false)

    val state: StateFlow<StoreUiState> = combine(
        meals.shoppingList(), hideChecked, profiles.profile,
    ) { items, hide, profile ->
        val visible = if (hide) items.filter { !it.checked } else items
        StoreUiState(
            loading = false,
            groups = groupByAisle(visible).map { g ->
                g.copy(items = g.items.sortedBy { it.name.lowercase() })
            },
            totalItems = items.size,
            checkedItems = items.count { it.checked },
            remainingUsd = items.filter { !it.checked }.sumOf { it.estPriceUsd },
            hideChecked = hide,
            budgetUsd = profile.weeklyBudgetUsd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StoreUiState())

    fun toggle(item: ShoppingItemEntity) {
        viewModelScope.launch { meals.setChecked(item.id, !item.checked) }
    }

    fun toggleHideChecked() {
        hideChecked.update { !it }
    }
}
