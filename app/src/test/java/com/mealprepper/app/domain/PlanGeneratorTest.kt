package com.mealprepper.app.domain

import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanGeneratorTest {

    private val profile = UserProfile()
    private val targets = NutritionCalculator.targets(profile)
    private val recipes = library()
    private val byId = recipes.associateBy { it.recipe.id }
    private val generator = PlanGenerator()

    private fun plan(seed: Long = 42L, days: Int = 7, p: UserProfile = profile) =
        generator.generate(recipes, targets, p, days, seed)

    @Test
    fun fillsEverySlotEveryDay() {
        val slots = plan()
        for (day in 0 until 7) {
            val today = slots.filter { it.dayOffset == day }.map { it.slot }
            assertEquals("day $day: $today", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK), today)
        }
    }

    @Test
    fun deterministicPerSeed() {
        assertEquals(plan(seed = 7), plan(seed = 7))
        assertEquals(plan(seed = 7), generator.generate(recipes.reversed(), targets, profile, 7, 7))
        val differs = (1L..5L).any { plan(seed = it) != plan(seed = 7) }
        assertTrue("different seeds should give different plans", differs)
    }

    @Test
    fun noCookedRepeatWithinThreeDays() {
        for (seed in 1L..25L) {
            val cooked = plan(seed = seed, days = 14).filter { !it.isLeftover }
            val byRecipe = cooked.groupBy { it.recipeId }
            for ((id, uses) in byRecipe) {
                val days = uses.map { it.dayOffset }.sorted()
                days.zipWithNext().forEach { (a, b) ->
                    assertTrue("seed $seed: $id cooked on days $a and $b", b - a >= PlanGenerator.MIN_REPEAT_GAP_DAYS)
                }
            }
        }
    }

    @Test
    fun leftoversFollowPrepFriendlyDinners() {
        for (seed in 1L..25L) {
            val slots = plan(seed = seed)
            val leftovers = slots.filter { it.isLeftover }
            for (lo in leftovers) {
                assertEquals(MealType.LUNCH, lo.slot)
                assertEquals(1, lo.servings)
                val dinner = slots.single { it.dayOffset == lo.dayOffset - 1 && it.slot == MealType.DINNER }
                assertEquals(dinner.recipeId, lo.recipeId)
                assertEquals(2, dinner.servings)
                assertFalse(dinner.isLeftover)
            }
            // Every prep-friendly dinner (except the last day) produces a leftover lunch.
            for (d in slots.filter { it.slot == MealType.DINNER && it.dayOffset < 6 }) {
                val r = byId.getValue(d.recipeId).recipe
                val hasLeftover = leftovers.any { it.dayOffset == d.dayOffset + 1 && it.recipeId == d.recipeId }
                assertEquals("seed $seed day ${d.dayOffset}", r.mealPrepFriendly && r.servings >= 2, hasLeftover)
            }
            assertTrue("no leftover on day 0", slots.none { it.dayOffset == 0 && it.isLeftover })
        }
    }

    @Test
    fun breakfastAtMostTwicePerWeek() {
        for (seed in 1L..25L) {
            val counts = plan(seed = seed).filter { it.slot == MealType.BREAKFAST }.groupingBy { it.recipeId }.eachCount()
            assertTrue("seed $seed: $counts", counts.values.all { it <= PlanGenerator.MAX_BREAKFAST_PER_WEEK })
        }
    }

    @Test
    fun variedProteinsAndCuisines() {
        for (seed in 1L..10L) {
            val slots = plan(seed = seed)
            val dinners = slots.filter { it.slot == MealType.DINNER }
            assertTrue("seed $seed: dinners not varied", dinners.map { it.recipeId }.toSet().size >= 5)
            val proteins = dinners.map { PlanGenerator.proteinKey(byId.getValue(it.recipeId)) }
            assertTrue("seed $seed proteins $proteins", proteins.toSet().size >= 4)
            // Consecutive dinners shouldn't share a main protein.
            proteins.zipWithNext().forEach { (a, b) -> assertNotEquals("seed $seed: $proteins", a, b) }
        }
    }

    @Test
    fun respectsAvoidTags() {
        val p = profile.copy(avoidTags = setOf("Spicy"))
        for (seed in 1L..10L) {
            assertTrue(generator.generate(recipes, targets, p, 7, seed).none { it.recipeId == "d5" })
        }
    }

    @Test
    fun noSnackWhenDisabled() {
        val slots = plan(p = profile.copy(includeSnack = false))
        assertTrue(slots.none { it.slot == MealType.SNACK })
        assertEquals(21, slots.size)
    }

    @Test
    fun dailyCaloriesAndProteinNearTargets() {
        val slots = plan(seed = 3)
        for (day in 0 until 7) {
            val today = slots.filter { it.dayOffset == day }.map { byId.getValue(it.recipeId).recipe }
            val kcal = today.sumOf { it.calories }
            val protein = today.sumOf { it.proteinG }
            assertTrue("day $day kcal $kcal vs ${targets.calories}", kcal in (targets.calories * 0.85).toInt()..(targets.calories * 1.15).toInt())
            assertTrue("day $day protein $protein vs ${targets.proteinG}", protein >= targets.proteinG * 0.9)
        }
    }

    @Test
    fun staysWithinBudget() {
        val slots = plan(seed = 11)
        val cost = slots.filter { !it.isLeftover }.sumOf { s ->
            val rw = byId.getValue(s.recipeId)
            rw.ingredients.sumOf { it.estPriceUsd } * s.servings / rw.recipe.servings
        }
        assertTrue("weekly cost $cost", cost <= profile.weeklyBudgetUsd)
    }

    @Test
    fun emptyInputsGiveEmptyPlan() {
        assertTrue(generator.generate(emptyList(), targets, profile, 7, 1).isEmpty())
        assertTrue(generator.generate(recipes, targets, profile, 0, 1).isEmpty())
    }

    @Test
    fun replacementDiffersFromCurrent() {
        val current = byId.getValue("d0").recipe
        val r = generator.pickReplacement(recipes, MealType.DINNER, current, setOf("d1", "d2"), profile, 5)
        assertTrue(r != null && r.id != "d0" && r.id !in setOf("d1", "d2"))
    }
}
