package com.mealprepper.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprepper.app.AppContainer
import com.mealprepper.app.MealPrepperApp

/** Creates a ViewModel with access to the [AppContainer]. Use [key] for per-argument VMs. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as MealPrepperApp
    return viewModel(key = key) { create(app.container) }
}
