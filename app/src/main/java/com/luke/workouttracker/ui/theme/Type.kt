package com.luke.workouttracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.luke.workouttracker.R

/**
 * Archivo, as a variable font. `minSdk = 26` is exactly the floor for
 * FontVariation weight axes, so one file covers every weight.
 */
@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int) = Font(
    resId = R.font.archivo_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Archivo = FontFamily(
    archivo(400),
    archivo(600),
    archivo(700),
    archivo(800),
)

/** Every figure the user reads or edits. Tabular by construction. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

val WorkoutTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PlexMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(800),
        fontSize = 20.sp,
        letterSpacing = (-0.015).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(700),
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(700),
        fontSize = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(400),
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(400),
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Archivo,
        fontWeight = FontWeight(600),
        fontSize = 9.sp,
        letterSpacing = 0.18.em,
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
