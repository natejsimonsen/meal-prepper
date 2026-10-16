package com.mealprepper.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealPlanEntryEntity
import com.mealprepper.app.data.model.PantryItemEntity
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.ShoppingItemEntity

/** The single on-device SQLite database. Nothing here ever leaves the phone. */
@Database(
    entities = [
        RecipeEntity::class,
        IngredientEntity::class,
        MealPlanEntryEntity::class,
        ShoppingItemEntity::class,
        PantryItemEntity::class,
        DailyLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao
    abstract fun planDao(): PlanDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun pantryDao(): PantryDao
    abstract fun logDao(): LogDao

    companion object {
        const val NAME: String = "meal_prepper.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // v1: no migrations yet. Add real Migrations before shipping a schema change.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
