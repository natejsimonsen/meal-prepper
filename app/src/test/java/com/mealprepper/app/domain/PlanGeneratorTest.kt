package com.mealprepper.app.domain

import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeWithIngredients
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

    /** Portions actually eaten that day (a batch dinner cooks 2 but you eat 1). */
    private fun eaten(s: PlannedSlot): Int = if (s.slot == MealType.DINNER) 1 else s.servings

    private fun dayKcal(slots: List<PlannedSlot>, day: Int, lib: Map<String, RecipeWithIngredients> = byId) =
        slots.filter { it.dayOffset == day }.sumOf { lib.getValue(it.recipeId).recipe.calories * eaten(it) }

    private fun dayProtein(slots: List<PlannedSlot>, day: Int, lib: Map<String, RecipeWithIngredients> = byId) =
        slots.filter { it.dayOffset == day }.sumOf { lib.getValue(it.recipeId).recipe.proteinG * eaten(it) }

    @Test
    fun fillsEverySlotEveryDay() {
        val slots = plan()
        for (day in 0 until 7) {
            val today = slots.filter { it.dayOffset == day }.map { it.slot }
            assertEquals("day $day: $today", setOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK), today.toSet())
            assertEquals("day $day: $today", 1, today.count { it == MealType.BREAKFAST })
            assertEquals("day $day: $today", 1, today.count { it == MealType.LUNCH })
            assertEquals("day $day: $today", 1, today.count { it == MealType.DINNER })
            assertTrue("day $day: $today", today.count { it == MealType.SNACK } in 1..2)
            assertEquals("sorted by slot", today.sortedBy { it.ordinal }, today)
        }
    }

    @Test
    fun lowCalorieLibraryIsToppedUpToTarget() {
        // 1 serving of everything ≈ 300 + 450 + 500 + 180 = 1430 kcal, far below ~2160.
        val low = ArrayList<RecipeWithIngredients>()
        val proteins = listOf("chicken breast", "salmon", "ground turkey", "lean beef", "shrimp", "tofu")
        for (i in 0 until 10) low += recipe("b$i", MealType.BREAKFAST, calories = 300 + i * 5, protein = 25, servings = 1, cuisine = "C$i", price = 1.0)
        for (i in 0 until 14) low += recipe("l$i", MealType.LUNCH, calories = 450 + i * 5, protein = 35, cuisine = "C${i % 6}",
            ingredients = listOf(Triple(proteins[i % 6], 0.5, "lb")), price = 4.0)
        for (i in 0 until 18) low += recipe("d$i", MealType.DINNER, calories = 500 + i * 5, protein = 40, servings = 4, mealPrep = i % 2 == 0,
            cuisine = "C${(i + 3) % 6}", ingredients = listOf(Triple(proteins[(i + 2) % 6], 1.0, "lb")), price = 8.0)
        for (i in 0 until 8) low += recipe("s$i", MealType.SNACK, calories = 160 + i * 10, protein = 15, servings = 1, minutes = 2, price = 1.0)
        val lib = low.associateBy { it.recipe.id }
        for (seed in 1L..20L) {
            val slots = generator.generate(low, targets, profile, 7, seed)
            for (day in 0 until 7) {
                val kcal = dayKcal(slots, day, lib)
                assertTrue("seed $seed day $day kcal $kcal vs ${targets.calories}",
                    kcal >= targets.calories * 0.9 && kcal <= targets.calories * 1.1)
            }
            // Top-ups never touch dinners or leftovers, so batch accounting holds.
            for (lo in slots.filter { it.isLeftover }) {
                assertEquals(1, lo.servings)
                val dinner = slots.single { it.dayOffset == lo.dayOffset - 1 && it.slot == MealType.DINNER }
                assertEquals(lo.recipeId, dinner.recipeId)
                assertEquals(2, dinner.servings)
            }
            assertTrue(slots.filter { it.slot == MealType.DINNER }.all { d ->
                d.servings == 1 || slots.any { it.isLeftover && it.dayOffset == d.dayOffset + 1 && it.recipeId == d.recipeId }
            })
            // At most two snacks per day; two snacks on one day are different recipes.
            for (day in 0 until 7) {
                val snacks = slots.filter { it.dayOffset == day && it.slot == MealType.SNACK }
                assertTrue(snacks.size <= 2)
                assertEquals(snacks.size, snacks.map { it.recipeId }.toSet().size)
            }
        }
    }

    @Test
    fun noTopUpBeyondTenPercent() {
        // Everything is big: never add extra portions that would push past +10%.
        val big = library().map { rw -> rw.copy(recipe = rw.recipe.copy(calories = rw.recipe.calories + 150)) }
        val lib = big.associateBy { it.recipe.id }
        val slots = generator.generate(big, targets, profile, 7, 9)
        for (day in 0 until 7) {
            val withoutTopUp = slots.filter { it.dayOffset == day }
            assertTrue(withoutTopUp.none { it.slot != MealType.DINNER && !it.isLeftover && it.servings > 1 } ||
                dayKcal(slots, day, lib) <= targets.calories * 1.1)
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
        assertEquals(7, slots.count { it.slot == MealType.BREAKFAST })
        assertEquals(7, slots.count { it.slot == MealType.DINNER })
    }

    @Test
    fun dailyCaloriesAndProteinNearTargets() {
        val slots = plan(seed = 3)
        for (day in 0 until 7) {
            val kcal = dayKcal(slots, day)
            val protein = dayProtein(slots, day)
            assertTrue("day $day kcal $kcal vs ${targets.calories}", kcal in (targets.calories * 0.9).toInt()..(targets.calories * 1.1).toInt())
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
