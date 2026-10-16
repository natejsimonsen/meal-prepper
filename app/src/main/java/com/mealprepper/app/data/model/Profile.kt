package com.mealprepper.app.data.model

enum class Sex { MALE, FEMALE }

/** How fast to lose. Slow & steady is the default. */
enum class GoalPace(val label: String, val lbPerWeek: Double) {
    MAINTAIN("Maintain", 0.0),
    GENTLE("Gentle · 0.5 lb/wk", 0.5),
    STEADY("Steady · 1 lb/wk", 1.0),
}

data class UserProfile(
    val name: String = "",
    val age: Int = 23,
    val sex: Sex = Sex.MALE,
    val heightIn: Int = 70,
    val weightLb: Double = 185.0,
    val goalWeightLb: Double = 175.0,
    val pace: GoalPace = GoalPace.GENTLE,
    val swimMinutesPerDay: Int = 30,
    val swimYardsPerDay: Int = 1000,
    val weeklyBudgetUsd: Int = 200,
    val mealsPerDay: Int = 3,
    val includeSnack: Boolean = true,
    val useDynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Tags to avoid, e.g. "spicy". */
    val avoidTags: Set<String> = emptySet(),
)

data class NutritionTargets(
    val bmr: Int,
    val tdee: Int,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val waterCups: Int,
)
