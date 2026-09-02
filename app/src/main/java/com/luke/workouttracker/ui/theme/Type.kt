package com.luke.workouttracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.luke.workouttracker.R

/**
 * Archivo as four static weights.
 *
 * The variable build was tried first and silently failed to load — Compose
 * fell back to the system font while still applying size, weight and letter
 * spacing, so the theme looked applied but the letterforms were Roboto.
 * Static instances also avoid synthetic bold, which is what loading a single
 * variable file without a working weight axis produces.
 */
val Archivo = FontFamily(
    Font(R.font.archivo_regular, FontWeight(400)),
    Font(R.font.archivo_semibold, FontWeight(600)),
    Font(R.font.archivo_bold, FontWeight(700)),
    Font(R.font.archivo_extrabold, FontWeight(800)),
)

/** Every figure the user reads or edits. Tabular by construction. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

val WorkoutTypography = Typography(
    // Figures. Plex Mono everywhere a number is read as a value.
    displayLarge = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp, letterSpacing = (-0.02).em,
    ),
    displayMedium = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, letterSpacing = (-0.02).em,
    ),
    displaySmall = TextStyle(
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, letterSpacing = (-0.02).em,
    ),

    // Words. Archivo throughout.
    headlineLarge = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(800),
        fontSize = 28.sp, letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(800),
        fontSize = 24.sp, letterSpacing = (-0.018).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(800),
        fontSize = 20.sp, letterSpacing = (-0.015).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700),
        fontSize = 20.sp, letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700), fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700), fontSize = 13.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 12.sp,
    ),

    // Controls. labelLarge is what Button and FloatingActionButton use.
    labelLarge = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700),
        fontSize = 14.sp, letterSpacing = 0.01.em,
    ),
    labelMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(600),
        fontSize = 12.sp, letterSpacing = 0.04.em,
    ),
    labelSmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(600),
        fontSize = 9.sp, letterSpacing = 0.18.em,
    ),
)

/**
 * Reps, weights, volumes, timers. Not a Material 3 role — added as an
 * extension so figures are styled consistently without abusing an
 * unrelated style.
 */
val Typography.numeric: TextStyle
    get() = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp,
        letterSpacing = (-0.03).em,
    )
