package com.mealprepper.app.data.repo

import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.PantryItemEntity
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class RecipeFilter(
    val query: String = "",
    val mealType: MealType? = null,
    val maxTotalMinutes: Int? = null,
    val tags: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val minProteinG: Int? = null,
)

/** What to merge into the one shopping list. */
data class ListBuildRequest(
    /** Include every planned (non-leftover, non-cooked) meal in this date range, inclusive. */
    val planStart: LocalDate? = null,
    val planEnd: LocalDate? = null,
    /** Extra recipes with the number of servings wanted. */
    val extraRecipes: Map<String, Int> = emptyMap(),
    /** Skip ingredients whose name is in the pantry. */
    val skipPantry: Boolean = true,
    /** Keep manually-added items currently on the list. */
    val keepManualItems: Boolean = true,
)

data class ShoppingSummary(
    val totalItems: Int,
    val checkedItems: Int,
    val estTotalUsd: Double,
    val budgetUsd: Int,
)

interface MealRepository {
    // Recipes
    fun recipes(filter: RecipeFilter = RecipeFilter()): Flow<List<RecipeEntity>>
    fun recipe(id: String): Flow<RecipeWithIngredients?>
    fun allTags(): Flow<List<String>>
    suspend fun toggleFavorite(id: String)
    /** Inserts or replaces a user recipe and its ingredients. Returns the recipe id. */
    suspend fun saveUserRecipe(recipe: RecipeEntity, ingredients: List<IngredientEntity>): String
    suspend fun deleteUserRecipe(id: String)
    suspend fun setRecipePhoto(id: String, path: String?)
    /** Quick dinner picks for tonight: low effort, high protein, sorted by total time. */
    fun tonightPicks(limit: Int = 6): Flow<List<RecipeEntity>>

    // Plan
    fun plan(start: LocalDate, endInclusive: LocalDate): Flow<List<PlannedMeal>>
    suspend fun addToPlan(date: LocalDate, slot: MealType, recipeId: String, servings: Int = 1)
    suspend fun removeFromPlan(entryId: Long)
    suspend fun setCooked(entryId: Long, cooked: Boolean)
    /** Replaces the plan for [days] days starting at [start] with a varied, protein-forward plan. */
    suspend fun generatePlan(start: LocalDate, days: Int, profile: UserProfile, seed: Long = System.currentTimeMillis())
    suspend fun swapMeal(entryId: Long, profile: UserProfile)

    // Shopping list (one list)
    fun shoppingList(): Flow<List<ShoppingItemEntity>>
    fun shoppingSummary(budgetUsd: Int): Flow<ShoppingSummary>
    suspend fun buildShoppingList(request: ListBuildRequest)
    suspend fun addRecipeToList(recipeId: String, servings: Int)
    suspend fun addManualItem(name: String, quantity: Double = 1.0, unit: String = "whole", aisle: Aisle = Aisle.OTHER)
    suspend fun setChecked(itemId: Long, checked: Boolean)
    suspend fun deleteItem(itemId: Long)
    suspend fun clearChecked()
    suspend fun clearList()
    /** Plain text of the list grouped by aisle, for sharing. */
    suspend fun shoppingListAsText(): String

    // Pantry
    fun pantry(): Flow<List<PantryItemEntity>>
    suspend fun addPantry(name: String)
    suspend fun removePantry(name: String)

    // Logs
    fun logs(start: LocalDate, endInclusive: LocalDate): Flow<List<DailyLogEntity>>
    fun log(date: LocalDate): Flow<DailyLogEntity?>
    suspend fun upsertLog(log: DailyLogEntity)
}

interface ProfileRepository {
    val profile: Flow<UserProfile>
    suspend fun update(transform: (UserProfile) -> UserProfile)
}
