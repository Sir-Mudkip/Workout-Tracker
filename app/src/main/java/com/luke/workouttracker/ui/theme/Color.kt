package com.luke.workouttracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The only place colour literals may appear.
 *
 * [Track] is the load-bearing token: the trace is a two-state system —
 * accent for current, track for remaining or historical — and it is that
 * pairing, not the accent alone, that carries the app's identity.
 *
 * Values are contrast-checked against the surfaces they sit on. Track is
 * held to 3:1 because in the progress chart it draws historical data
 * lines, which carry information (WCAG 1.4.11). It looks too bright in
 * isolation and is correct on device — if accent and track ever feel too
 * close, lighten accent rather than darkening track.
 */
object WorkoutColors {
    // Dark
    val Ground = Color(0xFF0F1216)
    val Surface = Color(0xFF171B21)
    val SurfaceRaised = Color(0xFF1E232B)
    val Border = Color(0xFF272D36)
    val Track = Color(0xFF66718A)
    val Accent = Color(0xFFFF9E2C)
    val OnAccent = Color(0xFF1A1206)
    val Text = Color(0xFFECEEF1)
    val Muted = Color(0xFF8A929E)
    val Danger = Color(0xFFE5484D)

    // Light
    val GroundLight = Color(0xFFF4F5F7)
    val SurfaceLight = Color(0xFFFFFFFF)
    val BorderLight = Color(0xFFE2E5E9)
    val TrackLight = Color(0xFF8B96A6)
    val AccentLight = Color(0xFFA85C00)
    val OnAccentLight = Color(0xFFFFFFFF)
    val TextLight = Color(0xFF0F1216)
    val MutedLight = Color(0xFF5C646F)
    val DangerLight = Color(0xFFC62A2F)
}

/**
 * `outlineVariant` carries [WorkoutColors.Track] so trace drawing reads
 * it from the scheme rather than importing the token object directly.
 */
val DarkScheme: ColorScheme = darkColorScheme(
    primary = WorkoutColors.Accent,
    onPrimary = WorkoutColors.OnAccent,
    primaryContainer = WorkoutColors.Accent,
    onPrimaryContainer = WorkoutColors.OnAccent,
    inversePrimary = WorkoutColors.AccentLight,

    // No secondary or tertiary hue exists in this design. Both map onto the
    // accent so that any component reaching for them cannot fall back to the
    // Material 3 baseline purple.
    secondary = WorkoutColors.Accent,
    onSecondary = WorkoutColors.OnAccent,
    secondaryContainer = WorkoutColors.SurfaceRaised,
    onSecondaryContainer = WorkoutColors.Text,
    tertiary = WorkoutColors.Accent,
    onTertiary = WorkoutColors.OnAccent,
    tertiaryContainer = WorkoutColors.SurfaceRaised,
    onTertiaryContainer = WorkoutColors.Text,

    background = WorkoutColors.Ground,
    onBackground = WorkoutColors.Text,
    surface = WorkoutColors.Surface,
    onSurface = WorkoutColors.Text,
    surfaceVariant = WorkoutColors.SurfaceRaised,
    onSurfaceVariant = WorkoutColors.Muted,
    surfaceTint = WorkoutColors.Accent,
    inverseSurface = WorkoutColors.Text,
    inverseOnSurface = WorkoutColors.Ground,
    surfaceBright = WorkoutColors.SurfaceRaised,
    surfaceDim = WorkoutColors.Ground,
    surfaceContainer = WorkoutColors.Surface,
    surfaceContainerLow = WorkoutColors.Ground,
    surfaceContainerLowest = WorkoutColors.Ground,
    surfaceContainerHigh = WorkoutColors.SurfaceRaised,
    surfaceContainerHighest = WorkoutColors.SurfaceRaised,

    outline = WorkoutColors.Border,
    outlineVariant = WorkoutColors.Track,
    scrim = WorkoutColors.Ground,

    error = WorkoutColors.Danger,
    onError = WorkoutColors.Text,
    errorContainer = WorkoutColors.SurfaceRaised,
    onErrorContainer = WorkoutColors.Danger,
)


val LightScheme: ColorScheme = lightColorScheme(
    primary = WorkoutColors.AccentLight,
    onPrimary = WorkoutColors.OnAccentLight,
    primaryContainer = WorkoutColors.AccentLight,
    onPrimaryContainer = WorkoutColors.OnAccentLight,
    inversePrimary = WorkoutColors.Accent,

    secondary = WorkoutColors.AccentLight,
    onSecondary = WorkoutColors.OnAccentLight,
    secondaryContainer = WorkoutColors.GroundLight,
    onSecondaryContainer = WorkoutColors.TextLight,
    tertiary = WorkoutColors.AccentLight,
    onTertiary = WorkoutColors.OnAccentLight,
    tertiaryContainer = WorkoutColors.GroundLight,
    onTertiaryContainer = WorkoutColors.TextLight,

    background = WorkoutColors.GroundLight,
    onBackground = WorkoutColors.TextLight,
    surface = WorkoutColors.SurfaceLight,
    onSurface = WorkoutColors.TextLight,
    surfaceVariant = WorkoutColors.GroundLight,
    onSurfaceVariant = WorkoutColors.MutedLight,
    surfaceTint = WorkoutColors.AccentLight,
    inverseSurface = WorkoutColors.TextLight,
    inverseOnSurface = WorkoutColors.SurfaceLight,
    surfaceBright = WorkoutColors.SurfaceLight,
    surfaceDim = WorkoutColors.GroundLight,
    surfaceContainer = WorkoutColors.SurfaceLight,
    surfaceContainerLow = WorkoutColors.GroundLight,
    surfaceContainerLowest = WorkoutColors.SurfaceLight,
    surfaceContainerHigh = WorkoutColors.GroundLight,
    surfaceContainerHighest = WorkoutColors.GroundLight,

    outline = WorkoutColors.BorderLight,
    outlineVariant = WorkoutColors.TrackLight,
    scrim = WorkoutColors.TextLight,

    error = WorkoutColors.DangerLight,
    onError = WorkoutColors.SurfaceLight,
    errorContainer = WorkoutColors.SurfaceLight,
    onErrorContainer = WorkoutColors.DangerLight,
)
