# Architecture

A single-activity Jetpack Compose app with no backend. Everything is
local: Room for storage, `SharedPreferences` for two scalar settings, and
one outbound network call to check GitHub for updates.

## Layers

```
ui/<feature>/     Composable screen + its @HiltViewModel, in one file
    |
data/repo/        Repositories — suspend functions and Flows
    |
data/db/dao/      Room DAOs
    |
data/db/entities/ Room entities
```

`di/AppModule.kt` provides the database and every DAO. Repositories are
`@Singleton` classes with `@Inject constructor`, so they are not listed
in the module.

## Screen and ViewModel in one file

Each screen keeps its ViewModel, its Composables, and any state class in
a **single file** named after the screen — `ui/session/ActiveSessionScreen.kt`
holds `ActiveSessionState`, `ActiveSessionViewModel`, `ActiveSessionScreen`,
and the private Composables it uses.

This is deliberate and worth preserving: a screen's state shape, its
transitions, and its rendering are read together far more often than
they are read apart, and the files stay small enough to hold in one
view. Follow it for new screens.

The exception is anything **shared between screens**, which gets its own
file — `ui/library/ExercisePicker.kt` is used by both the day editor and
the active session, so it lives on its own.

## State

ViewModels expose `StateFlow`, screens collect with `collectAsState()`.
Two shapes appear, and both are correct in their place:

- **Derived from the database** — `stateIn(viewModelScope, SharingStarted.Eagerly, initial)`
  over a repository `Flow`. Used where the database is the source of
  truth and the screen should follow it, as with `DayEditorViewModel.exercises`.
- **Explicitly held** — a `MutableStateFlow` the ViewModel updates itself.
  Used where the screen has position or progress the database does not
  model, as with `ActiveSessionViewModel._state`, which tracks which set
  the user is on.

`ActiveSessionState` is worth reading as the example of the second
shape: it is an immutable snapshot with computed properties
(`currentExercise`, `isLastSetOfSession`) and pure functions
(`prefillWeight()`, `displayName()`) rather than a bag of mutable
fields. That is what makes it unit-testable without a database or an
emulator — see [`testing.md`](./testing.md).

## Navigation

`ui/nav/Routes.kt` holds route templates as constants alongside builder
functions:

```kotlin
const val DayEditor = "program/{programId}/day/{dayId}"
fun dayEditor(programId: Long, dayId: Long) = "program/$programId/day/$dayId"
```

The constant is what `AppNavHost` registers; the function is what call
sites use. Keep both in step when adding a route — a hand-built path
string at a call site is how they drift.

Ids arrive in ViewModels through `SavedStateHandle` and are read with
`checkNotNull(handle["programId"])`. A missing id is a programming
error, not a runtime condition, so failing loudly is correct.

## Screens

| Route | File | Purpose |
|---|---|---|
| `programs` | `ui/programs/ProgramListScreen.kt` | Program list, create, JSON import/export |
| `program/{id}` | `ui/programs/ProgramEditorScreen.kt` | Rename, days, reorder days |
| `program/{id}/day/{dayId}` | `ui/programs/DayEditorScreen.kt` | Add/reorder/delete exercises and their sets |
| `session/pick/{programId}` | `ui/session/SessionPickerScreen.kt` | Choose week and day to train |
| `session/active/{sessionId}` | `ui/session/ActiveSessionScreen.kt` | Log sets, rest timer, difficulty, swap |
| `program/{id}/progress` | `ui/progress/ProgressScreen.kt` | Per-exercise weekly volume bars |
| `program/{id}/peak` | `ui/peak/PeakDayScreen.kt` | Start/end 1RM per named lift |
| `settings` | `ui/settings/SettingsScreen.kt` | Bodyweight, theme, update check |

## Repositories

Three, split by aggregate rather than by table:

- **`ProgramRepository`** — programs, days, exercises, sets. Owns the
  ordering arithmetic and JSON import. Multi-table writes go through
  `db.withTransaction { }`; reordering and deletion both re-pack
  ordering columns and must not half-apply.
- **`SessionRepository`** — sessions, set logs, swaps, volume queries.
- **`PeakRepository`** — 1RM entries. Small and self-contained.
- **`ExerciseLibraryRepository`** — merges the shipped exercise name list
  with user-saved names.

Repositories return entities and Flows directly. There is no separate
domain model, and adding one would not currently pay for itself — the
entities are already plain data classes with no Room types leaking into
them.

## Pure logic is extracted

Where behaviour can be expressed without Android or Room, it lives in a
plain Kotlin file and is unit tested:

- `data/repo/ExerciseOrdering.kt` — index arithmetic for reordering
- `data/library/ExerciseSearch.kt` — name search and merge
- `data/library/ExerciseLibrary.kt` — the shipped name list
- `data/db/entities/SetDifficulty.kt` — the 1-6 exertion scale

This is the main testing lever in the project. See
[`testing.md`](./testing.md) for what it does and does not buy.

## Settings

`data/prefs/BodyweightPrefs.kt` and `ThemePrefs.kt` wrap
`SharedPreferences` and expose a `StateFlow`. Two scalars do not justify
DataStore, and bodyweight in particular is read on a hot path — the
volume query takes it as a parameter, see [`data-model.md`](./data-model.md).

## Updates

`data/updates/UpdateChecker.kt` queries the GitHub releases API,
compares against `BuildConfig`, downloads the APK, and hands it to the
package installer through a `FileProvider`. This is the only network
call the app makes and the only reason it holds the internet permission.

The app is distributed as an APK from GitHub releases rather than a
store, which is why it updates itself.
