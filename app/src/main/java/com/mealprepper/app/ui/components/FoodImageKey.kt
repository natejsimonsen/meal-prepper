package com.mealprepper.app.ui.components

import androidx.annotation.DrawableRes
import com.mealprepper.app.R

/**
 * Offline food illustrations (vector drawables in res/drawable/food_*.xml).
 * Recipe.imageKey must be one of these names in lowercase.
 */
enum class FoodImageKey(@DrawableRes val res: Int) {
    BOWL_GRAIN(R.drawable.food_bowl_grain),
    SALMON(R.drawable.food_salmon),
    CHICKEN(R.drawable.food_chicken),
    STEAK(R.drawable.food_steak),
    EGGS(R.drawable.food_eggs),
    OATS(R.drawable.food_oats),
    SMOOTHIE(R.drawable.food_smoothie),
    PARFAIT(R.drawable.food_parfait),
    TACOS(R.drawable.food_tacos),
    STIR_FRY(R.drawable.food_stir_fry),
    SALAD(R.drawable.food_salad),
    PASTA(R.drawable.food_pasta),
    SOUP(R.drawable.food_soup),
    WRAP(R.drawable.food_wrap),
    SHRIMP(R.drawable.food_shrimp),
    TOFU(R.drawable.food_tofu),
    SANDWICH(R.drawable.food_sandwich),
    SHEET_PAN(R.drawable.food_sheet_pan),
    CHILI(R.drawable.food_chili),
    TOAST(R.drawable.food_toast),
    CURRY(R.drawable.food_curry),
    BURGER(R.drawable.food_burger),
    POKE(R.drawable.food_poke),
    SNACK_BOX(R.drawable.food_snack_box);

    companion object {
        fun from(key: String): FoodImageKey =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: BOWL_GRAIN
    }
}
