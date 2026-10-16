# 🥗 Meal Prepper

A private, offline Android meal-prep app built around one question:

> *Healthy, super-easy, high-protein meal prep with real variety — and a shopping list I can use while I'm at the store.*

Built with Kotlin, Jetpack Compose and Material 3, using a custom **Fresh Market** palette and **Poppins** throughout.

## Features

| | |
|---|---|
| **Today** | Calorie and macro rings against your personal targets, today's meals with an "eaten" checkbox, one-tap swim log with streak, and quick picks. |
| **Tonight mode** | "Dinner in 10, bed by 10:30." Low-effort, high-protein dinners. One button adds the ingredients to your list and opens Store mode. |
| **Recipe database** | About 50 original recipes in SQLite (Room), seeded from `assets/recipes.json`. Each has per-serving macros, aisle-tagged ingredients with price estimates, steps, fridge life and a source credit. Works fully offline. |
| **Illustrations** | Every recipe has a bundled vector illustration. You can add your own photo with the system Photo Picker; it's stored in app-private storage. |
| **Week planner** | Generates a varied, protein-forward week. It rotates proteins and cuisines, avoids repeats, turns batch-cooked dinners into next-day lunches, aims near your calorie target and stays within your budget. You can swap, add or remove any meal. |
| **Prep day** | A batch-cooking checklist built from your week's plan. |
| **List builder** | Combines your plan range, extra recipes, your manual items and your pantry exclusions into **one** list. Matching ingredients are merged, units converted, and the total is costed against your budget. |
| **Store mode** | Keeps the screen on, uses large high-contrast rows grouped in store-walk order, has an aisle jump bar and haptic check-off, and celebrates when you're done. |
| **Cook mode** | Step-by-step pager with automatic timers detected from the step text and a keep-awake screen. |
| **Progress** | Swim log (yards and minutes), weight trend chart, water tracking, streaks and a projected goal date. |
| **Recipe editor** | Create or duplicate recipes with your own macros, ingredients, steps, illustration and color. |
| **Privacy** | No `INTERNET` permission, cloud backup and device transfer turned off, no account. Everything stays on your phone. |

## How targets are computed
- **BMR:** Mifflin-St Jeor equation (1990).
- **Swim burn:** from the Compendium of Physical Activities METs, added on top of a sedentary baseline.
- **Deficit:** a gentle 0.5 lb/week by default (about 250 kcal/day). It never drops below your BMR.
- **Protein:** about 0.8 g per lb of goal weight, and at least 1.6 g/kg (ISSN position stand), to protect muscle while you lose fat.

The sources are listed in the app under Profile → About & sources. The numbers are estimates, not medical advice.

## Build
CI (`.github/workflows/android.yml`) runs the unit tests and builds debug and release APKs. Download them from the run's **Artifacts**.

To build locally you need JDK 17+ and the Android SDK (API 36):
```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

## Architecture
See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md). In short: Compose UI → ViewModels (StateFlow) → `MealRepository` / `ProfileRepository` → Room (SQLite) and DataStore. The domain logic (`NutritionCalculator`, `PlanGenerator`, `ShoppingListBuilder`) is pure Kotlin with unit tests.

## Ideas backlog
- Glance home-screen widget: today's meals and list progress.
- Wear OS tile for checking off the list in the store.
- Barcode scan to add pantry items (CameraX and on-device ML Kit, still no network).
- "Use it up" mode: plan around what's already in your fridge.
- On-device AI (Gemini Nano via ML Kit GenAI) to rewrite a recipe with what you have, offline.
- Health Connect: import swims from your watch and write nutrition.
- Encrypted export/import file for moving to a new phone.

Poppins © Indian Type Foundry, SIL Open Font License (`docs/POPPINS_OFL.txt`).
