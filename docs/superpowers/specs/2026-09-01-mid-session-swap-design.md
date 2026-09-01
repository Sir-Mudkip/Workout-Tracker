# Mid-Session Exercise Swap — Design

**Date:** 2026-09-01
**Status:** Approved for planning
**Feature:** #4 of 4 in the current batch (after set difficulty, exercise reordering, and the exercise library)

## Problem

Gym equipment is often occupied. When the machine an exercise calls for is
taken, there is currently no way to record doing a close alternative — the only
options are logging it under the wrong name or editing the program, which would
change every future week.

The swap must apply to one session only, leave the program untouched, and keep
the progression data usable afterwards.

## Decisions

Settled during brainstorming, recorded here because each one narrows scope:

| Decision | Choice | Rationale |
|---|---|---|
| Progress attribution | **Count toward the original, label it** | Keeps the progression line unbroken — a swap is a close alternative in the same program slot — while recording what was actually done. |
| Bodyweight handling | **Asked in the swap dialog** | A swap can cross the weighted/bodyweight boundary. Without this, swapping a pulldown for a pull-up records near-zero volume. |
| Storage | **`session_exercise_swaps` table** | A swap is a property of the session, existing from the moment it is chosen — before any set is logged. Per-set columns cannot represent that. |
| Set and rep scheme | **Unchanged by the swap** | Swapping to a close alternative keeps the prescription. Failing to hit the reps is recorded via the set-difficulty scale from feature #1. |
| When swapping is allowed | **Until the first set of that exercise is logged** | The realistic case is finding the machine occupied before starting. Blocking afterwards avoids retroactively relabelling sets already performed. |
| Progress chart annotation | **Included** | A bar that may not represent the exercise it is labelled with is misleading. The annotation is what makes the attribution choice safe. |

## Architecture

```
ActiveSessionScreen
  "Swap exercise"  (hidden once a set is logged for this exercise)
        |
   SwapExerciseDialog
     ExercisePicker  (shared with feature #3)
     + bodyweight checkbox
        |
   SessionRepository.swapExercise(...)
        |
   session_exercise_swaps   (sessionId, plannedExerciseId) -> name, isBodyweight
        |
        +---> ActiveSessionState.displayName / isBodyweight  (session display)
        +---> observeWeeklyVolume LEFT JOIN                  (volume + annotation)
```

`SetLog` is unchanged. Its foreign key still points at the original
`plannedExerciseId`, which is what makes swapped work count toward the original
exercise without any data migration of existing logs.

### Schema

```kotlin
@Entity(
    tableName = "session_exercise_swaps",
    primaryKeys = ["sessionId", "plannedExerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannedExercise::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("plannedExerciseId")],
)
data class SessionExerciseSwap(
    val sessionId: Long,
    val plannedExerciseId: Long,
    val replacementName: String,
    val isBodyweight: Boolean,
)
```

Follows the composite-key pattern already used by `PeakResult`.

The composite primary key means swapping the same exercise twice in one session
**replaces** the row rather than accumulating history. Inserts use
`OnConflictStrategy.REPLACE`, so the last choice wins.

Both foreign keys cascade: deleting a session or a planned exercise removes its
swaps, leaving no orphans. This matches `SetLog`.

Two deliberate omissions:

- **No `createdAt`.** Nothing would read it; a swap's lifetime is the session's.
- **`isBodyweight` is not nullable.** The dialog always answers, pre-filled from
  the original's setting. `COALESCE(sw.isBodyweight, pe.isBodyweight)` therefore
  means exactly "is there a swap", not "is there a swap that also specified
  bodyweight".

`MIGRATION_5_6` creates the table empty:

```sql
CREATE TABLE IF NOT EXISTS session_exercise_swaps (
    sessionId INTEGER NOT NULL,
    plannedExerciseId INTEGER NOT NULL,
    replacementName TEXT NOT NULL,
    isBodyweight INTEGER NOT NULL,
    PRIMARY KEY(sessionId, plannedExerciseId),
    FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON DELETE CASCADE,
    FOREIGN KEY(plannedExerciseId) REFERENCES planned_exercises(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS index_session_exercise_swaps_sessionId
    ON session_exercise_swaps(sessionId);
CREATE INDEX IF NOT EXISTS index_session_exercise_swaps_plannedExerciseId
    ON session_exercise_swaps(plannedExerciseId);
```

Database version 5 -> 6. Must be registered in **both** `AppDatabase.Companion`
and `AppModule.provideDatabase`'s `addMigrations(...)`.
`fallbackToDestructiveMigration()` is active, so an unregistered migration
silently deletes all user data.

## Session behaviour

`ActiveSessionState` gains `swapsByExercise: Map<Long, SessionExerciseSwap>`,
loaded in `load()` alongside the existing data. Because it comes from the table,
a swap survives backgrounding and resuming the session.

Display resolves through two accessors rather than reading fields directly:

```kotlin
fun displayName(ex: PlannedExercise): String =
    swapsByExercise[ex.id]?.replacementName ?: ex.name

fun isBodyweight(ex: PlannedExercise): Boolean =
    swapsByExercise[ex.id]?.isBodyweight ?: ex.isBodyweight
```

Every place currently reading `ex.name` or `ex.isBodyweight` in the active
session — the exercise card, the bodyweight hint, and the logged-sets list —
goes through these instead.

### Swap availability

