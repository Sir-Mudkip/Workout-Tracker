package com.luke.workouttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.luke.workouttracker.data.prefs.ThemeMode

/**
 * Dynamic colour is deliberately absent.
 *
 * It previously defaulted to true, which meant Material You replaced the
 * entire scheme with wallpaper-derived colours on every Android 12+
 * device — the app had no identity of its own, and the amber palette
 * defined here was dead code. The parameter is removed rather than
 * defaulted to false so it cannot be switched back on without a
 * considered change to this file.
 */
@Composable
fun WorkoutTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = WorkoutTypography,
        shapes = WorkoutShapes,
        content = content,
    )
}
