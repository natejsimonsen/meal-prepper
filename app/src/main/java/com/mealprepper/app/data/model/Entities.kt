package com.mealprepper.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable

@Serializable
enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snack")
}

/** Store sections, in the order you'd typically walk a grocery store. */
@Serializable
enum class Aisle(val label: String, val emoji: String) {
    PRODUCE("Produce", "🥬"),
    BAKERY("Bakery", "🥖"),
    MEAT_SEAFOOD("Meat & Seafood", "🥩"),
    DAIRY_EGGS("Dairy & Eggs", "🥚"),
    GRAINS("Grains & Pasta", "🌾"),
    CANNED("Canned & Jarred", "🥫"),
    PANTRY("Pantry", "🫙"),
    SPICES("Spices & Sauces", "🧂"),
    FROZEN("Frozen", "🧊"),
    BEVERAGES("Beverages", "🧃"),
    OTHER("Other", "🛒"),
}

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val mealType: MealType,
    val cuisine: String,
    val prepMinutes: Int,
    val cookMinutes: Int,
    val servings: Int,
    /** Nutrition is PER SERVING. */
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    /** One of [com.mealprepper.app.ui.components.FoodImageKey] names, lowercase. */
    val imageKey: String,
    /** "#RRGGBB" accent used behind the illustration. */
    val accentColor: String,
    val tags: List<String>,
    val steps: List<String>,
    /** 1 = easy, 2 = medium, 3 = involved. */
    val difficulty: Int,
    val mealPrepFriendly: Boolean,
    val fridgeDays: Int,
    val sourceName: String,
    val sourceUrl: String,
    val isFavorite: Boolean = false,
    val isUserCreated: Boolean = false,
    /** Absolute path to a user photo in app-private storage, overrides the illustration. */
    val userPhotoPath: String? = null,
) {
    val totalMinutes: Int get() = prepMinutes + cookMinutes
}

@Entity(
    tableName = "ingredients",
    foreignKeys = [ForeignKey(
        entity = RecipeEntity::class,
        parentColumns = ["id"],
        childColumns = ["recipeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("recipeId")],
)
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: String,
    /** Canonical lowercase singular name, e.g. "chicken breast". Used to merge shopping items. */
    val name: String,
    /** Quantity for the recipe's full [RecipeEntity.servings]. */
    val quantity: Double,
    /** One of [Units.ALL]. */
    val unit: String,
    val aisle: Aisle,
    /** Estimated USD cost of this quantity. */
    val estPriceUsd: Double,
    val note: String? = null,
    /** Salt, oil, etc. Hidden from the shopping list if in pantry. */
    val pantryStaple: Boolean = false,
    val position: Int = 0,
)

data class RecipeWithIngredients(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val ingredients: List<IngredientEntity>,
)

@Entity(tableName = "meal_plan", indices = [Index("epochDay"), Index("recipeId")])
data class MealPlanEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** java.time.LocalDate.toEpochDay() */
    val epochDay: Long,
    val slot: MealType,
    val recipeId: String,
    val servings: Int = 1,
    /** True when this meal is leftovers of a batch cooked earlier (no shopping/cooking needed). */
    val isLeftover: Boolean = false,
    val cooked: Boolean = false,
)

data class PlannedMeal(
    @Embedded val entry: MealPlanEntryEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity,
)

@Entity(tableName = "shopping_items")
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Double,
    val unit: String,
    val aisle: Aisle,
    val checked: Boolean = false,
    val estPriceUsd: Double = 0.0,
    /** Titles of recipes this item is for, for "why is this on my list?". */
    val forRecipes: List<String> = emptyList(),
    val isManual: Boolean = false,
    val note: String? = null,
)

@Entity(tableName = "pantry")
data class PantryItemEntity(
    @PrimaryKey val name: String,
)

/** One row per day: swim + weight + water tracking. */
@Entity(tableName = "daily_logs")
data class DailyLogEntity(
    @PrimaryKey val epochDay: Long,
    val swimYards: Int = 0,
    val swimMinutes: Int = 0,
    val weightLb: Double? = null,
    val waterCups: Int = 0,
    val note: String? = null,
)

object Units {
    val ALL = listOf(
        "g", "kg", "oz", "lb", "ml", "l", "tsp", "tbsp", "cup",
        "whole", "clove", "slice", "can", "bunch", "pinch", "pack", "jar",
    )
}
