package com.mealprepper.app.data.seed

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.mealprepper.app.data.db.AppDatabase
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.PantryItemEntity
import com.mealprepper.app.data.model.RecipeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * Loads the bundled recipe library (`assets/recipes.json`) into Room.
 *
 * Re-seeds only when the JSON `version` differs from the one stored in SharedPreferences
 * ("seed" / "version"). On re-seed, built-in recipes are upserted (keeping the user's
 * `isFavorite` and `userPhotoPath`), their ingredients replaced, and built-ins that disappeared
 * from the JSON are removed (with their plan entries). User-created recipes are never touched.
 * Never throws: a missing or malformed asset is logged and ignored.
 */
class SeedLoader(context: Context, private val db: AppDatabase) {

    private val appContext: Context = context.applicationContext

    suspend fun seedIfNeeded() {
        try {
            withContext(Dispatchers.IO) { seedInternal() }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Seeding failed; continuing without seed data", t)
        }
    }

    private suspend fun seedInternal() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedVersion = prefs.getInt(KEY_VERSION, NO_VERSION)

        val text = try {
            appContext.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Seed asset $ASSET_NAME missing or unreadable", e)
            return
        }
        val seed = try {
            SeedJson.decodeFromString(SeedFile.serializer(), text)
        } catch (e: Exception) {
            Log.e(TAG, "Seed asset $ASSET_NAME is malformed", e)
            return
        }
        if (seed.version == storedVersion) return

        val recipeDao = db.recipeDao()
        val seededCount: Int = db.withTransaction {
            val existing = recipeDao.getAll().associateBy { it.id }
            val seenIds = HashSet<String>()
            for (dto in seed.recipes) {
                val mealType = parseMealType(dto.mealType)
                if (mealType == null || dto.id.isBlank() || !seenIds.add(dto.id)) {
                    Log.w(TAG, "Skipping invalid/duplicate seed recipe '${dto.id}'")
                    continue
                }
                val old = existing[dto.id]
                if (old != null && old.isUserCreated) continue // never clobber user data
                val entity = dto.toEntity(
                    mealType = mealType,
                    isFavorite = old?.isFavorite ?: false,
                    userPhotoPath = old?.userPhotoPath,
                )
                recipeDao.upsert(entity)
                recipeDao.deleteIngredientsFor(entity.id)
                recipeDao.insertIngredients(
                    dto.ingredients.mapIndexed { index, ing -> ing.toEntity(entity.id, index) }
                )
            }
            val removed = existing.values
                .filter { !it.isUserCreated && it.id !in seenIds }
                .map { it.id }
            if (removed.isNotEmpty()) {
                removed.chunked(500).forEach { recipeDao.deleteByIds(it) }
            }
            db.planDao().deleteOrphans()

            if (storedVersion == NO_VERSION) {
                db.pantryDao().insertAll(DEFAULT_PANTRY.map { PantryItemEntity(it) })
            }
            seenIds.size
        }
        prefs.edit().putInt(KEY_VERSION, seed.version).apply()
        Log.i(TAG, "Seeded $seededCount recipes (version ${seed.version})")
    }

    private fun parseMealType(raw: String): MealType? =
        MealType.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

    private fun SeedRecipe.toEntity(
        mealType: MealType,
        isFavorite: Boolean,
        userPhotoPath: String?,
    ): RecipeEntity = RecipeEntity(
        id = id,
        title = title,
        subtitle = subtitle,
        description = description,
        mealType = mealType,
        cuisine = cuisine,
        prepMinutes = prepMinutes.coerceAtLeast(0),
        cookMinutes = cookMinutes.coerceAtLeast(0),
        servings = servings.coerceAtLeast(1),
        calories = calories.roundToInt(),
        proteinG = proteinG.roundToInt(),
        carbsG = carbsG.roundToInt(),
        fatG = fatG.roundToInt(),
        fiberG = fiberG.roundToInt(),
        imageKey = imageKey.lowercase(),
        accentColor = accentColor,
        tags = tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
        steps = steps,
        difficulty = difficulty.coerceIn(1, 3),
        mealPrepFriendly = mealPrepFriendly,
        fridgeDays = fridgeDays.coerceAtLeast(0),
        sourceName = sourceName,
        sourceUrl = sourceUrl,
        isFavorite = isFavorite,
        isUserCreated = false,
        userPhotoPath = userPhotoPath,
    )

    private fun SeedIngredient.toEntity(recipeId: String, position: Int): IngredientEntity =
        IngredientEntity(
            recipeId = recipeId,
            name = name.trim().lowercase(),
            quantity = quantity,
            unit = unit.trim().lowercase(),
            aisle = Aisle.entries.firstOrNull { it.name.equals(aisle.trim(), ignoreCase = true) }
                ?: Aisle.OTHER,
            estPriceUsd = estPriceUsd,
            note = note,
            pantryStaple = pantryStaple,
            position = position,
        )

    companion object {
        private const val TAG = "SeedLoader"
        private const val ASSET_NAME = "recipes.json"
        private const val PREFS_NAME = "seed"
        private const val KEY_VERSION = "version"
        private const val NO_VERSION = Int.MIN_VALUE

        /** Pre-filled on first launch; names match the canonical ingredient names in the seed. */
        val DEFAULT_PANTRY: List<String> = listOf(
            "olive oil",
            "extra virgin olive oil",
            "avocado oil",
            "cooking spray",
            "kosher salt",
            "salt",
            "sea salt",
            "black pepper",
            "garlic powder",
            "onion powder",
            "paprika",
            "smoked paprika",
            "ground cumin",
            "chili powder",
            "dried oregano",
            "italian seasoning",
            "red pepper flakes",
            "ground cinnamon",
            "soy sauce",
        )
    }
}
