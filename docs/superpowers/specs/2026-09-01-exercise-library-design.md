# Exercise Library — Design

**Date:** 2026-09-01
**Status:** Approved for planning
**Feature:** #3 of 4 in the current batch (after set-difficulty and exercise reordering, before mid-session exercise swap)

## Problem

Creating a program means typing every exercise name by hand into
`AddExerciseDialog`. This is slow, and it produces inconsistent names across
programs — `Bench Press` in one, `bench press` or `BB Bench` in another. The
progress screen groups set logs by `plannedExerciseId`, so inconsistent naming
does not currently corrupt data, but it does make programs harder to read and
compare.

A library of known exercise names, searchable at the point of creation, fixes
both. It is also a prerequisite for feature #4 (swap an exercise mid-session),
which needs a picker to choose the replacement.

## Decisions

Settled during brainstorming, recorded here because each one narrows scope:

| Decision | Choice | Rationale |
|---|---|---|
| Metadata per exercise | **Name only** | No muscle group or equipment fields. Adding columns later is a routine migration; guessing at taxonomy now is speculative. |
| Unknown names | **Free-type, offer to save** | Typing a name not in the library uses it immediately and offers to add it. The library never blocks logging. |
| Management screen | **None** | The library grows only via the save checkbox. No rename or delete UI. |
| Storage | **Stock list in code, custom names in a table** | Stock exercises are shipped content that changes with releases; custom names are user data that must survive them. Each lives where its lifecycle fits. |
| Save checkbox default | **Checked** | A library that does not grow is the failure mode that matters. See Risks. |

## Architecture

Two sources of names, merged at read time.

```
ExerciseLibrary.kt          custom_exercises table
(stock, ~140 names)         (user-saved names)
        |                            |
        +-------------+--------------+
                      |
          ExerciseLibraryRepository
           observeNames(): Flow<List<String>>
           save(name: String)
                      |
              filterExercises()   <- pure, tested
                      |
                ExercisePicker    <- shared composable
                      |
        +-------------+--------------+
        |                            |
  AddExerciseDialog          SwapExerciseDialog
  (this feature)             (feature #4)
```

The library supplies a `String` at creation time. It is **not** a foreign key.
`PlannedExercise.name` remains a plain column, unchanged. Nothing about existing
programs or set logs is affected, and feature #4 can swap an exercise by writing
a different name.

### Schema

One new table:

```kotlin
@Entity(
    tableName = "custom_exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
```

`MIGRATION_4_5` creates the table empty. Nothing is seeded into it, so the
migration cannot conflict with existing data.

```sql
CREATE TABLE IF NOT EXISTS custom_exercises (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS index_custom_exercises_name
    ON custom_exercises(name);
```

Database version 4 -> 5. The migration must be registered in **both**
`AppDatabase.Companion` and `AppModule.provideDatabase`'s `addMigrations(...)`
call. `fallbackToDestructiveMigration()` is active, so an unregistered migration
silently deletes all user data rather than failing.

The unique index makes duplicate saves harmless. Inserts use
`OnConflictStrategy.IGNORE`.

### Naming convention

Stock names follow one pattern:

```
[Modifier] + [Equipment] + [Movement]
```

The modifier is grip, stance, angle, or bar position, and appears **only when it
distinguishes a variant that trains differently**:

- `High Bar Back Squat` / `Low Bar Back Squat` — bar position changes quad/hip balance
- `Close Grip Barbell Bench Press` — grip width shifts chest to triceps
- `Neutral Grip Lat Pulldown` / `Wide Grip Lat Pulldown` — grip changes lat emphasis
- `Incline Dumbbell Press` / `Flat Dumbbell Press` — angle changes upper/mid chest

Two rules:

1. **No abbreviations.** `Dumbbell`, never `DB`. Search is predictable when
   names are spelled out.
2. **The unmodified name is the conventional default.** `Barbell Bench Press` is
   flat and medium-grip. There is no `Flat Medium Grip Barbell Bench Press`.
   Without this rule every entry accumulates qualifiers.

### Search

```kotlin
fun filterExercises(all: List<String>, query: String): List<String>
```

