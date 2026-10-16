package com.mealprepper.app.domain

import com.mealprepper.app.data.model.GoalPace
import com.mealprepper.app.data.model.Sex
import com.mealprepper.app.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionCalculatorTest {

    private val user = UserProfile() // 23 y, male, 70 in, 185 lb → 175 lb, gentle, 30 min / 1000 yd

    @Test
    fun bmrMatchesMifflinStJeor() {
        // 83.91 kg, 177.8 cm, 23 y male → 10*83.91 + 6.25*177.8 - 115 + 5 = 1840.4
        val t = NutritionCalculator.targets(user)
        assertEquals(1840, t.bmr)
    }

    @Test
    fun defaultUserTargetsAreSensible() {
        val t = NutritionCalculator.targets(user)
        // 1.2 × 1840 + swim (4.8 net MET × 83.9 kg × 0.5 h ≈ 201) ≈ 2410; gentle −250 → 2160
        assertEquals(2410, t.tdee)
        assertEquals(2160, t.calories)
        assertEquals(0, t.calories % 10)
        // 0.8 g × 175 lb = 140 g (≥ 1.6 g/kg × 83.9 = 134 g)
        assertEquals(140, t.proteinG)
        assertEquals(67, t.fatG) // 28% of 2160 / 9
        assertEquals(t.calories.toDouble(), (t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9).toDouble(), 8.0)
        assertEquals(30, t.fiberG)
        assertEquals(14, t.waterCups) // 185/2/8 = 11.6 + 2 → 14
    }

    @Test
    fun swimAddsCaloriesAndFasterIsMore() {
        val none = NutritionCalculator.targets(user.copy(swimMinutesPerDay = 0, swimYardsPerDay = 0))
        val easy = NutritionCalculator.targets(user)
        val fast = NutritionCalculator.targets(user.copy(swimYardsPerDay = 1800))
        assertTrue(easy.tdee > none.tdee)
        assertTrue(fast.tdee > easy.tdee)
        assertEquals(easy.waterCups - 2, none.waterCups)
    }

    @Test
    fun deficitScalesWithPaceAndMaintainHasNone() {
        val maintain = NutritionCalculator.targets(user.copy(pace = GoalPace.MAINTAIN))
        val gentle = NutritionCalculator.targets(user.copy(pace = GoalPace.GENTLE))
        val steady = NutritionCalculator.targets(user.copy(pace = GoalPace.STEADY))
        assertEquals(maintain.tdee, maintain.calories)
        assertEquals(250.0, (maintain.calories - gentle.calories).toDouble(), 10.0)
        assertEquals(500.0, (maintain.calories - steady.calories).toDouble(), 10.0)
    }

    @Test
    fun neverBelowFloor() {
        val small = UserProfile(
            age = 60, sex = Sex.FEMALE, heightIn = 60, weightLb = 110.0, goalWeightLb = 100.0,
            pace = GoalPace.STEADY, swimMinutesPerDay = 0, swimYardsPerDay = 0,
        )
        val t = NutritionCalculator.targets(small)
        assertTrue("calories ${t.calories} below floor", t.calories >= maxOf(t.bmr, 1200) - 10 || t.calories >= t.tdee - 10)
        val man = NutritionCalculator.targets(user.copy(heightIn = 64, weightLb = 140.0, goalWeightLb = 130.0, age = 50, pace = GoalPace.STEADY, swimMinutesPerDay = 0))
        assertTrue(man.calories >= maxOf(man.bmr, 1500) - 10)
    }

    @Test
    fun proteinAtLeast1point6PerKg() {
        val t = NutritionCalculator.targets(user.copy(weightLb = 250.0, goalWeightLb = 180.0))
        assertTrue(t.proteinG >= (250 * 0.45359237 * 1.6).toInt())
    }

    @Test
    fun noDeficitWhenAtGoal() {
        val t = NutritionCalculator.targets(user.copy(weightLb = 175.0, goalWeightLb = 175.0, pace = GoalPace.STEADY))
        assertEquals(t.tdee, t.calories)
    }
}
