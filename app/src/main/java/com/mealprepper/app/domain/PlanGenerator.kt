package com.mealprepper.app.domain

import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.model.UserProfile
import java.time.DayOfWeek
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One meal in a generated plan.
 *
 * @param dayOffset 0-based day from the plan start.
 * @param servings servings to cook/shop for. For breakfast, lunch and snack this equals the
 *   portions eaten (may be 2 to reach the calorie target). A dinner whose leftovers become
 *   tomorrow's lunch is cooked with 2 servings (1 eaten tonight + 1 leftover). Leftovers have
 *   `servings = 1` and need no shopping/cooking. Portions eaten = `if (slot == DINNER) 1 else servings`.
 */
data class PlannedSlot(
    val dayOffset: Int,
    val slot: MealType,
    val recipeId: String,
    val servings: Int,
    val isLeftover: Boolean,
)

/**
 * Builds a varied, protein-forward meal-prep plan. Pure Kotlin and deterministic for a given
 * seed (candidates are visited in id order and the RNG is consumed in a fixed order).
 *
 * Rules (hard constraints are relaxed step by step only when nothing else fits):
 * - No recipe is cooked twice within 3 days (gap ≥ 3 days); planned leftovers are exempt.
 * - A breakfast recipe appears at most twice in any 7-day window.
 * - Recipes matching `profile.avoidTags` (tag, cuisine or main protein) are never used.
 * - Meal-prep-friendly dinners with ≥ 2 servings are cooked as 2 servings and become the next
 *   day's lunch (`isLeftover = true`, `servings = 1`).
 * - Portions: after the main picks, a day below 95% of the calorie target is topped up (≤ 3
 *   steps, never above 110%) by a 2nd serving of breakfast / snack / fresh lunch, or a second
 *   SNACK entry. Dinners are never bumped, so a dinner's `servings` is always its cooked batch
 *   size (1, or 2 when tomorrow's lunch is its leftover).
 * - Scoring favours: daily calories near the target, protein ≥ target, rotating main proteins
 *   and cuisines, quick recipes on weekdays, and staying inside the weekly budget
 *   (cost = Σ ingredient estPriceUsd × servings / recipe.servings, leftovers cost nothing).
 */
class PlanGenerator {

