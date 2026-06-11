package com.example.ez_capstone.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Composition local — true when high-contrast accessibility mode is active */
val LocalHighContrast = staticCompositionLocalOf { false }

/** Composition local — true when voice-only mode is active (hides map, shows text only) */
val LocalVoiceOnlyMode = staticCompositionLocalOf { false }

// OBSIDIAN DESIGN SYSTEM — Theme

private val ObsidianColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Background,
    primaryContainer = AccentDim,
    onPrimaryContainer = TextPrimary,
    secondary = AccentLight,
    onSecondary = Background,
    secondaryContainer = SurfaceTop,
    onSecondaryContainer = TextPrimary,
    tertiary = Success,
    onTertiary = Background,
    tertiaryContainer = SurfaceTop,
    onTertiaryContainer = TextPrimary,
    error = Error,
    onError = TextPrimary,
    errorContainer = SurfaceHigh,
    onErrorContainer = Error,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Border,
    outlineVariant = BorderActive,
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    inversePrimary = AccentDim,
    scrim = Background,
)

private val HighContrastColorScheme = darkColorScheme(
    primary = Color(0xFF99BBFF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3355CC),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFBBCCFF),
    onSecondary = Color.Black,
    tertiary = Color(0xFF00FF99),
    onTertiary = Color.Black,
    error = Color(0xFFFF6666),
    onError = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0D0D0D),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF444444),
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    inversePrimary = Color(0xFF3355CC),
    scrim = Color.Black,
)

@Composable
fun EZMapTheme(
    highContrast: Boolean = false,
    voiceOnlyMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Background.toArgb()
            window.navigationBarColor = Background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalVoiceOnlyMode provides voiceOnlyMode
    ) {
        MaterialTheme(
            colorScheme = if (highContrast) HighContrastColorScheme else ObsidianColorScheme,
            typography = EZMapTypography,
            content = content
        )
    }
}