- Case-insensitive.
- Query is split on whitespace; **every token must match** somewhere in the name.
  `lat pull` matches `Neutral Grip Lat Pulldown`.
- Results rank names where a token matches at a word boundary above names where
  it matches mid-word. `bench` puts `Bench Press` above `Close Grip Barbell Bench Press`.
- Blank query returns the full list.
- Capped at **8 results** so the dialog stays shorter than the keyboard.
- No matches returns an empty list, which is what triggers the `+ Use "..."`
  fallback in the UI.

Merging stock and custom names is also pure and tested: case-insensitive dedupe,
custom entries taking precedence, sorted alphabetically.

## Components

### `data/library/ExerciseLibrary.kt`
The stock list as `val STOCK_EXERCISES: List<String>`, grouped by comment for
readability. The app treats it as one flat list. Editing it is a one-line code
change requiring no migration.

### `data/library/ExerciseSearch.kt`
`filterExercises` and the merge function. Pure, no Android dependencies, fully
unit tested.

### `data/db/dao/ExerciseLibraryDao.kt`
`observeCustom(): Flow<List<CustomExercise>>`, and
`insert(exercise: CustomExercise)` with `OnConflictStrategy.IGNORE`.

### `data/repo/ExerciseLibraryRepository.kt`
Merges stock and custom into `observeNames(): Flow<List<String>>`. Exposes
`save(name: String)`.

### `ui/library/ExercisePicker.kt`
The shared composable. A text field, up to 8 matching suggestions beneath it,
and — when nothing matches — a `+ Use "<query>"` row with an
`also save to library` checkbox, checked by default.

Reports two values to its caller: the chosen name, and whether to save it.
Selecting an existing suggestion reports `save = false`.

### `ui/programs/DayEditorScreen.kt`
`AddExerciseDialog`'s plain `Name` field is replaced by `ExercisePicker`. The
bodyweight checkbox, starting weight field, and set rows are unchanged.

The confirm callback gains one parameter:

```kotlin
onConfirm: (
    name: String,
    startingWeight: Double,
    sets: List<Pair<Int, Double?>>,
    isBodyweight: Boolean,
    saveToLibrary: Boolean,
) -> Unit
```

`DayEditorViewModel.addExercise` takes the same flag and calls
`library.save(name)` before `repo.addExercise(...)`.

This is the only integration point — `addExercise` has no other caller.

## Seed content

140 entries. Grouped by comment in the source file only; the app
sees one flat list.

**Squat / quad** — High Bar Back Squat, Low Bar Back Squat, Front Squat, Safety
Bar Squat, Goblet Squat, Bulgarian Split Squat, Walking Lunge, Reverse Lunge,
Step Up, Hack Squat, Leg Press, Pendulum Squat, Sissy Squat, Leg Extension

**Hinge / posterior chain** — Conventional Deadlift, Sumo Deadlift, Romanian
Deadlift, Stiff Leg Deadlift, Trap Bar Deadlift, Deficit Deadlift, Rack Pull,
Good Morning, Barbell Hip Thrust, Single Leg Hip Thrust, Glute Bridge, Back
Extension, Reverse Hyperextension, Seated Leg Curl, Lying Leg Curl, Nordic
Curl, Cable Pull Through, Kettlebell Swing

**Horizontal push** — Barbell Bench Press, Close Grip Barbell Bench Press, Wide
Grip Barbell Bench Press, Incline Barbell Bench Press, Decline Barbell Bench
Press, Flat Dumbbell Press, Incline Dumbbell Press, Decline Dumbbell Press,
Neutral Grip Dumbbell Press, Machine Chest Press, Incline Machine Chest Press,
Smith Machine Bench Press, Push Up, Weighted Push Up, Deficit Push Up, Dip,
Weighted Dip, Cable Fly, Low To High Cable Fly, High To Low Cable Fly, Pec Deck

**Vertical push** — Standing Overhead Press, Seated Overhead Press, Seated
Dumbbell Shoulder Press, Standing Dumbbell Shoulder Press, Neutral Grip Dumbbell
Shoulder Press, Arnold Press, Push Press, Machine Shoulder Press, Landmine Press

