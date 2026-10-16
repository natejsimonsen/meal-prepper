package com.mealprepper.app.data.repo

import androidx.room.withTransaction
import com.mealprepper.app.data.db.AppDatabase
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.DailyLogEntity
import com.mealprepper.app.data.model.IngredientEntity
import com.mealprepper.app.data.model.MealPlanEntryEntity
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.PantryItemEntity
import com.mealprepper.app.data.model.PlannedMeal
import com.mealprepper.app.data.model.RecipeEntity
import com.mealprepper.app.data.model.RecipeWithIngredients
import com.mealprepper.app.data.model.ShoppingItemEntity
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.domain.CookRequest
import com.mealprepper.app.domain.NutritionCalculator
import com.mealprepper.app.domain.PlanGenerator
import com.mealprepper.app.domain.ShoppingListBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** [MealRepository] backed entirely by the on-device Room database. */
class OfflineMealRepository(private val db: AppDatabase) : MealRepository {

    private val recipeDao = db.recipeDao()
    private val planDao = db.planDao()
    private val shoppingDao = db.shoppingDao()
    private val pantryDao = db.pantryDao()
    private val logDao = db.logDao()
    private val planGenerator = PlanGenerator()

    // ------------------------------------------------------------------ Recipes

    override fun recipes(filter: RecipeFilter): Flow<List<RecipeEntity>> {
        val wantedTags = filter.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        return recipeDao.observeFiltered(
            query = filter.query.trim(),
            mealType = filter.mealType?.name,
            maxMinutes = filter.maxTotalMinutes,
            favoritesOnly = filter.favoritesOnly,
            minProteinG = filter.minProteinG,
        ).map { list ->
            if (wantedTags.isEmpty()) list
            else list.filter { r ->
                val tags = r.tags.map { it.lowercase() }.toSet()
                tags.containsAll(wantedTags)
            }
        }.distinctUntilChanged()
    }

    override fun recipe(id: String): Flow<RecipeWithIngredients?> =
        recipeDao.observeWithIngredients(id).map { rw ->
            rw?.copy(ingredients = rw.ingredients.sortedBy { it.position })
        }

    override fun allTags(): Flow<List<String>> =
        recipeDao.observeAll().map { list ->
            list.flatMap { it.tags }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }.distinctUntilChanged()

    override suspend fun toggleFavorite(id: String) = recipeDao.toggleFavorite(id)

    override suspend fun saveUserRecipe(recipe: RecipeEntity, ingredients: List<IngredientEntity>): String {
        val id = recipe.id.ifBlank { "user-${UUID.randomUUID()}" }
        db.withTransaction {
            val existing = recipeDao.getById(id)
            val toSave = recipe.copy(
                id = id,
                isUserCreated = true,
                servings = recipe.servings.coerceAtLeast(1),
                tags = recipe.tags.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
                userPhotoPath = recipe.userPhotoPath ?: existing?.userPhotoPath,
            )
            recipeDao.upsert(toSave)
            recipeDao.deleteIngredientsFor(id)
            recipeDao.insertIngredients(
                ingredients
                    .filter { it.name.isNotBlank() }
                    .sortedBy { it.position } // stable: keeps caller order for equal positions
                    .mapIndexed { index, ing ->
                        ing.copy(
                            id = 0,
                            recipeId = id,
                            name = ShoppingListBuilder.normalizeName(ing.name),
                            unit = ShoppingListBuilder.normalizeUnit(ing.unit),
                            position = index,
                        )
                    }
            )
        }
        return id
    }

    override suspend fun deleteUserRecipe(id: String) {
        var photo: String? = null
        db.withTransaction {
            val existing = recipeDao.getById(id) ?: return@withTransaction
            if (!existing.isUserCreated) return@withTransaction
            photo = existing.userPhotoPath
            planDao.deleteForRecipe(id)
            recipeDao.deleteUserRecipe(id) // ingredients cascade
            Unit
        }
        photo?.let { path -> runCatching { File(path).delete() } }
    }