    fun generate(
        recipes: List<RecipeWithIngredients>,
        targets: NutritionTargets,
        profile: UserProfile,
        days: Int,
        seed: Long,
        startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): List<PlannedSlot> {
        if (days <= 0) return emptyList()
        val all = candidatesFrom(recipes, profile)
        if (all.isEmpty()) return emptyList()

        val rng = Random(seed)
        val slots = slotsFor(profile)
        val pools = slots.associateWith { poolFor(it, all) }
        val byId = all.associateBy { it.id }
        val dailyBudget = profile.weeklyBudgetUsd.coerceAtLeast(1) / 7.0

        val cookedDays = HashMap<String, MutableList<Int>>()
        val proteinByDay = HashMap<Int, MutableList<String>>()
        val cuisineByDay = HashMap<Int, MutableList<String>>()
        val result = ArrayList<PlannedSlot>()
        val leftoverLunchFor = HashMap<Int, Candidate>()
        var spent = 0.0

        for (day in 0 until days) {
            val weekend = isWeekend(startDayOfWeek, day)
            val picked = LinkedHashMap<MealType, Candidate>()
            var kcal = 0.0
            var protein = 0.0

            leftoverLunchFor[day]?.let { lo ->
                picked[MealType.LUNCH] = lo
                result += PlannedSlot(day, MealType.LUNCH, lo.id, 1, isLeftover = true)
                kcal += lo.recipe.calories
                protein += lo.recipe.proteinG
            }

            val order = listOf(MealType.DINNER, MealType.LUNCH, MealType.BREAKFAST, MealType.SNACK)
                .filter { it in slots && it !in picked }

            for (slot in order) {
                val unfilledShare = order.filter { it !in picked }.sumOf { share(it, slots) }
                val frac = share(slot, slots) / unfilledShare.coerceAtLeast(1e-6)
                val idealKcal = max(120.0, (targets.calories - kcal) * frac)
                val idealProtein = max(8.0, (targets.proteinG - protein) * frac)

                val canLeftover = slot == MealType.DINNER && MealType.LUNCH in slots && day + 1 < days
                val pool = pools.getValue(slot)
                val choice = pickWithRelaxation(pool, all) { c, level ->
                    allowed(c, slot, day, level, cookedDays, picked)
                }?.let { cands ->
                    cands.maxByOrNull { c ->
                        val leftover = canLeftover && c.leftoverFriendly
                        val cookServings = if (leftover) 2 else 1
                        val cost = c.costPerServing * cookServings
                        score(
                            c = c, slot = slot, day = day, weekend = weekend,
                            idealKcal = idealKcal, idealProtein = idealProtein,
                            proteinByDay = proteinByDay, cuisineByDay = cuisineByDay,
                            picked = picked, leftover = leftover,
                            cost = cost,
                            slotBudget = dailyBudget * share(slot, slots) * cookServings,
                            overBudget = spent + cost > dailyBudget * (day + 1),
                            cookedDays = cookedDays,
                        ) + rng.nextDouble() * RANDOMNESS
                    }
                } ?: continue

                val leftover = canLeftover && choice.leftoverFriendly
                val servings = if (leftover) 2 else 1
                picked[slot] = choice
                result += PlannedSlot(day, slot, choice.id, servings, isLeftover = false)
                kcal += choice.recipe.calories
                protein += choice.recipe.proteinG
                spent += choice.costPerServing * servings
                cookedDays.getOrPut(choice.id) { ArrayList() } += day
                if (slot == MealType.LUNCH || slot == MealType.DINNER) {
                    proteinByDay.getOrPut(day) { ArrayList() } += choice.proteinKey
                    cuisineByDay.getOrPut(day) { ArrayList() } += choice.cuisine
                }
                if (leftover) leftoverLunchFor[day + 1] = choice
            }
            // Close the calorie gap: portions are fixed per recipe, so 1 serving of everything
            // often lands well under target. Add a 2nd portion of breakfast/snack/fresh lunch, or
            // a second snack, whichever lands closest to the target without overshooting +10%.
            val snackPool = pools[MealType.SNACK]?.filter { it.recipe.mealType == MealType.SNACK }.orEmpty()
            var adjustments = 0
            while (adjustments < MAX_CALORIE_ADJUSTMENTS && kcal < targets.calories * CALORIE_LOW) {
                val target = targets.calories.toDouble()
                val upper = target * CALORIE_HIGH
                var bestGap = abs(target - kcal)
                var bestBump = -1
                var bestSnack: Candidate? = null
                for (i in result.indices) {
                    val ps = result[i]
                    if (ps.dayOffset != day || ps.isLeftover || ps.servings >= 2) continue
                    if (ps.slot == MealType.DINNER) continue // dinner servings = cooked batch size
                    val cal = byId.getValue(ps.recipeId).recipe.calories
                    val newKcal = kcal + cal
                    if (newKcal <= upper && abs(target - newKcal) < bestGap) {
                        bestGap = abs(target - newKcal); bestBump = i
                    }
                }
                val snacksToday = result.count { it.dayOffset == day && it.slot == MealType.SNACK }
                if (MealType.SNACK in slots && snacksToday < MAX_SNACKS_PER_DAY && snackPool.isNotEmpty()) {
                    val todayIds = result.filter { it.dayOffset == day }.map { it.recipeId }.toSet()
                    val extra = (0..2).asSequence()
                        .map { level ->
                            snackPool.filter { it.id !in todayIds && allowed(it, MealType.SNACK, day, level, cookedDays, picked) }
                        }
                        .firstOrNull { it.isNotEmpty() }
                        ?.filter { kcal + it.recipe.calories <= upper }
                        ?.minWithOrNull(
                            compareBy<Candidate>({ abs(target - kcal - it.recipe.calories) }, { -it.recipe.proteinG }, { it.id })
                        )
                    if (extra != null && abs(target - kcal - extra.recipe.calories) < bestGap) {
                        bestSnack = extra; bestBump = -1
                    }
                }
                val snack = bestSnack
                if (snack != null) {
                    result += PlannedSlot(day, MealType.SNACK, snack.id, 1, isLeftover = false)
                    kcal += snack.recipe.calories
                    protein += snack.recipe.proteinG
                    spent += snack.costPerServing
                    cookedDays.getOrPut(snack.id) { ArrayList() } += day
                } else if (bestBump >= 0) {
                    val ps = result[bestBump]
                    val c = byId.getValue(ps.recipeId)
                    result[bestBump] = ps.copy(servings = ps.servings + 1)
                    kcal += c.recipe.calories
                    protein += c.recipe.proteinG
                    spent += c.costPerServing
                } else {
                    break
                }
                adjustments++
            }

            // Leftover lunches also count for tomorrow's protein/cuisine rotation.
            leftoverLunchFor[day]?.let { lo ->
                proteinByDay.getOrPut(day) { ArrayList() } += lo.proteinKey
                cuisineByDay.getOrPut(day) { ArrayList() } += lo.cuisine
            }
        }
        return result.sortedWith(compareBy({ it.dayOffset }, { it.slot.ordinal }))
    }