The `Swap exercise` button appears on the active card only while no set has been
logged for the current exercise in this session:

```kotlin
/**
 * Swapping is allowed only before the first set of [plannedExerciseId] is
 * logged in this session. Pure, so it is unit tested directly.
 */
fun canSwapExercise(plannedExerciseId: Long, logs: List<SetLog>): Boolean =
    logs.none { it.plannedExerciseId == plannedExerciseId }
```

`logs` is the existing `SessionRepository.observeLogs(sessionId)` flow, already
collected by the screen. It is passed in rather than held on
`ActiveSessionState`, which does not carry logs.

Once the first set is logged the button is gone, so sets already performed are
never relabelled.

### Weight prefill

`prefillWeight()` currently reads prior logs for the `plannedExerciseId`. For a
swapped exercise those describe the original movement — suggesting last week's
60 kg pulldown for today's pull-ups.

**When an exercise is swapped, the weight field prefills empty.** No prior-log
lookup, no `startingWeight` fallback. Reps still prefill from the plan, since
the prescription is unchanged.

## Volume and progress

Both `observeWeeklyVolume` and `weeklyVolume` in `SessionDao` are currently
byte-identical and must stay in sync. Each gains one join, one `COALESCE`, and
one column:

```sql
SELECT pe.id AS plannedExerciseId,
       pe.name AS exerciseName,
       s.weekNumber AS weekNumber,
       SUM(sl.actualReps * (sl.actualWeight +
           CASE WHEN COALESCE(sw.isBodyweight, pe.isBodyweight)
                THEN :bodyweight ELSE 0 END)) AS totalVolume,
       MAX(sw.replacementName) AS swappedTo
FROM set_logs sl
INNER JOIN workout_sessions s ON s.id = sl.sessionId
INNER JOIN planned_exercises pe ON pe.id = sl.plannedExerciseId
LEFT JOIN session_exercise_swaps sw
       ON sw.sessionId = sl.sessionId
      AND sw.plannedExerciseId = sl.plannedExerciseId
WHERE s.programId = :programId
GROUP BY pe.id, pe.name, s.weekNumber
ORDER BY pe.name, s.weekNumber
```

`GROUP BY pe.id` is unchanged, so swapped work still counts toward the original
exercise. The bodyweight component now follows what was actually performed.

`MAX(sw.replacementName)` exists only to satisfy the `GROUP BY`. There is at
most one swap per group: a session is unique per `(programId, weekNumber, dayId)`
and a planned exercise belongs to exactly one day.

`ExerciseWeeklyVolume` gains `val swappedTo: String? = null`.

### Session log

`LoggedSetsCard` shows the swapped name and marks it:

```
Neutral Grip Pull Up · set 1: 10 × BW (swapped)
```

### Progress chart

The per-week row marks swapped weeks, and the card carries a footnote:

```
Neutral Grip Lat Pulldown
 W1  ██████        1800 kg
 W2  ███████       2100 kg
 W3* ███████       2050 kg
 W4  ████████      2300 kg
 * W3 swapped: Neutral Grip Pull Up
```

The bar keeps its true length so progression stays comparable; the footnote
records that the week is not what the title says.

## Testing

Consistent with the existing suite: pure logic is extracted and tested, SQL and
Compose are not.

`SwapStateTest` covers the display resolution:

- `displayName` returns the replacement when swapped, the planned name otherwise
- `isBodyweight` follows the swap — weighted to bodyweight **and** bodyweight to
  weighted
- swap-aware prefill returns null for a swapped exercise and the existing
  prior-log result for an unswapped one
- `canSwapExercise` is true before any set is logged for that exercise, false after
- `canSwapExercise` is unaffected by sets logged against a *different* exercise

Manual verification must cover what the tests cannot:

1. Existing data survives `MIGRATION_5_6`.
2. Swap before the first set: the card shows the new name; the button then
   disappears after logging a set.
3. Swap persists across leaving and resuming the session.
4. **Weighted swapped to bodyweight logs non-zero volume** — log `10 × 0 kg` on
   a swapped bodyweight exercise and confirm the week shows `10 × bodyweight`,
   not zero. This is the `COALESCE` and it is the most likely thing to be wrong.
5. The progress chart shows the `*` marker and footnote for the swapped week.
6. An unswapped exercise is completely unaffected.

## Out of scope

- **The swap does not change sets or reps.** The prescription is kept.
- **No swap history.** Re-swapping replaces the row.
- **No suggestions.** The picker is a search box; the library carries no
  muscle-group or equipment metadata to rank alternatives by. This follows from
  feature #3's decisions.
- **The day editor is unchanged.** Editing an exercise permanently is a separate
  feature, not built here.

## Risks

**The volume SQL is untested.** The `COALESCE` over the swap join is the piece
most likely to be wrong and least visible when it is — a silent zero, or double
counting bodyweight. There is no Room test harness in this codebase, so manual
check 4 above is the only guard. This is the fourth consecutive feature where a
data-correctness gap lands on manual verification.

**Migrations remain untested.** `MIGRATION_5_6` is the fourth migration shipping
without a test, guarded only by `fallbackToDestructiveMigration()`, which
deletes all user data if a migration is wrong rather than failing loudly.

Both risks are carried forward to the documentation-and-infrastructure task,
which should decide whether to add a Room test harness and whether to remove the
destructive fallback. They are recorded here rather than solved, because solving
them inside a feature would mix unrelated concerns.
