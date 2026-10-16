package com.mealprepper.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mealprepper.app.data.model.GoalPace
import com.mealprepper.app.data.model.Sex
import com.mealprepper.app.data.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profile")

/** [ProfileRepository] persisted in Preferences DataStore (on device only). */
class DataStoreProfileRepository(context: Context) : ProfileRepository {

    private val store: DataStore<Preferences> = context.applicationContext.dataStore

    override val profile: Flow<UserProfile> = store.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it.toProfile() }
        .distinctUntilChanged()

    override suspend fun update(transform: (UserProfile) -> UserProfile) {
        store.edit { prefs ->
            val updated = transform(prefs.toProfile())
            prefs.write(updated)
        }
    }

    private fun Preferences.toProfile(): UserProfile {
        val d = UserProfile()
        return UserProfile(
            name = this[Keys.NAME] ?: d.name,
            age = this[Keys.AGE] ?: d.age,
            sex = this[Keys.SEX]?.let { raw -> Sex.entries.firstOrNull { it.name == raw } } ?: d.sex,
            heightIn = this[Keys.HEIGHT_IN] ?: d.heightIn,
            weightLb = this[Keys.WEIGHT_LB] ?: d.weightLb,
            goalWeightLb = this[Keys.GOAL_WEIGHT_LB] ?: d.goalWeightLb,
            pace = this[Keys.PACE]?.let { raw -> GoalPace.entries.firstOrNull { it.name == raw } } ?: d.pace,
            swimMinutesPerDay = this[Keys.SWIM_MINUTES] ?: d.swimMinutesPerDay,
            swimYardsPerDay = this[Keys.SWIM_YARDS] ?: d.swimYardsPerDay,
            weeklyBudgetUsd = this[Keys.WEEKLY_BUDGET] ?: d.weeklyBudgetUsd,
            mealsPerDay = this[Keys.MEALS_PER_DAY] ?: d.mealsPerDay,
            includeSnack = this[Keys.INCLUDE_SNACK] ?: d.includeSnack,
            useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: d.useDynamicColor,
            onboardingDone = this[Keys.ONBOARDING_DONE] ?: d.onboardingDone,
            avoidTags = this[Keys.AVOID_TAGS] ?: d.avoidTags,
        )
    }

    private fun MutablePreferences.write(p: UserProfile) {
        this[Keys.NAME] = p.name
        this[Keys.AGE] = p.age
        this[Keys.SEX] = p.sex.name
        this[Keys.HEIGHT_IN] = p.heightIn
        this[Keys.WEIGHT_LB] = p.weightLb
        this[Keys.GOAL_WEIGHT_LB] = p.goalWeightLb
        this[Keys.PACE] = p.pace.name
        this[Keys.SWIM_MINUTES] = p.swimMinutesPerDay
        this[Keys.SWIM_YARDS] = p.swimYardsPerDay
        this[Keys.WEEKLY_BUDGET] = p.weeklyBudgetUsd
        this[Keys.MEALS_PER_DAY] = p.mealsPerDay
        this[Keys.INCLUDE_SNACK] = p.includeSnack
        this[Keys.DYNAMIC_COLOR] = p.useDynamicColor
        this[Keys.ONBOARDING_DONE] = p.onboardingDone
        this[Keys.AVOID_TAGS] = p.avoidTags
    }

    private object Keys {
        val NAME = stringPreferencesKey("name")
        val AGE = intPreferencesKey("age")
        val SEX = stringPreferencesKey("sex")
        val HEIGHT_IN = intPreferencesKey("height_in")
        val WEIGHT_LB = doublePreferencesKey("weight_lb")
        val GOAL_WEIGHT_LB = doublePreferencesKey("goal_weight_lb")
        val PACE = stringPreferencesKey("pace")
        val SWIM_MINUTES = intPreferencesKey("swim_minutes_per_day")
        val SWIM_YARDS = intPreferencesKey("swim_yards_per_day")
        val WEEKLY_BUDGET = intPreferencesKey("weekly_budget_usd")
        val MEALS_PER_DAY = intPreferencesKey("meals_per_day")
        val INCLUDE_SNACK = booleanPreferencesKey("include_snack")
        val DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val AVOID_TAGS = stringSetPreferencesKey("avoid_tags")
    }
}
