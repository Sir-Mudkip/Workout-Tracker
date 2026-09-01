# Trace — Visual Redesign

**Date:** 2026-09-02
**Status:** Approved for planning
**Scope:** All eight screens, plus the theme, typography and shape system

## Problem

The app has no visual identity. Three things cause that, and only the
first is obvious:

1. **`Theme.kt` defines an amber palette that never renders.**
   `MainActivity.kt:27` calls `WorkoutTheme(mode = mode)` without
   `dynamicColor`, which defaults to `true`. On Android 12+ —
   every device that matters — `dynamicLightColorScheme` /
   `dynamicDarkColorScheme` replaces the entire scheme with colours
   sampled from the user's wallpaper. The chosen `0xFFE07A1F` and
   `0xFFF59E0B` are dead code in practice.
2. **No typography is defined.** `MaterialTheme` is constructed with a
   `colorScheme` only, so every text style is the Material 3 default in
   Roboto.
3. **No shape system is defined.** Every corner radius is whatever
   Material 3 defaults to for that component.

The result is an app that looks like the user's wallpaper, in the system
font, with default corners — indistinguishable from any other Compose
app built the same way.

## Direction

**Trace.** One continuous line is the visual language. It appears as set
progress, as the rest timer ring, and as the progress chart, replacing
the current bar chart. The line has two states, and that pairing is the
identity:

- **Accent** (`#FF9E2C`) — current, live, most recent
- **Track** (`#66718A`) — remaining, historical, secondary

A single bright accent on a dark ground is a common and rather tired
look. What distinguishes this one is that the accent is never merely
decorative: everywhere it appears, a track appears with it, and the pair
encodes done-versus-remaining or current-versus-past. If a proposed use
of the accent has no corresponding track, it is probably decoration and
should be reconsidered.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Dynamic colour | **Off** | It is the direct cause of the app having no identity. |
| Primary mode | **Dark** | Chosen by the user. A light variant ships from the same tokens. |
| Accent | **Amber `#FF9E2C`** | Survives a sunlit gym, reads as "load"/"caution" on equipment, and restores the intent of the app's original amber. |
| Display and body face | **Archivo** | Squarer terminals than Inter or Roboto, so headings read as built rather than neutral. |
| Figures | **IBM Plex Mono** | Tabular by construction — weights and timers must not shift width as they change. |
| Font delivery | **Bundled** | The app is offline-only. Downloadable Fonts would add a network dependency for a core visual. |
| Progress chart | **Traces, not bars** | The bar chart is the weakest screen and the direction's clearest application. |

## Tokens

### Colour — dark (primary)

| Token | Hex | Use |
|---|---|---|
| `ground` | `#0F1216` | Window background |
| `surface` | `#171B21` | Cards |
| `surfaceRaised` | `#1E232B` | Dialogs, menus, elevated sheets |
| `border` | `#272D36` | Every border and divider, 1px |
| `track` | `#66718A` | Trace track: remaining, historical |
| `accent` | `#FF9E2C` | Trace live: current, actionable |
| `onAccent` | `#1A1206` | Text and icons on accent |
| `text` | `#ECEEF1` | Primary text |
| `muted` | `#8A929E` | Secondary text, labels |
| `danger` | `#E5484D` | Destructive actions only |

The ground is a blue-leaning charcoal rather than black, so surfaces read
as lifted without needing heavy borders or shadows.

### Colour — light

Same roles, inverted. Not a second design — the same tokens.

| Token | Hex |
|---|---|
| `ground` | `#F4F5F7` |
| `surface` | `#FFFFFF` |
| `surfaceRaised` | `#FFFFFF` |
| `border` | `#E2E5E9` |
| `track` | `#8B96A6` |
| `accent` | `#A85C00` |
| `onAccent` | `#FFFFFF` |
| `text` | `#0F1216` |
| `muted` | `#5C646F` |
| `danger` | `#C62A2F` |

The light accent is darkened from `#FF9E2C` to `#A85C00`. The bright
amber does not carry sufficient contrast against white; `#A85C00` reaches
5.0:1 both as accent text on white and as white text on a filled accent
button, so one value serves both roles.

### Material 3 mapping

Tokens map onto the Material 3 colour roles so existing components pick
them up without per-screen rewrites. This is what keeps the work
proportionate: `Button`, `Card`, `FilterChip`, `OutlinedTextField`,
`TopAppBar` and `AlertDialog` are all already in use and all read from
the scheme.

