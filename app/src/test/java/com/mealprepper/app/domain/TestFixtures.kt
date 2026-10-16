package com.mealprepper.app.domain

import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients

internal fun recipe(
    id: String,
    mealType: MealType,
    calories: Int = 550,
    protein: Int = 40,
    cuisine: String = "American",
    minutes: Int = 15,
    servings: Int = 2,
    mealPrep: Boolean = false,
    tags: List<String> = emptyList(),
    title: String = id,
    ingredients: List<Triple<String, Double, String>> = emptyList(),
    aisle: Aisle = Aisle.MEAT_SEAFOOD,
    price: Double = 3.0,
): RecipeWithIngredients {
    val r = RecipeEntity(
        id = id, title = title, subtitle = "", description = "", mealType = mealType,
        cuisine = cuisine, prepMinutes = minutes / 2, cookMinutes = minutes - minutes / 2,
        servings = servings, calories = calories, proteinG = protein, carbsG = 40, fatG = 15,
        fiberG = 5, imageKey = "bowl", accentColor = "#000000", tags = tags, steps = emptyList(),
        difficulty = 1, mealPrepFriendly = mealPrep, fridgeDays = if (mealPrep) 4 else 1,
        sourceName = "", sourceUrl = "",
    )
    val ings = ingredients.mapIndexed { i, (name, qty, unit) ->
        IngredientEntity(
            id = i.toLong() + 1, recipeId = id, name = name, quantity = qty, unit = unit,
            aisle = aisle, estPriceUsd = price, position = i,
        )
    }
    return RecipeWithIngredients(r, ings)
}

/** A realistic-ish library: 8 breakfasts, 8 lunches, 12 dinners, 5 snacks with rotating proteins. */
internal fun library(): List<RecipeWithIngredients> {
    val proteins = listOf("chicken breast", "salmon", "ground turkey", "lean beef", "shrimp", "tofu", "pork loin", "black bean")
    val cuisines = listOf("Mexican", "Mediterranean", "Asian", "American", "Italian", "Indian")
    val out = ArrayList<RecipeWithIngredients>()
    for (i in 0 until 8) out += recipe(
        "b$i", MealType.BREAKFAST, calories = 450 + i * 10, protein = 30 + i, cuisine = cuisines[i % 6],
        minutes = 10, servings = 1, ingredients = listOf(Triple("egg", 3.0, "whole")),
        aisle = Aisle.DAIRY_EGGS, price = 1.2,
    )
    for (i in 0 until 8) out += recipe(
        "l$i", MealType.LUNCH, calories = 580 + i * 10, protein = 40 + i, cuisine = cuisines[(i + 1) % 6],
        minutes = 15, ingredients = listOf(Triple(proteins[i % 8], 0.75, "lb")), price = 6.0,
    )
    for (i in 0 until 12) out += recipe(
        "d$i", MealType.DINNER, calories = 620 + (i % 4) * 20, protein = 45 + i % 5,
        cuisine = cuisines[(i + 2) % 6], minutes = if (i % 3 == 0) 40 else 20, servings = 4,
        mealPrep = i % 2 == 0, tags = if (i == 5) listOf("spicy") else emptyList(),
        ingredients = listOf(Triple(proteins[(i + 3) % 8], 1.5, "lb")), price = 10.0,
    )
    for (i in 0 until 5) out += recipe(
        "s$i", MealType.SNACK, calories = 220, protein = 20, minutes = 2, servings = 1,
        ingredients = listOf(Triple("greek yogurt", 1.0, "cup")), aisle = Aisle.DAIRY_EGGS, price = 1.5,
    )
    return out
}
