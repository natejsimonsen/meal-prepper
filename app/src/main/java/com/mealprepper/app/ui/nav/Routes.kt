package com.mealprepper.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable object TodayRoute
@Serializable object RecipesRoute
@Serializable object PlanRoute
@Serializable object ShoppingRoute
@Serializable object TonightRoute
@Serializable object OnboardingRoute
@Serializable object ProfileRoute
@Serializable object ProgressRoute
@Serializable object PantryRoute
@Serializable object ListBuilderRoute
@Serializable object StoreModeRoute
@Serializable data class RecipeDetailRoute(val id: String)
@Serializable data class CookModeRoute(val id: String)
/** id == null → new recipe. */
@Serializable data class RecipeEditorRoute(val id: String? = null)
