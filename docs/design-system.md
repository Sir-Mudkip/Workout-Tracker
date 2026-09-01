# Design system

The app's visual identity is called **Trace**. Every token lives in
`ui/theme/`; nothing visual should be defined anywhere else.

## Why dynamic colour is gone

`WorkoutTheme` has no `dynamicColor` parameter, and reintroducing one
undoes the entire design.

Until v0.3.0 it defaulted to `true`, so on Android 12+ Material You
replaced the whole scheme with colours sampled from the user's
wallpaper. The palette written in `Theme.kt` never rendered on any modern
device — the app looked like whatever wallpaper it sat on, in Roboto,
with default corner radii. The parameter was removed rather than
defaulted to `false` so it cannot be switched back on casually.

## The trace

One continuous line is the visual language, and it always has **two
states**:

- **accent** — current, live, most recent
- **track** — remaining, historical, secondary

That pairing is the identity, not the accent colour. A single bright
accent on a dark ground is a common look; what makes this one specific is
that the accent never appears alone. It is the filled part of a set
progress bar, the swept part of the rest ring, the highlighted line among
dimmed ones.

**If a proposed use of accent has no corresponding track, it is
decoration and is wrong.** Use `onSurfaceVariant` instead.

Three primitives in `ui/theme/Trace.kt` — `TraceProgress`, `TraceRing`,
`TraceChart` — are the only places the line is drawn. Screens compose
them; they do not draw their own `Canvas`.

## Tokens

`ui/theme/Color.kt` is the only file permitted to contain a colour
literal. Enforced by grep:

```bash
grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v "ui/theme/Color.kt"
```

| Token | Dark | Light | Role |
|---|---|---|---|
| `ground` | `#0F1216` | `#F4F5F7` | window background |
| `surface` | `#171B21` | `#FFFFFF` | cards |
| `surfaceRaised` | `#1E232B` | `#FFFFFF` | dialogs, menus |
| `border` | `#272D36` | `#E2E5E9` | every border and divider |
| `track` | `#66718A` | `#8B96A6` | trace: remaining, historical |
| `accent` | `#FF9E2C` | `#A85C00` | trace: current, actionable |
| `onAccent` | `#1A1206` | `#FFFFFF` | on accent |
| `text` | `#ECEEF1` | `#0F1216` | primary text |
| `muted` | `#8A929E` | `#5C646F` | secondary text, labels |
| `danger` | `#E5484D` | `#C62A2F` | destructive only |

The ground is a blue-leaning charcoal rather than black, so surfaces read
as lifted without heavy borders or shadows.

Tokens map onto Material 3 roles, which is what makes the system cheap to
apply: `Button`, `Card`, `FilterChip`, `OutlinedTextField`, `TopAppBar`
and `AlertDialog` all read from the scheme and restyle themselves.
`outlineVariant` carries `track` so trace drawing reads it from
`MaterialTheme.colorScheme` rather than importing the token object.

### Contrast

Values are contrast-checked, not chosen by eye. Two failed a first pass
and were amended:

- `track` was `#3A4250`, which is **1.71:1** on a card — historical
  traces would have been invisible in daylight. It is held to 3:1
  because those lines carry data (WCAG 1.4.11), not because they are
  decorative.
- The light accent was `#C26A00` at **3.92:1** on white, failing normal
  text. `#A85C00` reaches 5.0:1 both as text and behind white text.

Raising `track` narrowed the accent-to-track gap to 2.37:1, so the two
states lean more on hue than on lightness. **If they ever feel too close,
lighten `accent` — never darken `track` back.**

## Type

Two families, bundled in `res/font/` rather than downloaded, because the
app works entirely offline.

- **Archivo** — display and body, as a single variable font. `minSdk = 26`
  is exactly the floor for `FontVariation` weight axes. Requires
  `@OptIn(ExperimentalTextApi::class)`.
- **IBM Plex Mono** — every figure. Tabular by construction, so numbers
  do not shift width as they change.

The split is strict and load-bearing: **every figure a user reads as a
value uses `MaterialTheme.typography.numeric`; every word uses Archivo.**
A number inside a sentence stays in body type. That rule is what makes
the interface feel instrumented rather than merely dark.

`numeric` is not a Material 3 role — it is an extension property on
`Typography` in `Type.kt`, so figures are styled consistently without
abusing an unrelated style.

## Shape, spacing, motion

Three radii, deliberately not one everywhere: 14dp cards, 10dp controls,
6dp chips at rest, full-round pills. One border weight (1dp) and one
trace weight (2.5dp).

Motion is limited to two moments: the set trace animating on completion
(300ms), and the rest ring sweeping. There are no screen transitions and
no staggered list entrances — the app is used mid-workout, and animation
that delays reading a number is a cost.

## Adding a screen

1. Take colours from `MaterialTheme.colorScheme`, never a literal.
2. Take text styles from `MaterialTheme.typography`; use `numeric` for
   figures.
3. Section headings are `labelSmall`, uppercased, in `onSurfaceVariant`.
   Titles that name a specific item — a program, an exercise — are not
   headings and stay in title styles.
4. If you want a line, use a `Trace*` primitive.
5. Check both themes before committing. There are no screenshot tests, so
   looking is the only verification that exists.
