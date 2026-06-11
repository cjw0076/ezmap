package com.example.ez_capstone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ez_capstone.R

// OBSIDIAN DESIGN SYSTEM — Typography

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

val Rajdhani = FontFamily(
    Font(R.font.rajdhani_regular, FontWeight.Normal),
    Font(R.font.rajdhani_semibold, FontWeight.SemiBold),
    Font(R.font.rajdhani_bold, FontWeight.Bold),
)

val DashboardFont = FontFamily.Monospace

// Legacy alias
val Orbitron = Rajdhani

val EZMapTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    ),
    displayMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    ),
    displaySmall = TextStyle(
        fontFamily = DashboardFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        color = TextPrimary,
    ),
    headlineLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    ),
    headlineMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    ),
    headlineSmall = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = TextPrimary,
    ),
    titleLarge = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = TextPrimary,
    ),
    titleMedium = TextStyle(
        fontFamily = Pretendard,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    ),
    titleSmall = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        color = TextSecondary,
    ),
    bodyLarge = TextStyle(
        fontFamily = Pretendard,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        color = TextPrimary,
    ),
    bodyMedium = TextStyle(
        fontFamily = Pretendard,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        color = TextSecondary,
    ),
    bodySmall = TextStyle(
        fontFamily = Pretendard,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary,
    ),
    labelLarge = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = TextSecondary,
    ),
    labelMedium = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary,
    ),
    labelSmall = TextStyle(
        fontFamily = Rajdhani,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        letterSpacing = 0.5.sp,
        color = TextDim,
    ),
)

// Custom Styles

val DashboardLarge = TextStyle(
    fontFamily = DashboardFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 24.sp,
)

val DashboardUnit = TextStyle(
    fontFamily = DashboardFont,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    color = TextDim,
)

val TurnDistance = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 32.sp,
    letterSpacing = 0.sp,
)

val TurnDirection = TextStyle(
    fontWeight = FontWeight.ExtraBold,
    fontSize = 18.sp,
)

val ChipText = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = 0.3.sp,
)

val AppBarTitle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    letterSpacing = 0.sp,
    color = TextPrimary,
)

val SkillLabel = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = 0.5.sp,
)

// HUD Style aliases (used by DrivingDashboard, TurnInstructionCard)
val HudLabelStyle = TextStyle(
    fontFamily = Rajdhani,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    letterSpacing = 1.sp,
    color = TextSecondary,
)

val HudValueStyle = DashboardLarge

val HudUnitStyle = DashboardUnit

// Turn instruction aliases
val TurnDirectionStyle = TurnDirection

val TurnRoadStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontSize = 14.sp,
    color = TextSecondary,
)

val TurnDistanceStyle = TurnDistance
