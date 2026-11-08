package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BndDarkColorScheme = darkColorScheme(
    primary = BndVioletPrimary,
    onPrimary = BndTextPrimary,
    primaryContainer = BndSurfaceVariantDark,
    onPrimaryContainer = BndSoftPurple,
    secondary = BndPurpleSecondary,
    onSecondary = BndTextPrimary,
    secondaryContainer = BndSurfaceCard,
    onSecondaryContainer = BndSoftPurple,
    tertiary = BndOrchidTertiary,
    onTertiary = BndTextPrimary,
    background = BndBgDark,
    onBackground = BndTextPrimary,
    surface = BndSurfaceDark,
    onSurface = BndTextPrimary,
    surfaceVariant = BndSurfaceVariantDark,
    onSurfaceVariant = BndTextSecondary,
    outline = BndPurpleSecondary.copy(alpha = 0.4f),
    outlineVariant = BndSurfaceCardHover,
    error = BndErrorRed,
    onError = BndTextPrimary
)

@Composable
fun V2reyBndTheme(
    darkTheme: Boolean = true, // Default to stunning dark purple & violet theme
    content: @Composable () -> Unit
) {
    val colorScheme = BndDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BndBgDark.toArgb()
                window.navigationBarColor = BndBgDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
