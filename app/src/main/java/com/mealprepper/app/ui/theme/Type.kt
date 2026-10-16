package com.mealprepper.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mealprepper.app.R

val Poppins = FontFamily(
    Font(R.font.poppins_light, FontWeight.Light),
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Poppins,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

val AppTypography = Typography(
    displayLarge = style(52, FontWeight.Bold, 60, -1.0),
    displayMedium = style(42, FontWeight.Bold, 50, -0.8),
    displaySmall = style(34, FontWeight.Bold, 42, -0.5),
    headlineLarge = style(30, FontWeight.SemiBold, 38, -0.4),
    headlineMedium = style(26, FontWeight.SemiBold, 34, -0.3),
    headlineSmall = style(22, FontWeight.SemiBold, 30, -0.2),
    titleLarge = style(20, FontWeight.SemiBold, 28),
    titleMedium = style(16, FontWeight.SemiBold, 24, 0.1),
    titleSmall = style(14, FontWeight.Medium, 20, 0.1),
    bodyLarge = style(16, FontWeight.Normal, 24, 0.2),
    bodyMedium = style(14, FontWeight.Normal, 21, 0.2),
    bodySmall = style(12, FontWeight.Normal, 17, 0.3),
    labelLarge = style(14, FontWeight.SemiBold, 20, 0.2),
    labelMedium = style(12, FontWeight.Medium, 16, 0.4),
    labelSmall = style(11, FontWeight.Medium, 14, 0.5),
)
