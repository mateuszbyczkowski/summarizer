package com.summarizer.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// OPTION 1: PROFESSIONAL OCEAN BLUE (ACTIVE)
private val DarkColorScheme = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color.White,
    primaryContainer = DarkOcean,
    onPrimaryContainer = Color.White,
    secondary = BlueGreyLight,
    onSecondary = Color.White,
    tertiary = CyanLight,
    onTertiary = Color.Black,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = Color(0xFF001D36),
    secondary = BlueGrey,
    onSecondary = Color.White,
    tertiary = CyanAccent,
    onTertiary = Color.White,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    error = Color(0xFFB00020)
)

@Composable
fun SummarizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
