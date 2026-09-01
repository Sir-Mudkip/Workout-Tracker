# Trace Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the app a visual identity of its own — a dark, amber-accented design built around a two-state "trace" line — replacing the Material defaults and the wallpaper-derived dynamic colour that currently overrides everything.

**Architecture:** All tokens live in `ui/theme/` and are mapped onto Material 3 colour roles, so existing components restyle themselves without per-screen edits. Three trace primitives in `ui/theme/Trace.kt` are drawn once and reused by the session, rest timer, and progress screens.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Material 3, bundled variable fonts (`minSdk = 26`)

**Spec:** `docs/superpowers/specs/2026-09-02-trace-redesign-design.md`

## Global Constraints

- **No colour literal outside `ui/theme/Color.kt`.** Verified by grep in Task 7.
- **Every figure a user reads or edits uses `MaterialTheme.typography.numeric`** (IBM Plex Mono). Every word uses Archivo.
- **Accent always appears with a track.** Accent without a corresponding track is decoration and is wrong.
- **`dynamicColor` is removed from `WorkoutTheme` entirely**, not defaulted to false.
- **No layout, navigation or feature changes.** Visual pass only.
- **Both themes ship.** Light is not deferred.
- **One border weight (1dp) and one trace weight (2.5dp).**
- **Package root:** `com.luke.workouttracker`
- **Build:**
  ```bash
  export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
  export ANDROID_HOME=$HOME/Android/Sdk
  GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
  "$GRADLE" testDebugUnitTest --console=plain
  ```
- **The 55 existing unit tests must keep passing.** No logic changes here; a failure means something was broken by accident.

### Token reference

Copy these exactly. They were contrast-checked; do not adjust by eye.

| Token | Dark | Light |
|---|---|---|
| `ground` | `#0F1216` | `#F4F5F7` |
| `surface` | `#171B21` | `#FFFFFF` |
| `surfaceRaised` | `#1E232B` | `#FFFFFF` |
| `border` | `#272D36` | `#E2E5E9` |
| `track` | `#66718A` | `#8B96A6` |
| `accent` | `#FF9E2C` | `#A85C00` |
| `onAccent` | `#1A1206` | `#FFFFFF` |
| `text` | `#ECEEF1` | `#0F1216` |
| `muted` | `#8A929E` | `#5C646F` |
| `danger` | `#E5484D` | `#C62A2F` |

---

### Task 1: Fonts and typography

**Files:**
- Create: `app/src/main/res/font/archivo_variable.ttf` (downloaded)
- Create: `app/src/main/res/font/ibm_plex_mono_regular.ttf` (downloaded)
- Create: `app/src/main/res/font/ibm_plex_mono_semibold.ttf` (downloaded)
- Create: `app/src/main/java/com/luke/workouttracker/ui/theme/Type.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `val Archivo: FontFamily`, `val PlexMono: FontFamily`
  - `val WorkoutTypography: Typography`
  - `val Typography.numeric: TextStyle` (extension property)

- [ ] **Step 1: Download the fonts**

```bash
mkdir -p app/src/main/res/font
curl -fsSL -o app/src/main/res/font/archivo_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/archivo/Archivo%5Bwdth,wght%5D.ttf"
curl -fsSL -o app/src/main/res/font/ibm_plex_mono_regular.ttf \
  "https://github.com/google/fonts/raw/main/ofl/ibmplexmono/IBMPlexMono-Regular.ttf"
curl -fsSL -o app/src/main/res/font/ibm_plex_mono_semibold.ttf \
  "https://github.com/google/fonts/raw/main/ofl/ibmplexmono/IBMPlexMono-SemiBold.ttf"
ls -l app/src/main/res/font/
```

Expected: three files, roughly 640 KB, 135 KB and 135 KB. Both families
are OFL-licensed, so bundling is permitted.

Resource names must be lowercase letters, digits and underscores — that
is an Android requirement, not a preference.

- [ ] **Step 2: Write Type.kt**

Create `app/src/main/java/com/luke/workouttracker/ui/theme/Type.kt`:

```kotlin
package com.luke.workouttracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.luke.workouttracker.R

