# Meal Prepper — architecture & team contract

Kotlin + Jetpack Compose (Material 3) + Room (SQLite) + DataStore + kotlinx.serialization +
Navigation Compose (type-safe routes) + Coil 3. minSdk 26, compileSdk 36. Package `com.mealprepper.app`.
**100% offline & private**: no INTERNET permission, backups disabled, all data on device.

## IMPORTANT for contributors working in this sandbox
- There is NO Android SDK / Google Maven here, so the code cannot be compiled locally. CI (GitHub Actions)
  is the compiler. Write code as if a strict reviewer compiles it: correct imports, no invented APIs,
  exhaustive `when`, explicit types where inference is shaky. Prefer stable APIs; if you use an
  `@ExperimentalMaterial3Api` API, annotate with `@OptIn(ExperimentalMaterial3Api::class)`.
- Versions: Kotlin 2.2.10, Compose BOM 2025.08.00 (Material3 1.3.x), Navigation 2.9.3, Room 2.7.2,
  Lifecycle 2.9.2, Coil 3.3.0, kotlinx.serialization 1.9.0. Material icons extended is available
  (`Icons.Rounded.*`, `Icons.Outlined.*`; use `Icons.AutoMirrored.Rounded.ArrowBack` for back arrows).
- Only create/edit files you own (listed in your task). Do not touch shared files; if you need
  something from a shared file, define a private helper in your own file instead.
- Do not run git commit / push.

## Shared files (already written — READ them, don't change them)
- `data/model/Entities.kt` — Room entities: RecipeEntity, IngredientEntity, RecipeWithIngredients,
  MealPlanEntryEntity, PlannedMeal, ShoppingItemEntity, PantryItemEntity, DailyLogEntity, enums
  MealType, Aisle, and `Units.ALL`.
- `data/model/Profile.kt` — UserProfile, Sex, GoalPace, NutritionTargets.
- `data/repo/Repositories.kt` — `MealRepository`, `ProfileRepository` interfaces, RecipeFilter,
  ListBuildRequest, ShoppingSummary.
- `AppContainer.kt` — `container.mealRepository`, `container.profileRepository`.
- `ui/components/AppViewModel.kt` — `appViewModel(key) { container -> MyViewModel(...) }`.
- `ui/components/Common.kt` — RecipeImage, MacroChip, MacroRing, SectionHeader, EmptyState,
  RecipeCard, parseHex, formatQuantity, formatUsd.
- `ui/components/FoodImageKey.kt` — illustration keys.
- `ui/theme/*` — MealPrepperTheme, palette (Basil/Apricot/Plum/Tomato/Lemon, Paper/Ink…),
  ProteinColor/CarbsColor/FatColor/CaloriesColor, Poppins typography via MaterialTheme.typography.
- `ui/nav/AppNavHost.kt` + `Routes.kt` — defines the exact screen function signatures (see below).

## Domain API (implemented by the data-layer owner, usable by UI)
```kotlin
package com.mealprepper.app.domain
object NutritionCalculator { fun targets(p: UserProfile): NutritionTargets }
```

## Screen signatures (must match AppNavHost exactly)
Top-level tab screens receive `contentPadding: PaddingValues` (the bottom bar height) — apply it
as bottom content padding of the scrolling list. All screens handle their own status-bar insets
(use Scaffold/TopAppBar or `Modifier.statusBarsPadding()`), since the app is edge-to-edge.

```kotlin
// ui/screens/today
@Composable fun TodayScreen(contentPadding: PaddingValues, onOpenRecipe: (String) -> Unit, onOpenPlan: () -> Unit, onOpenList: () -> Unit, onOpenTonight: () -> Unit, onOpenProfile: () -> Unit, onOpenProgress: () -> Unit)
// ui/screens/tonight
@Composable fun TonightScreen(onBack: () -> Unit, onOpenRecipe: (String) -> Unit, onOpenStoreMode: () -> Unit)
// ui/screens/onboarding
@Composable fun OnboardingScreen(onDone: () -> Unit)
// ui/screens/profile
@Composable fun ProfileScreen(onBack: () -> Unit, onOpenPantry: () -> Unit, onOpenProgress: () -> Unit)
// ui/screens/progress
@Composable fun ProgressScreen(onBack: () -> Unit)
// ui/screens/recipes
@Composable fun RecipesScreen(contentPadding: PaddingValues, onOpenRecipe: (String) -> Unit, onCreateRecipe: () -> Unit)
@Composable fun RecipeDetailScreen(recipeId: String, onBack: () -> Unit, onCook: (String) -> Unit, onEdit: (String) -> Unit)
@Composable fun CookModeScreen(recipeId: String, onBack: () -> Unit)
@Composable fun RecipeEditorScreen(recipeId: String?, onBack: () -> Unit, onSaved: (String) -> Unit)
// ui/screens/plan
@Composable fun PlanScreen(contentPadding: PaddingValues, onOpenRecipe: (String) -> Unit, onBuildList: () -> Unit)
// ui/screens/shopping
@Composable fun ShoppingListScreen(contentPadding: PaddingValues, onOpenBuilder: () -> Unit, onOpenStoreMode: () -> Unit, onOpenPantry: () -> Unit)
@Composable fun ListBuilderScreen(onBack: () -> Unit, onBuilt: () -> Unit)
@Composable fun StoreModeScreen(onBack: () -> Unit)
// ui/screens/pantry
@Composable fun PantryScreen(onBack: () -> Unit)
```

## Recipe seed JSON — `app/src/main/assets/recipes.json`
```json
{
  "version": 1,
  "sources": [ { "name": "USDA FoodData Central", "url": "https://fdc.nal.usda.gov/", "note": "..." } ],
  "recipes": [ {
    "id": "kebab-case-unique",
    "title": "…", "subtitle": "short hook", "description": "1–2 sentences",
    "mealType": "BREAKFAST|LUNCH|DINNER|SNACK",
    "cuisine": "Mediterranean",
    "prepMinutes": 5, "cookMinutes": 10, "servings": 2,
    "calories": 520, "proteinG": 45, "carbsG": 40, "fatG": 18, "fiberG": 7,
    "imageKey": "salmon",
    "accentColor": "#F29E4C",
    "tags": ["quick", "high-protein"],
    "difficulty": 1,
    "mealPrepFriendly": true,
    "fridgeDays": 4,
    "sourceName": "…", "sourceUrl": "https://…",
    "ingredients": [ { "name": "chicken breast", "quantity": 1.0, "unit": "lb", "aisle": "MEAT_SEAFOOD",
                       "estPriceUsd": 5.5, "note": "boneless skinless", "pantryStaple": false } ],
    "steps": [ "…" ]
  } ]
}
```
Nutrition is per serving; ingredient quantities/prices are for the whole recipe (all servings).
`note` may be null/omitted; `pantryStaple` defaults false.