| M3 role | Dark | Light |
|---|---|---|
| `primary` | `accent` | `accent` |
| `onPrimary` | `onAccent` | `onAccent` |
| `background` | `ground` | `ground` |
| `onBackground` | `text` | `text` |
| `surface` | `surface` | `surface` |
| `onSurface` | `text` | `text` |
| `surfaceVariant` | `surfaceRaised` | `surfaceRaised` |
| `onSurfaceVariant` | `muted` | `muted` |
| `outline` | `border` | `border` |
| `outlineVariant` | `track` | `track` |
| `error` | `danger` | `danger` |

`track` is exposed through `outlineVariant` so trace drawing can read it
from `MaterialTheme.colorScheme` rather than importing a constant.

### Type

Two families, bundled as `res/font/`:

- **Archivo** — variable weight axis. `minSdk = 26`, which is exactly the
  floor for `FontVariation` weight settings, so a single file covers
  every weight.
- **IBM Plex Mono** — Regular and SemiBold statics.

| Style | Face | Size / weight | Use |
|---|---|---|---|
| `displayLarge` | Plex Mono SemiBold | 42sp | Rest timer |
| `headlineSmall` | Archivo 800 | 20sp, -0.015em | Exercise name in session |
| `titleMedium` | Archivo 700 | 16sp | Card titles, program names |
| `titleSmall` | Archivo 700 | 13sp | Section headings |
| `bodyMedium` | Archivo 400 | 14sp | Body copy |
| `bodySmall` | Archivo 400 | 12sp | Secondary detail |
| `labelSmall` | Archivo 600 | 9sp, 0.18em, uppercase | Eyebrow labels |
| `numeric` | Plex Mono SemiBold | 27sp, -0.03em | Reps, weight, volume |

`numeric` is not a Material 3 style. It is added as an extension on
`MaterialTheme.typography` so figures can be styled consistently without
abusing an unrelated role.

Every figure a user reads or edits — reps, weight, volume, timers,
percentages — uses Plex Mono. Every word uses Archivo. The split is
strict, and it is what makes the interface feel instrumented.

### Shape

Three steps, deliberately not one radius everywhere:

| Token | Radius | Applied to |
|---|---|---|
| `large` | 14dp | Cards, dialogs |
| `medium` | 10dp | Buttons, text fields |
| `small` | 6dp | Chips at rest |
| `pill` | 50% | Filter chips, difficulty chips |

### Spacing and stroke

- 4dp grid. Card padding 16dp, screen padding 16dp, item spacing 12dp.
- One border weight: **1dp**, `border`.
- One trace weight: **2.5dp**, round caps and joins.

### Contrast

Every token pair was checked rather than assumed, and two failed the
first draft:

| Pair | Ratio | |
|---|---|---|
| `accent` on `surface` (dark) | 8.38:1 | |
| `text` on `ground` (dark) | 16.15:1 | |
| `muted` on `surface` (dark) | 5.50:1 | |
| `onAccent` on `accent` (dark) | 8.98:1 | |
| `track` on `surface` (dark) | 3.53:1 | amended from `#3A4250`, which was 1.71:1 |
| `accent` on `surface` (light) | 5.00:1 | amended from `#C26A00`, which was 3.92:1 |
| `onAccent` on `accent` (light) | 5.00:1 | |
| `track` on `surface` (light) | 3.00:1 | |

`track` is held to 3:1 rather than treated as decoration because in the
progress chart it draws **historical data lines**. Those carry
information, so they must be perceivable — WCAG 1.4.11. The original
`#3A4250` looked right in a mockup and would have made older weeks
effectively invisible on a phone in daylight.

Raising `track` narrows the gap between accent and track to 2.37:1, so
the two-state system leans more on hue and less on lightness than the
mockups implied. It still reads clearly — warm amber against cool grey —
but if the distinction feels weak in the build, the fix is to lighten
`accent`, never to darken `track` back.

### Motion

Deliberately minimal. Two moments only:

- The set progress trace animates its length on completing a set
  (300ms, `FastOutSlowInEasing`).
- The rest ring sweeps continuously while resting.

No screen transitions, no staggered list entrances. The app is used
mid-workout; animation that delays reading a number is a cost, not a
feature.

