package com.mealprepper.app.data.db

import androidx.room.TypeConverter
import com.mealprepper.app.data.model.Aisle
import com.mealprepper.app.data.model.MealType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val converterJson = Json { ignoreUnknownKeys = true }
private val stringListSerializer = ListSerializer(String.serializer())

/** Room type converters. Enums are stored by name so reordering enum constants is safe. */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String =
        converterJson.encodeToString(stringListSerializer, value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList()
        else runCatching { converterJson.decodeFromString(stringListSerializer, value) }
            .getOrDefault(emptyList())

    @TypeConverter
    fun fromMealType(value: MealType): String = value.name

    @TypeConverter
    fun toMealType(value: String): MealType =
        MealType.entries.firstOrNull { it.name == value } ?: MealType.DINNER

    @TypeConverter
    fun fromAisle(value: Aisle): String = value.name

    @TypeConverter
    fun toAisle(value: String): Aisle =
        Aisle.entries.firstOrNull { it.name == value } ?: Aisle.OTHER
}