    /**
     * Picks a different recipe for one slot (used by "swap meal"). Prefers recipes close in
     * calories to [current], high in protein, not in [avoidRecipeIds] (e.g. meals within ±3 days).
     */
    fun pickReplacement(
        recipes: List<RecipeWithIngredients>,
        slot: MealType,
        current: RecipeEntity?,
        avoidRecipeIds: Set<String>,
        profile: UserProfile,
        seed: Long,
    ): RecipeEntity? {
        val all = candidatesFrom(recipes, profile)
        val pool = poolFor(slot, all).filter { it.id != current?.id }
        val fresh = pool.filter { it.id !in avoidRecipeIds }
        val cands = fresh.ifEmpty { pool }.ifEmpty { all.filter { it.id != current?.id } }
        if (cands.isEmpty()) return null
        val rng = Random(seed)
        val refKcal = (current?.calories ?: defaultKcal(slot)).toDouble().coerceAtLeast(100.0)
        val currentProteinKey = current?.let { cur -> all.firstOrNull { it.id == cur.id }?.proteinKey }
        return cands.maxByOrNull { c ->
            var s = -2.0 * abs(c.recipe.calories - refKcal) / refKcal
            s += 1.0 * c.proteinDensity
            if (c.recipe.mealType == slot) s += 0.5
            if (currentProteinKey != null && c.proteinKey == currentProteinKey) s -= 0.4
            if (c.recipe.isFavorite) s += 0.2
            s + rng.nextDouble() * RANDOMNESS
        }?.recipe
    }

    // ------------------------------------------------------------------ internals

    private class Candidate(
        val recipe: RecipeEntity,
        val costPerServing: Double,
        val proteinKey: String,
        val cuisine: String,
    ) {
        val id: String get() = recipe.id
        val leftoverFriendly: Boolean =
            recipe.mealPrepFriendly && recipe.servings >= 2 && recipe.fridgeDays >= 1
        val proteinDensity: Double =
            if (recipe.calories > 0) recipe.proteinG * 4.0 / recipe.calories else 0.0
        val totalMinutes: Int = recipe.prepMinutes + recipe.cookMinutes
    }

    private fun candidatesFrom(recipes: List<RecipeWithIngredients>, profile: UserProfile): List<Candidate> {
        val avoid = profile.avoidTags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return recipes
            .distinctBy { it.recipe.id }
            .sortedBy { it.recipe.id }
            .map { rw ->
                val servings = rw.recipe.servings.coerceAtLeast(1)
                Candidate(
                    recipe = rw.recipe,
                    costPerServing = rw.ingredients.sumOf { it.estPriceUsd.coerceAtLeast(0.0) } / servings,
                    proteinKey = proteinKey(rw),
                    cuisine = rw.recipe.cuisine.trim().lowercase(),
                )
            }
            .filter { c ->
                avoid.isEmpty() || (
                    c.recipe.tags.none { it.trim().lowercase() in avoid } &&
                        c.cuisine !in avoid && c.proteinKey !in avoid
                    )
            }
    }

