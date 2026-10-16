package com.mealprepper.app.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mealprepper.app.ui.screens.onboarding.OnboardingScreen
import com.mealprepper.app.ui.screens.pantry.PantryScreen
import com.mealprepper.app.ui.screens.plan.PlanScreen
import com.mealprepper.app.ui.screens.profile.ProfileScreen
import com.mealprepper.app.ui.screens.progress.ProgressScreen
import com.mealprepper.app.ui.screens.recipes.CookModeScreen
import com.mealprepper.app.ui.screens.recipes.RecipeDetailScreen
import com.mealprepper.app.ui.screens.recipes.RecipeEditorScreen
import com.mealprepper.app.ui.screens.recipes.RecipesScreen
import com.mealprepper.app.ui.screens.shopping.ListBuilderScreen
import com.mealprepper.app.ui.screens.shopping.ShoppingListScreen
import com.mealprepper.app.ui.screens.shopping.StoreModeScreen
import com.mealprepper.app.ui.screens.today.TodayScreen
import com.mealprepper.app.ui.screens.tonight.TonightScreen
import kotlin.reflect.KClass

private data class TopLevel(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val topLevels = listOf(
    TopLevel(TodayRoute, TodayRoute::class, "Today", Icons.Outlined.Home, Icons.Rounded.Home),
    TopLevel(RecipesRoute, RecipesRoute::class, "Recipes", Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    TopLevel(PlanRoute, PlanRoute::class, "Plan", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TopLevel(ShoppingRoute, ShoppingRoute::class, "List", Icons.Outlined.ShoppingBag, Icons.Rounded.ShoppingBag),
)

@Composable
fun AppNavHost(startOnboarding: Boolean, nav: NavHostController = rememberNavController()) {
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val showBar = topLevels.any { t -> dest?.hierarchy?.any { it.hasRoute(t.routeClass) } == true }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    topLevels.forEach { t ->
                        val selected = dest?.hierarchy?.any { it.hasRoute(t.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Screens draw their own top bars / insets; we only reserve space for the bottom bar.
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = if (startOnboarding) OnboardingRoute else TodayRoute,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(onDone = {
                        nav.navigate(TodayRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
                    })
                }
                composable<TodayRoute> {
                    TodayScreen(
                        contentPadding = padding,
                        onOpenRecipe = { nav.navigate(RecipeDetailRoute(it)) },
                        onOpenPlan = { nav.switchTab(PlanRoute) },
                        onOpenList = { nav.switchTab(ShoppingRoute) },
                        onOpenTonight = { nav.navigate(TonightRoute) },
                        onOpenProfile = { nav.navigate(ProfileRoute) },
                        onOpenProgress = { nav.navigate(ProgressRoute) },
                    )
                }
                composable<RecipesRoute> {
                    RecipesScreen(
                        contentPadding = padding,
                        onOpenRecipe = { nav.navigate(RecipeDetailRoute(it)) },
                        onCreateRecipe = { nav.navigate(RecipeEditorRoute(null)) },
                    )
                }
                composable<PlanRoute> {
                    PlanScreen(
                        contentPadding = padding,
                        onOpenRecipe = { nav.navigate(RecipeDetailRoute(it)) },
                        onBuildList = { nav.navigate(ListBuilderRoute) },
                    )
                }
                composable<ShoppingRoute> {
                    ShoppingListScreen(
                        contentPadding = padding,
                        onOpenBuilder = { nav.navigate(ListBuilderRoute) },
                        onOpenStoreMode = { nav.navigate(StoreModeRoute) },
                        onOpenPantry = { nav.navigate(PantryRoute) },
                    )
                }
                composable<TonightRoute> {
                    TonightScreen(
                        onBack = { nav.popBackStack() },
                        onOpenRecipe = { nav.navigate(RecipeDetailRoute(it)) },
                        onOpenStoreMode = { nav.navigate(StoreModeRoute) },
                    )
                }
                composable<RecipeDetailRoute> { entry ->
                    val id = entry.toRoute<RecipeDetailRoute>().id
                    RecipeDetailScreen(
                        recipeId = id,
                        onBack = { nav.popBackStack() },
                        onCook = { nav.navigate(CookModeRoute(it)) },
                        onEdit = { nav.navigate(RecipeEditorRoute(it)) },
                    )
                }
                composable<CookModeRoute> { entry ->
                    CookModeScreen(recipeId = entry.toRoute<CookModeRoute>().id, onBack = { nav.popBackStack() })
                }
                composable<RecipeEditorRoute> { entry ->
                    RecipeEditorScreen(
                        recipeId = entry.toRoute<RecipeEditorRoute>().id,
                        onBack = { nav.popBackStack() },
                        onSaved = { id ->
                            nav.popBackStack()
                            nav.navigate(RecipeDetailRoute(id)) { launchSingleTop = true }
                        },
                    )
                }
                composable<ListBuilderRoute> {
                    ListBuilderScreen(
                        onBack = { nav.popBackStack() },
                        onBuilt = {
                            nav.popBackStack()
                            nav.switchTab(ShoppingRoute)
                        },
                    )
                }
                composable<StoreModeRoute> { StoreModeScreen(onBack = { nav.popBackStack() }) }
                composable<PantryRoute> { PantryScreen(onBack = { nav.popBackStack() }) }
                composable<ProfileRoute> {
                    ProfileScreen(
                        onBack = { nav.popBackStack() },
                        onOpenPantry = { nav.navigate(PantryRoute) },
                        onOpenProgress = { nav.navigate(ProgressRoute) },
                    )
                }
                composable<ProgressRoute> { ProgressScreen(onBack = { nav.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