/**
 * Archivo, as a variable font. `minSdk = 26` is exactly the floor for
 * FontVariation weight axes, so one file covers every weight.
 */
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
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
        fontSize = 42.sp, letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(800),
        fontSize = 20.sp, letterSpacing = (-0.015).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700), fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(700), fontSize = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Archivo, fontWeight = FontWeight(400), fontSize = 12.sp,
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
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
        fontSize = 27.sp, letterSpacing = (-0.03).em,
    )
```

- [ ] **Step 3: Build**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL. If `variationSettings` is unresolved, the
Compose version predates it — fall back to `Font(R.font.archivo_variable,
FontWeight(weight))` and note it in the commit.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/font app/src/main/java/com/luke/workouttracker/ui/theme/Type.kt
git commit -m "feat(ui): bundle Archivo and IBM Plex Mono, define type scale"
```

---

### Task 2: Colour, shape, and theme

Kills dynamic colour. After this task the whole app changes appearance,
because every Material component reads from the scheme.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/ui/theme/Color.kt`
- Create: `app/src/main/java/com/luke/workouttracker/ui/theme/Shape.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/MainActivity.kt` (only if it passes `dynamicColor`)

**Interfaces:**
- Consumes: `WorkoutTypography` (Task 1)
- Produces:
  - `object WorkoutColors` with every token as a `Color`
  - `val WorkoutShapes: Shapes`
  - `WorkoutTheme(mode: ThemeMode, content: @Composable () -> Unit)` — **no `dynamicColor` parameter**

- [ ] **Step 1: Write Color.kt**

```kotlin
package com.luke.workouttracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The only place colour literals may appear.
 *
 * `track` is the load-bearing token: the trace is a two-state system —
 * accent for current, track for remaining or historical — and it is that
 * pairing, not the accent alone, that carries the app's identity.
 *
 * Values are contrast-checked against the surfaces they sit on. `track`
 * is held to 3:1 because in the progress chart it draws historical data
 * lines, which carry information (WCAG 1.4.11).
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
 * `outlineVariant` carries `track` so trace drawing reads it from the
 * scheme rather than importing WorkoutColors directly.
 */
val DarkScheme: ColorScheme = darkColorScheme(
    primary = WorkoutColors.Accent,
    onPrimary = WorkoutColors.OnAccent,
    background = WorkoutColors.Ground,
    onBackground = WorkoutColors.Text,
    surface = WorkoutColors.Surface,
    onSurface = WorkoutColors.Text,
    surfaceVariant = WorkoutColors.SurfaceRaised,
    onSurfaceVariant = WorkoutColors.Muted,
    outline = WorkoutColors.Border,
    outlineVariant = WorkoutColors.Track,
    error = WorkoutColors.Danger,
    onError = WorkoutColors.Text,
)