    private fun slotsFor(profile: UserProfile): List<MealType> {
        val meals = when {
            profile.mealsPerDay <= 1 -> listOf(MealType.DINNER)
            profile.mealsPerDay == 2 -> listOf(MealType.LUNCH, MealType.DINNER)
            else -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        }
        val snack = profile.includeSnack || profile.mealsPerDay >= 4
        return if (snack) meals + MealType.SNACK else meals
    }

    private fun share(slot: MealType, slots: List<MealType>): Double {
        val raw = when (slot) {
            MealType.BREAKFAST -> 0.25
            MealType.LUNCH -> 0.30
            MealType.DINNER -> 0.35
            MealType.SNACK -> 0.10
        }
        val total = slots.sumOf {
            when (it) {
                MealType.BREAKFAST -> 0.25
                MealType.LUNCH -> 0.30
                MealType.DINNER -> 0.35
                MealType.SNACK -> 0.10
            }
        }
        return raw / total
    }

    private fun defaultKcal(slot: MealType): Int = when (slot) {
        MealType.BREAKFAST -> 450
        MealType.LUNCH -> 600
        MealType.DINNER -> 650
        MealType.SNACK -> 250
    }

    private fun poolFor(slot: MealType, all: List<Candidate>): List<Candidate> {
        val pool = when (slot) {
            MealType.BREAKFAST -> all.filter { it.recipe.mealType == MealType.BREAKFAST }
            MealType.SNACK -> all.filter { it.recipe.mealType == MealType.SNACK }
            MealType.LUNCH, MealType.DINNER ->
                all.filter { it.recipe.mealType == MealType.LUNCH || it.recipe.mealType == MealType.DINNER }
        }
        return pool.ifEmpty { all }
    }

    private inline fun pickWithRelaxation(
        pool: List<Candidate>,
        all: List<Candidate>,
        allowed: (Candidate, Int) -> Boolean,
    ): List<Candidate>? {
        for (level in 0..2) {
            val c = pool.filter { allowed(it, level) }
            if (c.isNotEmpty()) return c
        }
        val any = all.filter { allowed(it, 2) }
        return any.ifEmpty { null }
    }

    /**
     * Level 0: strict. Level 1: may repeat after 2 days, breakfast weekly cap ignored.
     * Level 2: only "not already on today's menu" (and not cooked yesterday if avoidable).
     */
    private fun allowed(
        c: Candidate,
        slot: MealType,
        day: Int,
        level: Int,
        cookedDays: Map<String, List<Int>>,
        picked: Map<MealType, Candidate>,
    ): Boolean {
        if (picked.values.any { it.id == c.id }) return false
        val uses = cookedDays[c.id].orEmpty()
        val last = uses.maxOrNull()
        return when (level) {
            0 -> {
                val gapOk = last == null || day - last >= MIN_REPEAT_GAP_DAYS
                val weeklyOk = slot != MealType.BREAKFAST ||
                    uses.count { it > day - 7 } < MAX_BREAKFAST_PER_WEEK
                gapOk && weeklyOk
            }
            1 -> last == null || day - last >= 2
            else -> true
        }
    }

