package com.mealprepper.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Basil,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = BasilDark,
    secondary = Apricot,
    onSecondary = Ink,
    secondaryContainer = ApricotSoft,
    onSecondaryContainer = Color(0xFF5A3300),
    tertiary = Plum,
    onTertiary = Color.White,
    tertiaryContainer = PlumSoft,
    onTertiaryContainer = Color(0xFF2A1F6B),
    error = Tomato,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Cream,
    onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F4EE),
    surfaceContainer = Color(0xFFF2EEE5),
    surfaceContainerHigh = Color(0xFFECE7DC),
    surfaceContainerHighest = Color(0xFFE6E0D3),
    outline = Color(0xFFC9C2B4),
    outlineVariant = Line,
)

private val DarkColors = darkColorScheme(
    primary = BasilLight,
    onPrimary = Color(0xFF00382A),
    primaryContainer = BasilDark,
    onPrimaryContainer = Mint,
    secondary = Color(0xFFFFB877),
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6B3D10),
    onSecondaryContainer = ApricotSoft,
    tertiary = Color(0xFFC6BCFF),
    onTertiary = Color(0xFF2A1F6B),
    tertiaryContainer = Color(0xFF443693),
    onTertiaryContainer = PlumSoft,
    error = Color(0xFFFF8A75),
    background = Night,
    onBackground = Fog,
    surface = Night,
    onSurface = Fog,
    surfaceVariant = NightHigh,
    onSurfaceVariant = FogSoft,
    surfaceContainerLowest = Color(0xFF0A0E0C),
    surfaceContainerLow = Color(0xFF131917),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = Color(0xFF2C3632),
    outline = Color(0xFF55615B),
    outlineVariant = Color(0xFF2E3833),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun MealPrepperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