val LightScheme: ColorScheme = lightColorScheme(
    primary = WorkoutColors.AccentLight,
    onPrimary = WorkoutColors.OnAccentLight,
    background = WorkoutColors.GroundLight,
    onBackground = WorkoutColors.TextLight,
    surface = WorkoutColors.SurfaceLight,
    onSurface = WorkoutColors.TextLight,
    surfaceVariant = WorkoutColors.SurfaceLight,
    onSurfaceVariant = WorkoutColors.MutedLight,
    outline = WorkoutColors.BorderLight,
    outlineVariant = WorkoutColors.TrackLight,
    error = WorkoutColors.DangerLight,
    onError = WorkoutColors.SurfaceLight,
)
```

- [ ] **Step 2: Write Shape.kt**

```kotlin
package com.luke.workouttracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Three deliberate steps, not one radius everywhere. */
val WorkoutShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)
```

- [ ] **Step 3: Rewrite Theme.kt**

Replace the entire file:

```kotlin
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
 * device — the app had no identity of its own. The parameter is removed
 * rather than defaulted to false so it cannot be switched back on
 * without a considered change here.
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
```

- [ ] **Step 4: Check MainActivity still compiles**

`MainActivity.kt:27` currently reads `WorkoutTheme(mode = mode) {`, which
needs no change. Confirm nothing passes `dynamicColor`:

```bash
grep -rn "dynamicColor" app/src/main/java --include=*.kt
```
Expected: no results.

- [ ] **Step 5: Build and run tests**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, 55 tests passing.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/theme/ app/src/main/java/com/luke/workouttracker/MainActivity.kt
git commit -m "feat(ui): replace dynamic colour with the Trace palette and shape scale"
```

---

### Task 3: Trace primitives

Three drawing composables, defined once. Every edge case below is
specified because none of them will be caught by a test.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/ui/theme/Trace.kt`

**Interfaces:**
- Consumes: `MaterialTheme.colorScheme` (Task 2)
- Produces:
  - `TraceProgress(completed: Int, total: Int, modifier: Modifier)`
  - `TraceRing(progress: Float, modifier: Modifier, content: @Composable () -> Unit)`
  - `TraceChart(values: List<Double>, highlighted: Boolean, hollowAt: Set<Int>, modifier: Modifier)`

- [ ] **Step 1: Write Trace.kt**

```kotlin
package com.luke.workouttracker.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val TraceWidth = 2.5.dp

/**
 * Set progress as a single line: accent for completed, track for
 * remaining, with a dot at the current position.
 */
@Composable
fun TraceProgress(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "traceProgress",
    )
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(20.dp)) {
        val y = size.height / 2f
        val w = TraceWidth.toPx()
        drawLine(track, Offset(w, y), Offset(size.width - w, y), w, StrokeCap.Round)
        if (animated > 0f) {
            val end = w + (size.width - 2 * w) * animated
            drawLine(accent, Offset(w, y), Offset(end, y), w, StrokeCap.Round)
            drawCircle(accent, radius = w * 1.6f, center = Offset(end, y))
        }
    }
}

/**
 * The same line closed into a ring, for the rest timer. [progress] is
 * 0..1 and may exceed a full sweep conceptually — it is coerced.
 */
@Composable
fun TraceRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = TraceWidth.toPx() * 1.4f
            val inset = w / 2f
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent, startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                style = Stroke(width = w, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/**
 * Weekly volume as a polyline.
 *
 * [highlighted] draws in accent, otherwise track — so one exercise can be
 * the subject of the screen. [hollowAt] marks indices whose week was
 * performed as a swapped exercise.
 *
 * Edge cases, all of which occur in real data:
 *  - empty        -> draw nothing
 *  - one point    -> a single dot, centred
 *  - all equal    -> a flat line at mid height (no divide by zero)
 *  - all zero     -> same flat line; never scales by zero
 */
@Composable
fun TraceChart(
    values: List<Double>,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    hollowAt: Set<Int> = emptySet(),
) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    val stroke = if (highlighted) accent else track

    Canvas(modifier.fillMaxWidth().height(72.dp)) {
        if (values.isEmpty()) return@Canvas
        val w = TraceWidth.toPx()
        val pad = w * 3f
        val usableW = size.width - pad * 2
        val usableH = size.height - pad * 2

        if (values.size == 1) {
            drawCircle(stroke, w * 1.8f, Offset(size.width / 2f, size.height / 2f))
            return@Canvas
        }

        val max = values.max()
        val min = values.min()
        val span = max - min
        fun yFor(v: Double): Float =
            if (span <= 0.0) size.height / 2f
            else (pad + usableH * (1f - ((v - min) / span).toFloat()))
        fun xFor(i: Int): Float = pad + usableW * (i.toFloat() / (values.size - 1))

        val path = Path().apply {
            moveTo(xFor(0), yFor(values[0]))
            for (i in 1 until values.size) lineTo(xFor(i), yFor(values[i]))
        }
        drawPath(path, stroke, style = Stroke(width = w, cap = StrokeCap.Round))

        values.indices.forEach { i ->
            val c = Offset(xFor(i), yFor(values[i]))
            val isLast = i == values.lastIndex
            when {
                i in hollowAt -> {
                    drawCircle(surface, w * 1.9f, c)
                    drawCircle(stroke, w * 1.9f, c, style = Stroke(width = w * 0.8f))
                }
                isLast -> drawCircle(stroke, w * 1.8f, c)
            }
        }
    }
}
```

- [ ] **Step 2: Build**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/theme/Trace.kt
git commit -m "feat(ui): add trace drawing primitives"
```

---