    private fun score(
        c: Candidate,
        slot: MealType,
        day: Int,
        weekend: Boolean,
        idealKcal: Double,
        idealProtein: Double,
        proteinByDay: Map<Int, List<String>>,
        cuisineByDay: Map<Int, List<String>>,
        picked: Map<MealType, Candidate>,
        leftover: Boolean,
        cost: Double,
        slotBudget: Double,
        overBudget: Boolean,
        cookedDays: Map<String, List<Int>>,
    ): Double {
        val r = c.recipe
        var s = 0.0

        // Calories near the slot's share of what's left today; protein toward the target.
        s -= 3.0 * abs(r.calories - idealKcal) / max(idealKcal, 150.0)
        s += 1.5 * min(r.proteinG / idealProtein, 1.3)
        s += 1.0 * c.proteinDensity

        // Rotation of main protein and cuisine (lunch/dinner only — eggs at breakfast are fine).
        if (slot == MealType.LUNCH || slot == MealType.DINNER) {
            val todayOther = picked.values.filter { it.id != c.id }
            if (c.proteinKey != OTHER && todayOther.any { it.proteinKey == c.proteinKey }) s -= 1.5
            if (c.proteinKey != OTHER && proteinByDay[day - 1].orEmpty().contains(c.proteinKey)) s -= 1.2
            if (c.proteinKey != OTHER && proteinByDay[day - 2].orEmpty().contains(c.proteinKey)) s -= 0.4
            if (c.cuisine.isNotEmpty() && todayOther.any { it.cuisine == c.cuisine }) s -= 0.6
            if (c.cuisine.isNotEmpty() && cuisineByDay[day - 1].orEmpty().contains(c.cuisine)) s -= 0.6
        }
        // Prefer fresh picks even when repeats are allowed.
        val uses = cookedDays[c.id].orEmpty()
        s -= 0.35 * uses.count { it > day - 7 }

        // Slot fit.
        if (slot == MealType.LUNCH && r.mealType == MealType.DINNER) s -= 0.3
        if (slot == MealType.DINNER && r.mealType == MealType.LUNCH) s -= 0.5

        // Effort: quick on weekdays, more leeway on weekends.
        val minutes = c.totalMinutes
        s -= if (weekend) 0.015 * max(0, minutes - 35) else 0.04 * max(0, minutes - 20)
        if (!weekend && r.difficulty >= 3) s -= 0.4

        // Cook once, eat twice.
        if (leftover) s += 0.6

        // Budget.
        if (slotBudget > 0) s -= 1.5 * max(0.0, cost / slotBudget - 1.0)
        if (overBudget) s -= 0.5

        if (r.isFavorite) s += 0.3
        return s
    }

    private fun isWeekend(start: DayOfWeek, offset: Int): Boolean {
        val dow = start.plus(offset.toLong())
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY
    }

    companion object {
        const val MIN_REPEAT_GAP_DAYS: Int = 3
        const val MAX_BREAKFAST_PER_WEEK: Int = 2
        private const val RANDOMNESS = 0.8
        /** Top up a day while it is below 95% of target; never push it above 110%. */
        private const val CALORIE_LOW = 0.95
        private const val CALORIE_HIGH = 1.10
        private const val MAX_CALORIE_ADJUSTMENTS = 3
        private const val MAX_SNACKS_PER_DAY = 2
        private const val OTHER = "other"

        private val PROTEIN_KEYWORDS: List<Pair<String, String>> = listOf(
            "salmon" to "salmon", "tuna" to "tuna", "shrimp" to "shrimp", "cod" to "white fish",
            "tilapia" to "white fish", "fish" to "white fish",
            "chicken" to "chicken", "turkey" to "turkey",
            "beef" to "beef", "steak" to "beef", "sirloin" to "beef",
            "pork" to "pork", "ham" to "pork", "bacon" to "pork", "sausage" to "pork",
            "tofu" to "tofu", "tempeh" to "tempeh", "edamame" to "tofu",
            "lentil" to "lentils", "chickpea" to "beans", "black bean" to "beans", "bean" to "beans",
            "egg white" to "egg", "egg" to "egg",
            "greek yogurt" to "dairy", "cottage cheese" to "dairy", "skyr" to "dairy",
            "protein powder" to "whey", "whey" to "whey",
        )
        private val PROTEIN_REGEX: List<Pair<Regex, String>> = PROTEIN_KEYWORDS.map { (kw, key) ->
            Regex("\\b" + Regex.escape(kw) + "(s|es)?\\b", RegexOption.IGNORE_CASE) to key
        }

        /** Main protein used for rotation, e.g. "chicken", "salmon", "beans", or "other". */
        fun proteinKey(rw: RecipeWithIngredients): String {
            val meat = rw.ingredients.filter { it.aisle == Aisle.MEAT_SEAFOOD }.sortedBy { it.position }
            val rest = rw.ingredients.filter { it.aisle != Aisle.MEAT_SEAFOOD }.sortedBy { it.position }
            val texts = meat.map { it.name } + listOf(rw.recipe.title) + rest.map { it.name }
            for (text in texts) {
                for ((re, key) in PROTEIN_REGEX) if (re.containsMatchIn(text)) return key
            }
            return OTHER
        }
    }
}
