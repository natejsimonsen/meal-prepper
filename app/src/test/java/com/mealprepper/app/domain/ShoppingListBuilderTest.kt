package com.mealprepper.app.domain

import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.MealType
import com.mealprepper.app.data.model.ShoppingItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShoppingListBuilderTest {

    @Test
    fun scalesByServingsAndMergesMassUnits() {
        val a = recipe("a", MealType.DINNER, servings = 2, title = "Tacos",
            ingredients = listOf(Triple("Chicken Breast ", 1.0, "lb")), price = 6.0)
        val b = recipe("b", MealType.DINNER, servings = 4, title = "Bowls",
            ingredients = listOf(Triple("chicken breast", 16.0, "oz")), price = 8.0)
        // a ×2 servings (1.0×) = 1 lb; b ×2 servings (0.5×) = 8 oz → 1.5 lb
        val items = ShoppingListBuilder.build(listOf(CookRequest(a, 2), CookRequest(b, 2)))
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("chicken breast", item.name)
        assertEquals("lb", item.unit)
        assertEquals(1.5, item.quantity, 1e-9)
        assertEquals(10.0, item.estPriceUsd, 1e-9)
        assertEquals(listOf("Tacos", "Bowls"), item.forRecipes)
    }

    @Test
    fun smallMassStaysInOunces() {
        val a = recipe("a", MealType.DINNER, servings = 1, ingredients = listOf(Triple("feta", 3.0, "oz")))
        val b = recipe("b", MealType.DINNER, servings = 1, ingredients = listOf(Triple("feta", 2.0, "oz")))
        val item = ShoppingListBuilder.build(listOf(CookRequest(a, 1), CookRequest(b, 1))).single()
        assertEquals("oz", item.unit)
        assertEquals(5.0, item.quantity, 1e-9)
    }

    @Test
    fun metricOnlyStaysMetric() {
        val a = recipe("a", MealType.DINNER, servings = 1, ingredients = listOf(Triple("rice", 600.0, "g")))
        val b = recipe("b", MealType.DINNER, servings = 1, ingredients = listOf(Triple("rice", 0.5, "kg")))
        val item = ShoppingListBuilder.build(listOf(CookRequest(a, 1), CookRequest(b, 1))).single()
        assertEquals("kg", item.unit)
        assertEquals(1.1, item.quantity, 1e-9)
    }

    @Test
    fun volumeConversions() {
        // 2 tsp + 2 tsp = 4 tsp → 1⅓ tbsp → rounds up to 1.5 tbsp
        val a = recipe("a", MealType.DINNER, servings = 1, ingredients = listOf(Triple("cumin", 2.0, "tsp")))
        val b = recipe("b", MealType.DINNER, servings = 1, ingredients = listOf(Triple("cumin", 2.0, "tsp")))
        val cumin = ShoppingListBuilder.build(listOf(CookRequest(a, 1), CookRequest(b, 1))).single()
        assertEquals("tbsp", cumin.unit)
        assertEquals(1.5, cumin.quantity, 1e-9)

        // 3 tbsp + 0.5 cup = 11 tbsp → 0.6875 cup → 0.75 cup
        val c = recipe("c", MealType.DINNER, servings = 1, ingredients = listOf(Triple("milk", 3.0, "tbsp")))
        val d = recipe("d", MealType.DINNER, servings = 1, ingredients = listOf(Triple("milk", 0.5, "cup")))
        val milk = ShoppingListBuilder.build(listOf(CookRequest(c, 1), CookRequest(d, 1))).single()
        assertEquals("cup", milk.unit)
        assertEquals(0.75, milk.quantity, 1e-9)

        // 1 tsp stays tsp
        val e = recipe("e", MealType.DINNER, servings = 1, ingredients = listOf(Triple("salt", 1.0, "tsp")))
        val salt = ShoppingListBuilder.build(listOf(CookRequest(e, 1)), skipPantry = false).single()
        assertEquals("tsp", salt.unit)
        assertEquals(1.0, salt.quantity, 1e-9)
    }

    @Test
    fun countUnitsRoundUp() {
        val a = recipe("a", MealType.BREAKFAST, servings = 4, ingredients = listOf(Triple("egg", 6.0, "whole")))
        // 3 of 4 servings → 4.5 eggs → 5
        val egg = ShoppingListBuilder.build(listOf(CookRequest(a, 3))).single()
        assertEquals(5.0, egg.quantity, 1e-9)
        assertEquals("whole", egg.unit)

        val b = recipe("b", MealType.DINNER, servings = 2, ingredients = listOf(Triple("black beans", 1.0, "can")))
        val beans = ShoppingListBuilder.build(listOf(CookRequest(b, 1))).single()
        assertEquals(1.0, beans.quantity, 1e-9) // 0.5 can → 1 can
    }

    @Test
    fun incompatibleUnitsStaySeparate() {
        val a = recipe("a", MealType.DINNER, servings = 1, ingredients = listOf(Triple("garlic", 3.0, "clove")))
        val b = recipe("b", MealType.DINNER, servings = 1, ingredients = listOf(Triple("garlic", 1.0, "tsp")))
        val items = ShoppingListBuilder.build(listOf(CookRequest(a, 1), CookRequest(b, 1)))
        assertEquals(2, items.size)
        assertEquals(setOf("clove", "tsp"), items.map { it.unit }.toSet())
    }

    @Test
    fun pantrySkippingIsOptionalAndToleratesPlurals() {
        val a = recipe("a", MealType.DINNER, servings = 1, ingredients = listOf(
            Triple("olive oil", 1.0, "tbsp"), Triple("Kosher Salt", 1.0, "tsp"),
            Triple("egg", 2.0, "whole"), Triple("spinach", 5.0, "oz"),
        ))
        val pantry = setOf("Olive Oil", "kosher salt", "eggs")
        val skipped = ShoppingListBuilder.build(listOf(CookRequest(a, 1)), pantry, skipPantry = true)
        assertEquals(listOf("spinach"), skipped.map { it.name })
        val kept = ShoppingListBuilder.build(listOf(CookRequest(a, 1)), pantry, skipPantry = false)
        assertEquals(4, kept.size)
    }

    @Test
    fun sortedByAisleThenName() {
        val produce = recipe("p", MealType.LUNCH, servings = 1, aisle = Aisle.PRODUCE,
            ingredients = listOf(Triple("zucchini", 1.0, "whole"), Triple("avocado", 1.0, "whole")))
        val meat = recipe("m", MealType.LUNCH, servings = 1, aisle = Aisle.MEAT_SEAFOOD,
            ingredients = listOf(Triple("beef", 1.0, "lb")))
        val items = ShoppingListBuilder.build(listOf(CookRequest(meat, 1), CookRequest(produce, 1)))
        assertEquals(listOf("avocado", "zucchini", "beef"), items.map { it.name })
    }

    @Test
    fun zeroServingsAndDuplicatesAreHandled() {
        val a = recipe("a", MealType.DINNER, servings = 2, ingredients = listOf(Triple("tofu", 14.0, "oz")))
        assertTrue(ShoppingListBuilder.build(listOf(CookRequest(a, 0))).isEmpty())
        // Same recipe twice (two cooked batches) sums: 14 + 14 oz = 28 oz → 1.75 lb
        val item = ShoppingListBuilder.build(listOf(CookRequest(a, 2), CookRequest(a, 2))).single()
        assertEquals("lb", item.unit)
        assertEquals(1.75, item.quantity, 1e-9)
        assertEquals(listOf("a"), item.forRecipes)
    }

    @Test
    fun mergeIntoExistingKeepsIdsAndUnchecks() {
        val existing = listOf(
            ShoppingItemEntity(id = 7, name = "chicken breast", quantity = 1.0, unit = "lb",
                aisle = Aisle.MEAT_SEAFOOD, checked = true, estPriceUsd = 5.0, forRecipes = listOf("Tacos")),
            ShoppingItemEntity(id = 8, name = "paper towels", quantity = 1.0, unit = "whole",
                aisle = Aisle.OTHER, isManual = true),
        )
        val additions = listOf(
            ShoppingItemEntity(name = "chicken breast", quantity = 8.0, unit = "oz",
                aisle = Aisle.MEAT_SEAFOOD, estPriceUsd = 2.5, forRecipes = listOf("Bowls")),
            ShoppingItemEntity(name = "lime", quantity = 2.0, unit = "whole", aisle = Aisle.PRODUCE),
        )
        val merged = ShoppingListBuilder.merge(existing, additions)
        assertEquals(3, merged.size)
        val chicken = merged.first { it.name == "chicken breast" }
        assertEquals(7L, chicken.id)
        assertEquals(1.5, chicken.quantity, 1e-9)
        assertEquals("lb", chicken.unit)
        assertFalse(chicken.checked)
        assertEquals(7.5, chicken.estPriceUsd, 1e-9)
        assertEquals(listOf("Tacos", "Bowls"), chicken.forRecipes)
        assertEquals(0L, merged.first { it.name == "lime" }.id)
        assertNull(merged.first { it.name == "lime" }.note)
    }
}