    override suspend fun setRecipePhoto(id: String, path: String?) = recipeDao.setPhoto(id, path)

    override fun tonightPicks(limit: Int): Flow<List<RecipeEntity>> =
        recipeDao.observeAll().map { all ->
            val tonightOrder = compareByDescending<RecipeEntity> { it.hasTag(TONIGHT_TAG) }
                .thenBy { it.prepMinutes + it.cookMinutes }
                .thenByDescending { it.proteinG }
            val strict = all.filter {
                (it.mealType == MealType.DINNER || it.hasTag(TONIGHT_TAG)) &&
                    it.prepMinutes + it.cookMinutes <= TONIGHT_MAX_MINUTES
            }.sortedWith(tonightOrder)
            // Never show an empty "tonight" screen: fall back to the quickest dinners.
            val picks = strict.ifEmpty {
                all.filter { it.mealType == MealType.DINNER }.sortedWith(tonightOrder)
            }
            picks.take(limit.coerceAtLeast(0))
        }.distinctUntilChanged()

    // ------------------------------------------------------------------ Plan

    override fun plan(start: LocalDate, endInclusive: LocalDate): Flow<List<PlannedMeal>> =
        planDao.observePlan(start.toEpochDay(), endInclusive.toEpochDay())

    override suspend fun addToPlan(date: LocalDate, slot: MealType, recipeId: String, servings: Int) {
        planDao.insert(
            MealPlanEntryEntity(
                epochDay = date.toEpochDay(),
                slot = slot,
                recipeId = recipeId,
                servings = servings.coerceAtLeast(1),
            )
        )
    }

    override suspend fun removeFromPlan(entryId: Long) = planDao.deleteById(entryId)

    override suspend fun setCooked(entryId: Long, cooked: Boolean) = planDao.setCooked(entryId, cooked)

    override suspend fun generatePlan(start: LocalDate, days: Int, profile: UserProfile, seed: Long) {
        if (days <= 0) return
        val recipes = recipeDao.getAllWithIngredients()
        val targets = NutritionCalculator.targets(profile)
        val slots = planGenerator.generate(
            recipes = recipes,
            targets = targets,
            profile = profile,
            days = days,
            seed = seed,
            startDayOfWeek = start.dayOfWeek,
        )
        val startDay = start.toEpochDay()
        val entries = slots.map {
            MealPlanEntryEntity(
                epochDay = startDay + it.dayOffset,
                slot = it.slot,
                recipeId = it.recipeId,
                servings = it.servings,
                isLeftover = it.isLeftover,
            )
        }
        db.withTransaction {
            planDao.deleteRange(startDay, startDay + days - 1)
            if (entries.isNotEmpty()) planDao.insertAll(entries)
        }
    }

