package com.mealprepper.app.data.seed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient JSON config for the bundled seed file. */
internal val SeedJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/** Root of `assets/recipes.json`. See docs/ARCHITECTURE.md for the schema. */
@Serializable
data class SeedFile(
    val version: Int,
    val sources: List<SeedSource> = emptyList(),
    val recipes: List<SeedRecipe> = emptyList(),
)

@Serializable
data class SeedSource(
    val name: String,
    val url: String = "",
    val note: String? = null,
)

/**
 * One seed recipe. Nutrition is per serving; ingredient quantities/prices are for the whole recipe.
 * Nutrition numbers are decoded as Double so both `520` and `520.0` are accepted, then rounded.
 * `mealType` is kept as a String and mapped leniently (unknown values skip the recipe).
 */
@Serializable
data class SeedRecipe(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val description: String = "",
    val mealType: String,
    val cuisine: String = "",
    val prepMinutes: Int = 0,
    val cookMinutes: Int = 0,
    val servings: Int = 1,
    val calories: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val fiberG: Double = 0.0,
    val imageKey: String = "bowl",
    val accentColor: String = "#F29E4C",
    val tags: List<String> = emptyList(),
    val difficulty: Int = 1,
    val mealPrepFriendly: Boolean = false,
    val fridgeDays: Int = 3,
    val sourceName: String = "",
    val sourceUrl: String = "",
    val ingredients: List<SeedIngredient> = emptyList(),
    val steps: List<String> = emptyList(),
)

@Serializable
data class SeedIngredient(
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: String = "OTHER",
    val estPriceUsd: Double = 0.0,
    val note: String? = null,
    val pantryStaple: Boolean = false,
)
