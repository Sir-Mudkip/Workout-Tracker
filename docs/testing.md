# Testing

JVM unit tests only. There are no instrumentation tests, no Compose UI
tests, and no Room test harness.

```bash
./gradlew testDebugUnitTest
```

## The pattern: extract the logic, test the function

Anything that can be expressed without Android or Room is pulled into a
plain Kotlin file and tested directly. Everything else is verified by
hand.

This is not an aspiration — it is how each feature has been built, and
following it is how new work stays testable:

| Tested | Why it could be |
|---|---|
| `ExerciseOrdering` | Index arithmetic on lists of integers |
| `ExerciseSearch` | String filtering and merging |
| `ExerciseLibrary` | A list of strings, checked for invariants |
| `SetDifficulty` | An enum with a lookup |
| `ActiveSessionState` | Immutable snapshot with pure computed properties |

`ActiveSessionState` is the one worth studying. It could have been a
ViewModel holding mutable fields, in which case none of the resume,
prefill, or swap-resolution logic would be reachable from a JVM test. It
is instead a data class whose interesting behaviour is pure functions
over its own fields, so tests construct one directly and assert.

When adding a feature, ask what part of it is arithmetic or string
handling wearing a UI costume, and lift that out first.

## Current suite

| Class | Tests | Covers |
|---|---|---|
| `ActiveSessionStateTest` | 5 | Weight prefill precedence |
| `ExerciseLibraryTest` | 5 | Seed list invariants — count, duplicates, naming |
| `ExerciseOrderingTest` | 13 | Reordering, boundaries, re-packing after delete |
| `ExerciseSearchTest` | 14 | Token matching, ranking, result cap, merge/dedupe |
| `ProgramJsonTest` | 2 | Import parsing and round-trip |
| `SetDifficultyTest` | 6 | Scale mapping and out-of-range handling |
| `SwapStateTest` | 10 | Name/bodyweight resolution, swap eligibility |
| **Total** | **55** | |

Invariant tests are worth calling out as a category. `ExerciseLibraryTest`
does not test behaviour — it asserts the shipped exercise list has no
duplicates, no stray whitespace, and no abbreviations. Those are
documented conventions that a human editing a 140-entry list will
eventually break, so the test is the enforcement.

## What is not covered

Being specific here matters more than the coverage number, because these
are the places bugs actually survive.

**Room DAOs and migrations.** No harness exists. Every migration has
shipped verified only by installing over a previous version by hand. The
consequences are in [`database.md`](./database.md); they are severe,
because `fallbackToDestructiveMigration()` turns a mistake into silent
data loss.

**The volume SQL.** `observeWeeklyVolume` carries the bodyweight
`COALESCE` across the swap join — the most subtle expression in the
project, and one whose failure mode is a plausible-looking wrong number
rather than a crash. It has no automated coverage at all.

**Compose UI.** No screen is tested. Behaviour that lives only in a
Composable — a disabled button, a conditional field — is verified by
tapping through the app.

**Repositories.** Thin adapters over DAOs, untested by the same
reasoning that leaves the DAOs untested.

## Duplicated SQL

`SessionDao.observeWeeklyVolume` and `SessionDao.weeklyVolume` are the
same query, one returning a `Flow` and one a `List`. Nothing enforces
that they stay identical, and a change applied to one and not the other
would leave the progress screen and any one-shot caller disagreeing.

Until that is factored out or covered by a test, changes to either must
be applied to both in the same edit, and verified:

```bash
grep -c "COALESCE(sw.isBodyweight, pe.isBodyweight)" \
  app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
```

Expect `2`.

## Manual verification

Some checks cannot currently be automated. When a change touches the
schema or the volume calculation, verify by hand and say so in the pull
request:

1. **Install over an existing install** — never a fresh one, which skips
   the migration entirely — and confirm prior programs, sessions, logged
   sets and ratings are intact.
2. **Bodyweight volume** — log a bodyweight set at `0 kg` and confirm the
   week's volume is `reps × bodyweight`, not zero.
3. **The specific behaviour changed.**
