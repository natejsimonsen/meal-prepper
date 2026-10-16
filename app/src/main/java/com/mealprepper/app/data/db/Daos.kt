package com.mealprepper.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealPlanEntryEntity
import com.mealprepper.app.data.model.PantryItemEntity
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.model.ShoppingItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {

    /**
     * SQL-side filtering. [mealType] is the enum *name* (or null for any) so no converter is
     * needed for the nullable parameter. Tags are filtered in Kotlin by the repository.
     */
    @Query(
        """
        SELECT * FROM recipes
        WHERE (:query = '' OR title LIKE '%' || :query || '%'
               OR subtitle LIKE '%' || :query || '%'
               OR cuisine LIKE '%' || :query || '%')
          AND (:mealType IS NULL OR mealType = :mealType)
          AND (:maxMinutes IS NULL OR (prepMinutes + cookMinutes) <= :maxMinutes)
          AND (:favoritesOnly = 0 OR isFavorite = 1)
          AND (:minProteinG IS NULL OR proteinG >= :minProteinG)
        ORDER BY isFavorite DESC, title COLLATE NOCASE ASC
        """
    )
    fun observeFiltered(
        query: String,
        mealType: String?,
        maxMinutes: Int?,
        favoritesOnly: Boolean,
        minProteinG: Int?,
    ): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes WHERE (prepMinutes + cookMinutes) <= :maxMinutes")
    fun observeQuick(maxMinutes: Int): Flow<List<RecipeEntity>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeWithIngredients(id: String): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipes")
    suspend fun getAllWithIngredients(): List<RecipeWithIngredients>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id IN (:ids)")
    suspend fun getWithIngredients(ids: List<String>): List<RecipeWithIngredients>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipes")
    suspend fun getAll(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE isUserCreated = 0")
    suspend fun getBuiltIn(): List<RecipeEntity>

    /** Upsert (UPDATE-or-INSERT) — unlike REPLACE it never deletes the row, so no cascade. */
    @Upsert
    suspend fun upsert(recipe: RecipeEntity)

    @Upsert
    suspend fun upsertAll(recipes: List<RecipeEntity>)

    @Insert
    suspend fun insertIngredients(ingredients: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredientsFor(recipeId: String)

    @Query("DELETE FROM ingredients WHERE recipeId IN (:recipeIds)")
    suspend fun deleteIngredientsForAll(recipeIds: List<String>)

    @Query("UPDATE recipes SET isFavorite = NOT isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE recipes SET userPhotoPath = :path WHERE id = :id")
    suspend fun setPhoto(id: String, path: String?)

    @Query("DELETE FROM recipes WHERE id = :id AND isUserCreated = 1")
    suspend fun deleteUserRecipe(id: String): Int

    @Query("DELETE FROM recipes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM recipes")
    suspend fun count(): Int
}

@Dao
interface PlanDao {

    @Transaction
    @Query(
        """
        SELECT * FROM meal_plan
        WHERE epochDay BETWEEN :startDay AND :endDay
          AND recipeId IN (SELECT id FROM recipes)
        ORDER BY epochDay ASC,
          CASE slot WHEN 'BREAKFAST' THEN 0 WHEN 'LUNCH' THEN 1 WHEN 'DINNER' THEN 2 ELSE 3 END ASC,
          id ASC
        """
    )
    fun observePlan(startDay: Long, endDay: Long): Flow<List<PlannedMeal>>

    @Query("SELECT * FROM meal_plan WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC, id ASC")
    suspend fun getEntries(startDay: Long, endDay: Long): List<MealPlanEntryEntity>

    /** Planned meals that still need cooking/shopping: not leftovers, not already cooked. */
    @Query(
        """
        SELECT * FROM meal_plan
        WHERE epochDay BETWEEN :startDay AND :endDay AND isLeftover = 0 AND cooked = 0
        ORDER BY epochDay ASC, id ASC
        """
    )
    suspend fun getCookable(startDay: Long, endDay: Long): List<MealPlanEntryEntity>

    @Query("SELECT * FROM meal_plan WHERE id = :id")
    suspend fun getEntry(id: Long): MealPlanEntryEntity?

    @Insert
    suspend fun insert(entry: MealPlanEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<MealPlanEntryEntity>)

    @Update
    suspend fun update(entry: MealPlanEntryEntity)

    @Query("DELETE FROM meal_plan WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM meal_plan WHERE epochDay BETWEEN :startDay AND :endDay")
    suspend fun deleteRange(startDay: Long, endDay: Long)

    @Query("UPDATE meal_plan SET cooked = :cooked WHERE id = :id")
    suspend fun setCooked(id: Long, cooked: Boolean)

    @Query("DELETE FROM meal_plan WHERE recipeId = :recipeId")
    suspend fun deleteForRecipe(recipeId: String)

    /** Removes entries whose recipe no longer exists (keeps the non-null @Relation safe). */
    @Query("DELETE FROM meal_plan WHERE recipeId NOT IN (SELECT id FROM recipes)")
    suspend fun deleteOrphans()
}

@Dao
interface ShoppingDao {

    @Query("SELECT * FROM shopping_items ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ShoppingItemEntity>>

    @Query("SELECT * FROM shopping_items ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<ShoppingItemEntity>

    @Insert
    suspend fun insert(item: ShoppingItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ShoppingItemEntity>)

    @Update
    suspend fun updateAll(items: List<ShoppingItemEntity>)

    @Query("UPDATE shopping_items SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM shopping_items WHERE checked = 1")
    suspend fun deleteChecked()

    @Query("DELETE FROM shopping_items")
    suspend fun deleteAll()

    @Query("DELETE FROM shopping_items WHERE isManual = 0")
    suspend fun deleteGenerated()
}

@Dao
interface PantryDao {

    @Query("SELECT * FROM pantry ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PantryItemEntity>>

    @Query("SELECT * FROM pantry")
    suspend fun getAll(): List<PantryItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: PantryItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PantryItemEntity>)

    @Query("DELETE FROM pantry WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface LogDao {

    @Query("SELECT * FROM daily_logs WHERE epochDay BETWEEN :startDay AND :endDay ORDER BY epochDay ASC")
    fun observeRange(startDay: Long, endDay: Long): Flow<List<DailyLogEntity>>

    @Query("SELECT * FROM daily_logs WHERE epochDay = :epochDay")
    fun observe(epochDay: Long): Flow<DailyLogEntity?>

    @Upsert
    suspend fun upsert(log: DailyLogEntity)
}
