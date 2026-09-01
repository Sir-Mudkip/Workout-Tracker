# Data model

Two halves that meet in one place. The **plan** describes what should be
done; the **log** records what was. They join at
`SetLog.plannedExerciseId`, and almost every non-obvious behaviour in the
app follows from that one edge.

```
Program
  └── WorkoutDay            (dayIndex 1..N)
        └── PlannedExercise (orderInDay 0..N-1)
              └── PlannedSet (setNumber 1..N)

WorkoutSession (programId, weekNumber, dayId)
  └── SetLog ──────────────► PlannedExercise
  └── SessionExerciseSwap ─► PlannedExercise
```

A `Program` of 6 weeks × 3 days produces 18 sessions over its life, all
pointing back at the same 3 days of plan. That is what makes week-on-week
comparison possible: the plan is written once, and every week's logs hang
off it.

## Ordering columns

Two independent, differently-based sequences. Getting them confused is an
easy bug.

| Column | Base | Re-packed on delete |
|---|---|---|
| `WorkoutDay.dayIndex` | **1** | Yes, `ProgramRepository.deleteDay` |
| `PlannedExercise.orderInDay` | **0** | Yes, `ProgramRepository.deleteExercise` |
| `PlannedSet.setNumber` | **1** | No — rewritten wholesale by `replaceExerciseSets` |

Re-packing matters because new exercises take `max(orderInDay) + 1`
(`ExerciseOrdering.nextOrderInDay`). Before that was fixed, a new
exercise took the *list size*, so deleting the first of three and adding
another produced two rows sharing `orderInDay = 2` and an undefined
display order. Ordering is only ever read through `ORDER BY orderInDay`,
so duplicates do not error — they just make the list arbitrary.

## Weight semantics

Three fields interact, in a strict order of precedence.

- `PlannedExercise.startingWeight` — the default for every set.
- `PlannedSet.targetWeightOverride` — optional, overrides it for that set.
- `SetLog.actualWeight` — what was really lifted.

`ActiveSessionState.prefillWeight()` resolves what to suggest:

1. The most recent `actualWeight` from a **prior week**, same set number.
2. Otherwise `targetWeightOverride`.
3. Otherwise `startingWeight`.

Rule 1 is what makes the app progress automatically — last week's real
weight is this week's starting suggestion, so a program does not need
per-week weight prescriptions. Logs from the *current or future* weeks are
excluded deliberately; re-entering a session must not feed its own numbers
back into itself.

Rule 1 is skipped entirely for a swapped exercise, since prior logs
describe a different movement. See below.

## Bodyweight exercises

`PlannedExercise.isBodyweight` changes what `actualWeight` means. For a
bodyweight exercise it is **added** weight — `0.0` is a clean bodyweight
rep, `20.0` is a weighted pull-up with 20 kg.

Volume therefore cannot be `reps × weight`. The DAO computes:

```sql
SUM(sl.actualReps * (sl.actualWeight +
    CASE WHEN COALESCE(sw.isBodyweight, pe.isBodyweight)
         THEN :bodyweight ELSE 0 END))
```

`:bodyweight` is the user's bodyweight from `BodyweightPrefs`, passed in
from the ViewModel. This is why bodyweight lives in settings and why the
progress screen re-queries when it changes.

The consequence worth knowing: **changing your bodyweight setting
retroactively changes historical volume** for every bodyweight exercise.
There is no per-session bodyweight snapshot. That is a deliberate
simplification, not an oversight, but it means volume for those exercises
is "at today's bodyweight", not "as it was then".

## Session identity

A `WorkoutSession` is unique per `(programId, weekNumber, dayId)`.
`SessionRepository.startOrResumeSession` looks for an existing incomplete
session with that key and returns it rather than creating a second one,
which is what makes a session resumable after closing the app.

Progress within a session is **not stored**. It is recomputed on load by
`computeResume`, which counts logged sets per exercise and lands on the
first exercise with fewer logs than planned sets. The set logs are the
progress record; there is no cursor to get out of sync.

## Swaps

`SessionExerciseSwap` replaces an exercise for one session. Set logs keep
pointing at the **original** `plannedExerciseId`, so swapped work counts
toward the original exercise's progression rather than fragmenting the
chart.

Two things change: the displayed name, and the bodyweight flag —
`COALESCE(sw.isBodyweight, pe.isBodyweight)` above. The flag has to be
overridable because a swap can cross the boundary; substituting a lat
pulldown with a pull-up and keeping the planned exercise's
`isBodyweight = false` would record that week's volume as near zero.

The set and rep scheme is **not** changed by a swap. The prescription
stands; failing to hit it is recorded through the difficulty rating.

Swapping is only permitted before the first set of that exercise is
logged (`canSwapExercise`), so sets already performed are never
relabelled as something else.

## Difficulty

`SetLog.difficulty` is a nullable `Int` holding `SetDifficulty.stored`,
1 (Very easy) to 6 (Failure). Null means unrated, which is the normal
case — rating is always optional.

It is stored as an ordered integer rather than an enum name so it can be
compared and averaged in SQL without mapping back through Kotlin. Nothing
reads it that way yet; the column is shaped for it.

## Peak lifts

`PeakResult` is keyed `(programId, lift)` where `lift` is a `PeakLift`
enum **name**, not its display name. It records a start and end 1RM per
program and is entirely separate from the set logs — the peak day screen
is a manual record, not something derived from logged sets.
