package com.mealprepper.app

import android.content.Context
import com.mealprepper.app.data.db.AppDatabase
import com.mealprepper.app.data.repo.DataStoreProfileRepository
import com.mealprepper.app.data.repo.MealRepository
import com.mealprepper.app.data.repo.OfflineMealRepository
import com.mealprepper.app.data.repo.ProfileRepository
import com.mealprepper.app.data.seed.SeedLoader

/** Manual DI. Everything is on-device: Room (SQLite) + DataStore. No network. */
class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.build(context)
    val mealRepository: MealRepository = OfflineMealRepository(database)
    val profileRepository: ProfileRepository = DataStoreProfileRepository(context)
    val seedLoader: SeedLoader = SeedLoader(context, database)
}