### Task 4: Active session screen

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt`

**Interfaces:**
- Consumes: `TraceProgress` (Task 3), `numeric` (Task 1)
- Produces: nothing

- [ ] **Step 1: Add the set progress trace**

In `ActiveCard`, after the target text line and before the reps/weight
`Row`, insert:

```kotlin
TraceProgress(
    completed = state.currentSetIdx,
    total = state.totalSetsForCurrent,
    modifier = Modifier.padding(top = 12.dp),
)
```

Add imports:
```kotlin
import com.luke.workouttracker.ui.theme.TraceProgress
import com.luke.workouttracker.ui.theme.numeric
```

- [ ] **Step 2: Apply numeric type to the input fields**

On both `OutlinedTextField`s in `ActiveCard`, add:
```kotlin
textStyle = MaterialTheme.typography.numeric,
```

- [ ] **Step 3: Apply the eyebrow label to the logged card**

In `LoggedSetsCard`, replace:
```kotlin
Text("Logged this session", style = MaterialTheme.typography.titleSmall)
```
with:
```kotlin
Text(
    "LOGGED THIS SESSION",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

- [ ] **Step 4: Build and run tests**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, 55 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt
git commit -m "feat(ui): apply trace and numeric type to the active session"
```

---

### Task 5: Rest timer ring

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt`

**Interfaces:**
- Consumes: `TraceRing` (Task 3)
- Produces: nothing

- [ ] **Step 1: Replace the timer text with the ring**

In `RestTimerDialog`, replace the elapsed-time `Text` inside the `Column`
with the ring. The ring fills over a nominal three minutes — rest is
open-ended, so this is a visual pace indicator, not a limit, and it
simply completes and stays full beyond that.

```kotlin
Box(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    contentAlignment = Alignment.Center,
) {
    TraceRing(
        progress = (elapsedMs / 180_000f).coerceIn(0f, 1f),
        modifier = Modifier.size(180.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatDuration(elapsedMs),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                "ELAPSED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

Add imports:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import com.luke.workouttracker.ui.theme.TraceRing
```

- [ ] **Step 2: Restyle the difficulty prompt**

Replace:
```kotlin
Text(
    "How did that feel? (optional)",
    style = MaterialTheme.typography.bodySmall,
    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
)
```
with:
```kotlin
Text(
    "HOW DID THAT FEEL?",
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
)
```

The "(optional)" is dropped because the chips already read as optional —
nothing is preselected and the advance button is never disabled. The
label says what the control is for; it does not need to explain itself.

- [ ] **Step 3: Build and run tests**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, 55 tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt
git commit -m "feat(ui): make the rest timer a trace ring"
```

---

### Task 6: Progress screen

The largest visual change: bars become traces.

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/progress/ProgressScreen.kt`

**Interfaces:**
- Consumes: `TraceChart` (Task 3)
- Produces: nothing

- [ ] **Step 1: Replace the bar rows with a chart**

In `ExerciseProgressCard`, replace the entire `Column` containing the
per-week `Row`s (the `LinearProgressIndicator` block) with:

```kotlin
TraceChart(
    values = p.volumes,
    highlighted = highlighted,
    hollowAt = p.weeks.withIndex()
        .filter { (_, week) -> p.swapsByWeek.containsKey(week) }
        .map { it.index }
        .toSet(),
    modifier = Modifier.padding(top = 10.dp),
)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(
        "W${p.weeks.first()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "W${p.weeks.last()} · ${fmt(p.lastVolume)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

Add imports:
```kotlin
import com.luke.workouttracker.ui.theme.TraceChart
```

- [ ] **Step 2: Give the screen a subject**

`ExerciseProgressCard` gains a `highlighted` parameter so only the first
card draws in accent:

```kotlin
@Composable
private fun ExerciseProgressCard(p: ExerciseProgress, highlighted: Boolean) {
```

At the call site in `ProgressScreen`, change the `items(...)` to
`itemsIndexed(...)` and pass `highlighted = index == 0`. Update the
import from `androidx.compose.foundation.lazy.items` to
`androidx.compose.foundation.lazy.itemsIndexed`.

- [ ] **Step 3: Keep the swap footnote**

The existing footnote block stays exactly as it is. The hollow point
shows *that* a week was swapped; the footnote says *what* it was swapped
for. Do not remove it.

- [ ] **Step 4: Build and run tests**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, 55 tests passing.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/progress/ProgressScreen.kt
git commit -m "feat(ui): draw progress as traces instead of bars"
```

---

### Task 7: Remaining screens, guard, and verification

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/programs/ProgramListScreen.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/programs/ProgramEditorScreen.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/programs/DayEditorScreen.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/session/SessionPickerScreen.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/peak/PeakDayScreen.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: everything from Tasks 1-3
- Produces: nothing

- [ ] **Step 1: Apply numeric type to displayed figures**

In each screen above, find text that displays a weight, rep count,
volume or 1RM and apply:

```kotlin
style = MaterialTheme.typography.numeric,
```

for a prominent figure, or for inline figures within a sentence leave the
body style — a number inside prose does not need to be mono. The rule is:
a figure the user reads *as a value* gets `numeric`; a number inside a
sentence does not.

`PeakDayScreen`'s 1RM entries and `SessionPickerScreen`'s week numbers
are the clear candidates.

- [ ] **Step 2: Convert section headings to eyebrow labels**

Where a screen has a standalone section heading above a group — for
example "Theme" and "Bodyweight" in `SettingsScreen` — use:

```kotlin
style = MaterialTheme.typography.labelSmall,
color = MaterialTheme.colorScheme.onSurfaceVariant,
```
with the text uppercased. Do not convert titles that name a specific
item, such as a program name or an exercise name.

- [ ] **Step 3: Run the colour-literal guard**

```bash
grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v "ui/theme/Color.kt"
```
Expected: **no results**. Any hit is a hardcoded colour that will not
follow the theme between light and dark — move it into `Color.kt` or
replace it with a scheme role.

Also confirm dynamic colour is gone for good:
```bash
grep -rn "dynamicColor\|dynamicDarkColorScheme\|dynamicLightColorScheme" app/src/main/java --include=*.kt
```
Expected: no results.

- [ ] **Step 4: Build, test, and package**

```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest assembleDebug --console=plain
```
Expected: BUILD SUCCESSFUL, 55 tests passing.

Check the APK growth from the bundled fonts:
```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```
Expect roughly 900 KB more than before (was 17 MB).

- [ ] **Step 5: Manual verification**

Install from Android Studio. No schema changed, so data is not at risk
here — but the app must be checked in **both** themes.

1. Every screen in **dark**: nothing illegible, no leftover white
   surfaces, no invisible text.
2. Every screen in **light**: same.
3. The theme toggle in Settings switches modes and both are complete.
4. The active session set trace advances as sets are completed.
5. The rest timer ring sweeps and the elapsed time is readable at arm's
   length.
6. The progress chart draws correctly for: a program with **one** logged
   week (single point), a program where every week has identical volume
   (flat line), and a program with a swapped week (hollow point).
7. **The app looks the same on two devices with different wallpapers.**
   This is the direct test that dynamic colour is gone.
8. Set the system font size to its largest and confirm the active session
   does not clip.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/
git commit -m "feat(ui): apply the type scale across the remaining screens"
```

---

## Verification

| Check | Expectation |
|---|---|
| Unit tests | 55 passing, 0 failures |
| `grep "Color(0x"` outside `Color.kt` | no results |
| `grep "dynamicColor"` | no results |
| APK size | ~+900 KB versus 17 MB |
| Both themes, all 8 screens | legible, complete |

## Notes for the implementer

- **The trace is a two-state system.** Accent without a track is
  decoration and is wrong. If a screen wants accent alone, it probably
  wants `onSurfaceVariant` instead.
- **Do not adjust the token values by eye.** They were contrast-checked;
  `track` in particular looks too bright in isolation and is correct on
  device. If accent and track feel too close, lighten *accent*.
- **`dynamicColor` stays deleted.** Reintroducing it silently undoes the
  entire redesign.
- **No screenshot tests exist**, so every visual change is verified by
  looking. Budget for step 5 properly rather than rushing it.
- **Do not change layout or navigation.** If a screen looks wrong
  structurally, note it for later rather than restructuring here.
