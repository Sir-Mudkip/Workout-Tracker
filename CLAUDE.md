# Workout Tracker

A local-only Android app for authoring multi-week training programs,
logging sets during a workout, and charting per-exercise volume.

> `AGENTS.md` is a symlink to this file — one set of instructions for every agent.
>
> Rules live in this file; the reasoning behind them lives in [`docs/`](docs/README.md).

## Layout

| Path | Purpose |
|---|---|
| `app/src/main/java/com/luke/workouttracker/ui/<feature>/` | One file per screen: Composables, its `@HiltViewModel`, and its state class together |
| `.../data/db/entities/` | Room entities |
| `.../data/db/dao/` | Room DAOs |
| `.../data/repo/` | Repositories, plus pure logic extracted for testing |
| `.../data/library/` | Shipped exercise name list and search |
| `.../data/prefs/` | `SharedPreferences` wrappers exposing `StateFlow` |
| `.../di/AppModule.kt` | Hilt module: database and DAO providers |
| `app/src/test/` | JVM unit tests — the only tests that exist |
| `docs/` | Maintainer documentation: the *why* behind these rules |
| `samples/` | Example program JSON |

## Documentation

Design documentation lives in `docs/` — see [`docs/README.md`](docs/README.md)
for the index and a table of which page to update for a given change.

| Topic | Page |
|---|---|
| Layers, screens, navigation, repositories | [`docs/architecture.md`](docs/architecture.md) |
| Entities, weight and bodyweight semantics, ordering | [`docs/data-model.md`](docs/data-model.md) |
| Migrations and the destructive fallback | [`docs/database.md`](docs/database.md) |
| The Trace visual system and its tokens | [`docs/design-system.md`](docs/design-system.md) |
| Toolchain, Gradle, emulator, releases | [`docs/building.md`](docs/building.md) |
| What is tested and what is not | [`docs/testing.md`](docs/testing.md) |
| Program import/export schema | [`docs/json-format.md`](docs/json-format.md) |

Rules live here; the reasoning lives in `docs/`. When you change a design
decision, update the relevant page in the same commit.

## Database changes are the dangerous ones

`AppModule.provideDatabase` uses `.fallbackToDestructiveMigration()`
(`di/AppModule.kt:33`). A migration that is written but **not registered**
does not error — Room drops every table and the user loses their entire
training history, silently.

- Every migration goes in **two** places: defined in `AppDatabase.Companion`
  with `version` bumped, **and** added to `addMigrations(...)` in `AppModule`.
- Verify before committing:
  ```bash
  grep -n "MIGRATION_5_6" \
    app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
    app/src/main/java/com/luke/workouttracker/di/AppModule.kt
  ```
  Two hits, one per file.
- New columns must be nullable or defaulted; new tables are created empty.
  No migration to date rewrites existing data — keep it that way.
- **Install over an existing install** to verify. A fresh install never runs
  the migration and proves nothing.

There is no Room test harness. Full detail: [`docs/database.md`](docs/database.md).

## Both volume queries must change together

`SessionDao.observeWeeklyVolume` and `SessionDao.weeklyVolume` are the same
SQL, one returning a `Flow` and one a `List`. Nothing enforces that they
match. Change both in the same edit, then confirm:

```bash
grep -c "COALESCE(sw.isBodyweight, pe.isBodyweight)" \
  app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
```

Expect `2`. Reasoning: [`docs/testing.md`](docs/testing.md).

## Visual changes

- **No colour literals outside `ui/theme/Color.kt`.** Take colours from
  `MaterialTheme.colorScheme` and text styles from `MaterialTheme.typography`.
  ```bash
  grep -rn "Color(0x" app/src/main/java --include=*.kt | grep -v "ui/theme/Color.kt"
  ```
- **Figures use `MaterialTheme.typography.numeric`** (IBM Plex Mono); words use
  Archivo. A number inside a sentence stays in body type.
- **The trace accent never appears without its track.** Accent alone is
  decoration — use `onSurfaceVariant`. Draw lines with the `Trace*` primitives,
  not a bespoke `Canvas`.
- **Never reintroduce `dynamicColor`.** It silently replaces the whole scheme
  with wallpaper colours and undoes the design.
- **Check both themes.** There are no screenshot tests; looking is the only
  verification.

Tokens and reasoning: [`docs/design-system.md`](docs/design-system.md).

## Testing

- **Extract pure logic and unit test it.** Anything expressible without
  Android or Room goes in a plain Kotlin file with tests —
  `ExerciseOrdering`, `ExerciseSearch`, `SetDifficulty`, and the computed
  properties on `ActiveSessionState` are the existing examples.
- Room, Compose and repositories are **not** tested and adding a harness for
  one feature is out of scope — say what you verified by hand instead.
- Do not weaken an assertion to make a test pass.

```bash
./gradlew testDebugUnitTest
```

What is and is not covered: [`docs/testing.md`](docs/testing.md).

## Code conventions

- **One file per screen.** Composables, `@HiltViewModel`, and state class
  together, named after the screen. Split out only what is shared between
  screens (`ui/library/ExercisePicker.kt`).
- **State is immutable.** ViewModels expose `StateFlow`; screens collect with
  `collectAsState()`. Prefer computed properties and pure functions on a state
  data class over mutable fields — that is what makes them testable.
- **Multi-table writes go in `db.withTransaction { }`.** Reordering and
  deletion re-pack ordering columns and must not half-apply.
- **Routes live in `Routes.kt`** as a constant plus a builder function. Never
  hand-build a route string at a call site.
- **Follow the surrounding style.** The codebase uses trailing commas, named
  arguments for anything non-obvious, and KDoc on non-trivial functions only.
- Exercise names spell equipment out — `Dumbbell`, never `DB`. Enforced for
  the shipped list by `ExerciseLibraryTest`.

## Build

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK
./gradlew installDebug        # install to device or emulator
```

- **The Gradle wrapper is committed** and pins Gradle 8.7. Do not remove it — the release workflow runs `./gradlew` directly.
- **Use JDK 21, not Studio's bundled JBR 25** — Gradle 8.7 rejects Java 25 and
  reports only the version number as the error.
- **Install schema changes from Android Studio**, not the CLI. A CLI install
  over a Studio build fails on signature mismatch, and the only workaround
  (`adb uninstall`) deletes the database you were about to verify.

Emulator and toolchain problems: [`docs/building.md`](docs/building.md).

## Conventions

- **Conventional commits**: `<type>[scope]: <description>`, types
  `feat: fix: docs: chore: build: ci: refactor: test:`.
- AI agents disclose themselves in a commit footer:
  `Assisted-by: [Model] via [Tool]`.
- Be surgical — prefer the smallest change that works.
- **Do not add large comment blocks to explain design decisions.** Put the
  reasoning in the relevant `docs/` page and update it in the same commit.
- Update the docs page in the same commit as the change it describes. A rule
  here without its reasoning in `docs/` will be undone by whoever disagrees
  with it next.

### Leave alone unless asked

`.github/workflows/release.yml`, `gradle/libs.versions.toml`,
`app/proguard-rules.pro`, `.gitignore`.

Treat with caution: `AppDatabase.kt` and `AppModule.kt` (see above),
`SessionDao.kt` (the volume queries).