**Horizontal pull** — Barbell Row, Pendlay Row, Yates Row, Single Arm Dumbbell
Row, Chest Supported Dumbbell Row, Chest Supported T Bar Row, T Bar Row, Seated
Cable Row, Neutral Grip Seated Cable Row, Wide Grip Seated Cable Row, Machine
Row, Inverted Row, Meadows Row, Kroc Row

**Vertical pull** — Pull Up, Weighted Pull Up, Chin Up, Weighted Chin Up,
Neutral Grip Pull Up, Wide Grip Pull Up, Lat Pulldown, Wide Grip Lat Pulldown,
Neutral Grip Lat Pulldown, Close Grip Lat Pulldown, Reverse Grip Lat Pulldown,
Single Arm Lat Pulldown, Straight Arm Pulldown, Machine Pullover

**Shoulders / delts** — Dumbbell Lateral Raise, Cable Lateral Raise, Machine
Lateral Raise, Leaning Cable Lateral Raise, Front Raise, Rear Delt Fly, Cable
Rear Delt Fly, Reverse Pec Deck, Face Pull, Upright Row, Barbell Shrug,
Dumbbell Shrug

**Biceps** — Barbell Curl, EZ Bar Curl, Dumbbell Curl, Alternating Dumbbell
Curl, Hammer Curl, Incline Dumbbell Curl, Preacher Curl, Spider Curl, Cable
Curl, Concentration Curl, Reverse Curl

**Triceps** — Skull Crusher, EZ Bar Skull Crusher, Overhead Cable Extension,
Overhead Dumbbell Extension, Cable Triceps Pushdown, Rope Triceps Pushdown,
Reverse Grip Pushdown, Triceps Kickback, JM Press, Bench Dip

**Core** — Plank, Side Plank, Hanging Leg Raise, Hanging Knee Raise, Cable
Crunch, Machine Crunch, Ab Wheel Rollout, Russian Twist, Pallof Press, Decline
Sit Up, Dead Bug, Toes To Bar

**Calves** — Standing Calf Raise, Seated Calf Raise, Leg Press Calf Raise, Smith
Machine Calf Raise, Single Leg Calf Raise

Note: `Close Grip Barbell Bench Press` is listed under horizontal push only,
though it is a common triceps builder. Names appear once.

## Testing

Consistent with the existing suite: pure logic is extracted and tested; the Room
write path is not, matching how `restAfterMs` and the other repository methods
are handled.

`ExerciseSearchTest` covers:

- token matching — `lat pull` finds `Neutral Grip Lat Pulldown`
- case insensitivity in both name and query
- blank query returns the full list
- word-boundary ranking — `bench` puts `Bench Press` first
- the 8-result cap
- no matches returns an empty list
- merge dedupes case-insensitively, custom takes precedence, result is sorted

Manual verification after implementation:

1. Add an exercise from a suggestion — name fills, nothing new is saved.
2. Type an unknown name with the checkbox checked — exercise is created and the
   name appears in later searches.
3. Type an unknown name with the checkbox cleared — exercise is created, name
   does not appear later.
4. Existing programs and set logs are intact after the migration.

## Out of scope

- **JSON import does not populate the library.** Importing a program would add
  every name it contains, including whatever its author called things, defeating
  the naming convention. Import behaviour is otherwise unchanged.
- **No management screen.** No rename, no delete.
- **No metadata.** No muscle group, equipment, or category fields.
- **Progress and peak screens unchanged.** Neither consumes library data.

## Risks

**A mistyped name is permanent.** With the checkbox defaulting to checked and no
management UI, `Zercger Squat` enters the library and stays. Mistyping is most
likely mid-workout, when the user is least likely to correct it. Accepted
deliberately: a library that does not grow is the worse failure. If typos
accumulate, the cheap remedy is long-press-to-delete on a suggestion — roughly
twenty lines, no new screen — added at that point rather than pre-emptively.

**Migrations remain untested.** `MIGRATION_4_5` is the third migration shipping
without a test, guarded only by `fallbackToDestructiveMigration()`, which
deletes all user data if a migration is wrong instead of failing loudly. This
predates the feature and is not solved here. It is carried forward as a finding
for the documentation-and-infrastructure task, which should decide whether to
add a Room migration test harness and whether to remove the destructive
fallback.