    override suspend fun swapMeal(entryId: Long, profile: UserProfile) {
        db.withTransaction {
            val entry = planDao.getEntry(entryId) ?: return@withTransaction
            val recipes = recipeDao.getAllWithIngredients()
            val current = recipes.firstOrNull { it.recipe.id == entry.recipeId }?.recipe
            val nearby = planDao.getEntries(entry.epochDay - 3, entry.epochDay + 3)
            val avoid = nearby.map { it.recipeId }.toSet()
            val replacement = planGenerator.pickReplacement(
                recipes = recipes,
                slot = entry.slot,
                current = current,
                avoidRecipeIds = avoid,
                profile = profile,
                seed = System.nanoTime() xor entryId,
            ) ?: return@withTransaction

            val prepFriendly = replacement.mealPrepFriendly && replacement.servings >= 2 && replacement.fridgeDays >= 1

            if (entry.isLeftover) {
                // Swapping a leftover: the source meal no longer needs the extra serving.
                nearby.firstOrNull {
                    it.epochDay == entry.epochDay - 1 && !it.isLeftover && it.recipeId == entry.recipeId
                }?.let { src -> planDao.update(src.copy(servings = (src.servings - 1).coerceAtLeast(1))) }
                planDao.update(entry.copy(recipeId = replacement.id, isLeftover = false, servings = 1, cooked = false))
                return@withTransaction
            }

            val leftoverChild = nearby.firstOrNull {
                it.epochDay == entry.epochDay + 1 && it.isLeftover && it.recipeId == entry.recipeId
            }
            if (leftoverChild == null) {
                planDao.update(entry.copy(recipeId = replacement.id, cooked = false))
            } else if (prepFriendly) {
                planDao.update(entry.copy(recipeId = replacement.id, cooked = false))
                planDao.update(leftoverChild.copy(recipeId = replacement.id))
            } else {
                // New meal doesn't keep: cook 1 serving and give tomorrow a fresh pick.
                planDao.update(entry.copy(recipeId = replacement.id, servings = 1, cooked = false))
                val childReplacement = planGenerator.pickReplacement(
                    recipes = recipes,
                    slot = leftoverChild.slot,
                    current = current,
                    avoidRecipeIds = avoid + replacement.id,
                    profile = profile,
                    seed = System.nanoTime() xor leftoverChild.id,
                )
                if (childReplacement == null) {
                    planDao.deleteById(leftoverChild.id)
                } else {
                    planDao.update(
                        leftoverChild.copy(recipeId = childReplacement.id, isLeftover = false, servings = 1)
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ Shopping

    override fun shoppingList(): Flow<List<ShoppingItemEntity>> =
        shoppingDao.observeAll().map { it.sortedWith(ShoppingListBuilder.itemOrder) }

    override fun shoppingSummary(budgetUsd: Int): Flow<ShoppingSummary> =
        shoppingDao.observeAll().map { items ->
            ShoppingSummary(
                totalItems = items.size,
                checkedItems = items.count { it.checked },
                estTotalUsd = items.sumOf { it.estPriceUsd },
                budgetUsd = budgetUsd,
            )
        }.distinctUntilChanged()

    override suspend fun buildShoppingList(request: ListBuildRequest) {
        db.withTransaction {
            val servingsByRecipe = LinkedHashMap<String, Int>()
            if (request.planStart != null && request.planEnd != null) {
                val a = request.planStart.toEpochDay()
                val b = request.planEnd.toEpochDay()
                val start = minOf(a, b)
                val end = maxOf(a, b)
                // Only cooked batches: leftovers & already-cooked meals are excluded in SQL.
                for (e in planDao.getCookable(start, end)) {
                    servingsByRecipe[e.recipeId] = (servingsByRecipe[e.recipeId] ?: 0) + e.servings.coerceAtLeast(1)
                }
            }
            for ((id, servings) in request.extraRecipes) {
                if (servings > 0) servingsByRecipe[id] = (servingsByRecipe[id] ?: 0) + servings
            }
            val items = buildItems(servingsByRecipe, request.skipPantry)
            if (request.keepManualItems) shoppingDao.deleteGenerated() else shoppingDao.deleteAll()
            if (items.isNotEmpty()) shoppingDao.insertAll(items)
        }
    }

    override suspend fun addRecipeToList(recipeId: String, servings: Int) {
        if (servings <= 0) return
        db.withTransaction {
            val additions = buildItems(mapOf(recipeId to servings), skipPantry = true)
            if (additions.isEmpty()) return@withTransaction
            val existing = shoppingDao.getAll()
            val merged = ShoppingListBuilder.merge(existing, additions)
            val existingById = existing.associateBy { it.id }
            val toUpdate = merged.filter { it.id != 0L && existingById[it.id] != it }
            val toInsert = merged.filter { it.id == 0L }
            if (toUpdate.isNotEmpty()) shoppingDao.updateAll(toUpdate)
            if (toInsert.isNotEmpty()) shoppingDao.insertAll(toInsert)
        }
    }

    override suspend fun addManualItem(name: String, quantity: Double, unit: String, aisle: Aisle) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        shoppingDao.insert(
            ShoppingItemEntity(
                name = clean,
                quantity = if (quantity.isFinite() && quantity > 0) quantity else 1.0,
                unit = unit.trim().ifEmpty { "whole" },
                aisle = aisle,
                isManual = true,
            )
        )
    }

    override suspend fun setChecked(itemId: Long, checked: Boolean) = shoppingDao.setChecked(itemId, checked)

    override suspend fun deleteItem(itemId: Long) = shoppingDao.deleteById(itemId)

    override suspend fun clearChecked() = shoppingDao.deleteChecked()

    override suspend fun clearList() = shoppingDao.deleteAll()

    override suspend fun shoppingListAsText(): String {
        val items = shoppingDao.getAll().sortedWith(ShoppingListBuilder.itemOrder)
        if (items.isEmpty()) return "🛒 Shopping list is empty"
        val sb = StringBuilder("🛒 Shopping list\n")
        for ((aisle, group) in items.groupBy { it.aisle }.toSortedMap(compareBy<Aisle> { it.ordinal })) {
            sb.append('\n').append(aisle.emoji).append(' ').append(aisle.label).append('\n')
            for (item in group) {
                sb.append(if (item.checked) "☑ " else "☐ ")
                    .append(formatQty(item.quantity)).append(' ')
                    .append(item.unit).append(' ')
                    .append(item.name)
                item.note?.takeIf { it.isNotBlank() }?.let { sb.append(" (").append(it).append(')') }
                sb.append('\n')
            }
        }
        val total = items.sumOf { it.estPriceUsd }
        sb.append('\n').append("Estimated total: ").append(String.format(Locale.US, "$%.2f", total))
        return sb.toString()
    }

    private suspend fun buildItems(servingsByRecipe: Map<String, Int>, skipPantry: Boolean): List<ShoppingItemEntity> {
        if (servingsByRecipe.isEmpty()) return emptyList()
        val recipes = servingsByRecipe.keys.toList().chunked(500)
            .flatMap { recipeDao.getWithIngredients(it) }
            .associateBy { it.recipe.id }
        val requests = servingsByRecipe.mapNotNull { (id, servings) ->
            recipes[id]?.let { CookRequest(it, servings) }
        }
        val pantry = if (skipPantry) pantryDao.getAll().map { it.name }.toSet() else emptySet()
        return ShoppingListBuilder.build(requests, pantry, skipPantry)
    }

    // ------------------------------------------------------------------ Pantry

    override fun pantry(): Flow<List<PantryItemEntity>> = pantryDao.observeAll()

    override suspend fun addPantry(name: String) {
        val clean = ShoppingListBuilder.normalizeName(name)
        if (clean.isNotEmpty()) pantryDao.insert(PantryItemEntity(clean))
    }

    override suspend fun removePantry(name: String) {
        pantryDao.delete(name)
        val normalized = ShoppingListBuilder.normalizeName(name)
        if (normalized != name) pantryDao.delete(normalized)
    }

    // ------------------------------------------------------------------ Logs

    override fun logs(start: LocalDate, endInclusive: LocalDate): Flow<List<DailyLogEntity>> =
        logDao.observeRange(start.toEpochDay(), endInclusive.toEpochDay())

    override fun log(date: LocalDate): Flow<DailyLogEntity?> = logDao.observe(date.toEpochDay())

    override suspend fun upsertLog(log: DailyLogEntity) = logDao.upsert(log)

    // ------------------------------------------------------------------ helpers

    private fun RecipeEntity.hasTag(tag: String): Boolean = tags.any { it.equals(tag, ignoreCase = true) }

    private fun formatQty(q: Double): String {
        val whole = q.toLong()
        val frac = q - whole
        val f = when {
            frac < 0.06 -> ""
            frac < 0.29 -> "¼"
            frac < 0.40 -> "⅓"
            frac < 0.60 -> "½"
            frac < 0.71 -> "⅔"
            frac < 0.94 -> "¾"
            else -> return (whole + 1).toString()
        }
        return when {
            whole == 0L && f.isNotEmpty() -> f
            f.isEmpty() -> whole.toString()
            else -> "$whole$f"
        }
    }

    private companion object {
        const val TONIGHT_TAG = "tonight"
        const val TONIGHT_MAX_MINUTES = 15
    }
}
