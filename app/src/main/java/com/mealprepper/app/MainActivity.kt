package com.mealprepper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mealprepper.app.data.model.UserProfile
import com.mealprepper.app.ui.nav.AppNavHost
import com.mealprepper.app.ui.theme.MealPrepperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as MealPrepperApp).container
        setContent {
            // null until DataStore has emitted once, so we never flash onboarding for returning users.
            val profile: UserProfile? by container.profileRepository.profile
                .collectAsState(initial = null)
            splash.setKeepOnScreenCondition { profile == null }
            val p = profile ?: return@setContent
            MealPrepperTheme(dynamicColor = p.useDynamicColor) {
                // Decide the start destination once, so finishing onboarding doesn't rebuild the graph.
                val startOnboarding = remember { !p.onboardingDone }
                AppNavHost(startOnboarding = startOnboarding)
            }
        }
    }
}
