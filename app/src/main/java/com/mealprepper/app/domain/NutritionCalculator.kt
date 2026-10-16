package com.mealprepper.app.domain

import com.mealprepper.app.data.model.NutritionTargets
import com.mealprepper.app.data.model.Sex
import com.mealprepper.app.data.model.UserProfile
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Daily calorie / macro targets. Pure Kotlin, no Android dependencies.
 *
 * Method and assumptions:
 * 1. **BMR** — Mifflin-St Jeor (Mifflin MD, St Jeor ST, et al. "A new predictive equation for
 *    resting energy expenditure in healthy individuals." *Am J Clin Nutr* 1990;51(2):241-247):
 *    `10·kg + 6.25·cm − 5·age + 5` (male) or `− 161` (female). Weight lb→kg ×0.45359237,
 *    height in→cm ×2.54.
 * 2. **Non-exercise activity** — sedentary factor 1.2 × BMR. The daily swim is added separately
 *    rather than bumping the whole multiplier, so changing the swim changes the target.
 * 3. **Swim energy** — METs from the Compendium of Physical Activities (Ainsworth BE et al.,
 *    2011 update; Herrmann SD et al., 2024 adult Compendium): freestyle light/moderate ≈ 5.8 MET,
 *    freestyle fast/vigorous ≈ 9.8 MET, leisurely/mixed ≈ 4.8 MET. MET is picked from the pace
 *    in yards/minute (1000 yd in 30 min ≈ 33 yd/min → light freestyle). Only the *net* cost
 *    `(MET − 1) · kg · hours` is added, since resting energy is already counted in the BMR.
 * 4. **Deficit** — `pace.lbPerWeek × 500 kcal/day` (≈3500 kcal per lb of fat, a rough rule of
 *    thumb). No deficit when the goal weight is not below current weight. Calories never go
 *    below `max(BMR, 1500)` for men or `max(BMR, 1200)` for women; rounded to the nearest 10.
 * 5. **Protein** — 0.8 g per lb of goal weight, but at least 1.6 g/kg of current body weight,
 *    in line with the ISSN position stand on protein and exercise (Jäger R et al., *J Int Soc
 *    Sports Nutr* 2017;14:20: 1.4–2.0 g/kg/day; higher intakes help preserve lean mass in a
 *    deficit).
 * 6. **Fat** ≈ 28% of calories; **carbs** = the remaining calories (never negative);
 *    **fiber** 14 g per 1000 kcal (Dietary Guidelines for Americans).
 * 7. **Water** — rule of thumb of half the body weight (lb) in fluid ounces, ÷8 for cups, plus
 *    2 cups on days with a swim.
 *
 * These are estimates for a healthy adult, not medical advice.
 */
object NutritionCalculator {

    private const val LB_TO_KG = 0.45359237
    private const val IN_TO_CM = 2.54
    private const val SEDENTARY_FACTOR = 1.2
    private const val KCAL_PER_LB_PER_WEEK_DAILY = 500.0
    private const val FAT_SHARE = 0.28
    private const val FIBER_PER_1000_KCAL = 14.0
    private const val PROTEIN_G_PER_LB_GOAL = 0.8
    private const val PROTEIN_MIN_G_PER_KG = 1.6

    fun targets(p: UserProfile): NutritionTargets {
        val kg = p.weightLb.coerceAtLeast(50.0) * LB_TO_KG
        val cm = p.heightIn.coerceAtLeast(36) * IN_TO_CM
        val age = p.age.coerceIn(13, 100)

        val bmr = bmr(kg, cm, age, p.sex)
        val swim = swimKcal(kg, p.swimMinutesPerDay, p.swimYardsPerDay)
        val tdee = bmr * SEDENTARY_FACTOR + swim

        val wantsLoss = p.goalWeightLb < p.weightLb
        val deficit = if (wantsLoss) p.pace.lbPerWeek * KCAL_PER_LB_PER_WEEK_DAILY else 0.0
        val floor = max(bmr, if (p.sex == Sex.MALE) 1500.0 else 1200.0)
        // Never let the floor push the target above maintenance (tiny/low-activity profiles).
        val raw = max(tdee - deficit, min(floor, tdee))
        val calories = roundTo10(raw)

        val goalLb = if (p.goalWeightLb > 0) p.goalWeightLb else p.weightLb
        val proteinG = max(goalLb * PROTEIN_G_PER_LB_GOAL, kg * PROTEIN_MIN_G_PER_KG).roundToInt()
        val fatG = (calories * FAT_SHARE / 9.0).roundToInt()
        val carbsG = ((calories - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)
        val fiberG = (calories / 1000.0 * FIBER_PER_1000_KCAL).roundToInt()

        val swims = p.swimMinutesPerDay > 0
        val waterCups = (p.weightLb / 2.0 / 8.0 + if (swims) 2.0 else 0.0).roundToInt()

        return NutritionTargets(
            bmr = bmr.roundToInt(),
            tdee = roundTo10(tdee),
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            fiberG = fiberG,
            waterCups = waterCups,
        )
    }

    /** Mifflin-St Jeor resting energy expenditure, kcal/day. */
    fun bmr(kg: Double, cm: Double, age: Int, sex: Sex): Double {
        val base = 10.0 * kg + 6.25 * cm - 5.0 * age
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    /** MET for swimming at the given pace (yards per minute), from the Compendium. */
    fun swimMet(yardsPerMinute: Double): Double = when {
        yardsPerMinute <= 0.0 -> 4.8 // time logged, no distance: assume leisurely
        yardsPerMinute < 25.0 -> 4.8 // leisurely / mixed strokes
        yardsPerMinute <= 35.0 -> 5.8 // freestyle, light-moderate effort
        else -> min(9.8, 5.8 + (yardsPerMinute - 35.0) / 20.0 * 4.0) // up to fast/vigorous
    }

    /** Net (above resting) kcal burned by the daily swim. */
    fun swimKcal(kg: Double, minutes: Int, yards: Int): Double {
        if (minutes <= 0) return 0.0
        val met = swimMet(yards.toDouble() / minutes)
        return (met - 1.0) * kg * (minutes / 60.0)
    }

    private fun roundTo10(v: Double): Int = (v / 10.0).roundToInt() * 10
}