## Screens

### Active session — the most changed

- Exercise name in Archivo 800 at 20sp.
- A **set progress trace** under the target line: accent for sets
  completed, track for remaining, with a dot at the current position.
- Reps and weight fields use `numeric` — 27sp Plex Mono, readable at
  arm's length.
- "Complete set" is a filled accent button; "Swap exercise" is a text
  button in accent.
- Logged sets list in Plex Mono, difficulty in accent.

### Rest timer dialog

- The trace closed into a **ring**, sweeping while resting, with elapsed
  time at 42sp Plex Mono in the centre.
- Difficulty chips below as outlined pills that fill with accent when
  selected, so optional reads as optional.

### Progress — second most changed

- Each exercise card draws a **polyline trace** instead of the current
  `LinearProgressIndicator` bars.
- The most recent point carries a filled dot.
- A **swapped week is a hollow point** on the line — the swap is visible
  in the chart itself, replacing the `W3*` marker and footnote. The
  footnote text stays, since the hollow point says *that* something was
  substituted but not *what*.
- Cards below the fold draw their trace in `track` rather than accent, so
  the screen has one subject rather than nine equal charts.

### Program list, program editor, day editor, session picker, peak day, settings

These inherit from the theme and need no bespoke drawing. Changes are
limited to: `labelSmall` eyebrows where a section heading exists today,
`numeric` for any displayed figure, and confirming that nothing hardcodes
a colour outside the scheme.

## Implementation shape

The work concentrates in `ui/theme/`, which is why it is worth doing
properly there rather than screen by screen:

```
ui/theme/
  Color.kt      tokens + the two ColorSchemes
  Type.kt       font families + Typography + the numeric extension
  Shape.kt      the Shapes object
  Theme.kt      WorkoutTheme, dynamicColor removed
  Trace.kt      TraceLine, TraceRing, TraceChart composables
res/font/
  archivo_variable.ttf
  ibm_plex_mono_regular.ttf
  ibm_plex_mono_semibold.ttf
```

`Trace.kt` holds the three drawing primitives so the trace is defined
once and reused, rather than each screen drawing its own Canvas.

`WorkoutTheme`'s `dynamicColor` parameter is **removed entirely**, not
defaulted to `false`. Leaving it in place invites someone to switch it
back on and silently undo the redesign.

## Testing

There is nothing here that unit tests can meaningfully cover — the output
is pixels, and this codebase has no Compose UI tests or screenshot
harness. Adding one for a redesign would be a larger project than the
redesign.

What can be checked mechanically:

- No colour literal outside `Color.kt`. Enforced by grep, run in the
  final task:
  ```bash
  grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v "ui/theme/Color.kt"
  ```
  Expect no results.
- The existing 55 unit tests still pass, since no logic changes.

Everything else is manual, and the checks that matter are:

1. Both themes, every screen, nothing illegible.
2. The theme toggle in Settings still switches modes.
3. Text at the largest system font size does not clip.
4. The app looks identical on two devices with different wallpapers —
   the direct test that dynamic colour is gone.

## Out of scope

- **No layout or navigation changes.** Same screens, same routes, same
  flows. This is a visual pass.
- **No new features.** The percentage-of-best idea from direction 4 is
  not part of this.
- **No icon or splash change.** The launcher icon stays as it is.
- **No screenshot testing harness.**

## Risks

**Dark-only is punishing outdoors.** The light variant is specified and
must actually be built, not deferred. A gym with skylights defeats a dark
UI, and the Settings toggle already exists and will be exercised.

**Bundled fonts add roughly 900 KB to the APK.** Acceptable for a
self-updating app distributed as a direct download, and the alternative —
Downloadable Fonts — introduces a network dependency into an app whose
entire premise is working offline.

**The trace chart is hand-drawn Canvas.** The existing bar chart is
`LinearProgressIndicator`, which handles its own edge cases. A polyline
must handle a single data point, all-equal values, and zero volume
without dividing by zero or drawing off-canvas. These are specified in
the plan as explicit cases because they will not be caught by tests.

**No screenshot coverage means visual regressions are invisible.** A
later change to `Color.kt` or `Type.kt` affects all eight screens with no
automated signal. The grep above is the only mechanical guard, and it
only catches colour literals, not bad ones.
