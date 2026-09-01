# Database and migrations

Room over SQLite, one database file, `workout.db`. There is no remote
sync and no backup service — the database on the device is the only copy
of a user's training history. Losing it loses everything.

That single fact is why this page exists and why it is the first one to
read before touching `AppDatabase.kt`.

## The destructive fallback

`AppModule.provideDatabase` builds the database with
`.fallbackToDestructiveMigration()` (`di/AppModule.kt:33`). When Room
opens a database whose stored version is lower than the code's version
and cannot find a registered migration for the gap, that call tells it
to **drop every table and recreate the schema from scratch**.

It does not throw. It does not log an error the user will ever see. The
app opens to an empty program list, and every logged set is gone.

This makes forgetting to register a migration a silent, total data-loss
bug. It is the single most dangerous line in the codebase.

Fixing it is a real decision, not a cleanup: removing the fallback turns
a forgotten migration from silent data loss into a hard crash at
startup, which is louder but still broken for the user. The considered
options are to remove the fallback and add a migration test suite, or to
keep it and rely on the registration discipline below. Neither has been
chosen yet, and this page will be wrong the moment one is.

## Registering a migration

Every migration must be added in **two** places. Adding it to one is the
mistake the fallback punishes.

1. Define it in `AppDatabase.Companion` and bump `version` in the
   `@Database` annotation (`data/db/AppDatabase.kt`).
2. Add it to the `addMigrations(...)` call in
   `AppModule.provideDatabase` (`di/AppModule.kt:26`).

A migration defined but never registered compiles cleanly, passes every
test, installs fine, and destroys the user's data on first launch.

Before committing any schema change:

```bash
grep -n "MIGRATION_<n>_<n+1>" \
  app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
  app/src/main/java/com/luke/workouttracker/di/AppModule.kt
```

Two hits, one per file. One hit means the data is not safe.

## Version history

| Version | Change | Migration |
|---|---|---|
| 1 | Initial schema: programs, days, planned exercises, planned sets, sessions, set logs | — |
| 2 | `set_logs.restAfterMs` — rest duration recorded after each set | `MIGRATION_1_2` |
| 3 | `planned_exercises.isBodyweight`; new `peak_results` table | `MIGRATION_2_3` |
| 4 | `set_logs.difficulty` — optional 1-6 exertion rating | `MIGRATION_3_4` |
| 5 | New `custom_exercises` table — user-saved exercise names | `MIGRATION_4_5` |
| 6 | New `session_exercise_swaps` table — per-session exercise substitutions | `MIGRATION_5_6` |

New columns are added nullable or with a default, so existing rows stay
valid without a data backfill. New tables are created empty. No
migration to date rewrites existing data, and keeping it that way is
worth some effort — a migration that only adds structure is one that
cannot corrupt what is already there.

## Migrations are not tested

There is no Room test harness in this project and no instrumentation
tests. All five migrations above shipped verified only by a human
installing the app over a previous version and checking their data was
still there.

`exportSchema = false` (`data/db/AppDatabase.kt:35`) compounds this: Room
is not writing schema JSON, so there is nothing on disk describing what
each version's schema actually looked like, and no automated way to
verify a migration produces the schema the entities expect.

What does catch mistakes today is Room's annotation processor, which
compares the entity definitions against the migration SQL at build time.
A `CREATE TABLE` whose columns disagree with its `@Entity` fails the
build. That is real protection against typos, and it is the reason the
build step matters in every schema task. It cannot check anything about
*existing* data, ordering, or whether the migration is registered.

## Foreign keys and cascade

Ownership runs `Program → WorkoutDay → PlannedExercise → PlannedSet`,
and every level cascades on delete. Deleting a program removes its days,
their exercises, and those exercises' sets.

`SetLog` cascades from both `WorkoutSession` and `PlannedExercise`.
Deleting an exercise from a program therefore deletes the logged history
for that exercise — the sets are attached to the plan, not to a free
-standing record of what happened. Worth knowing before adding any
feature that deletes exercises.

`SessionExerciseSwap` cascades from both `WorkoutSession` and
`PlannedExercise`, so swaps clean themselves up with either parent.

`PeakResult` and `SessionExerciseSwap` use composite primary keys
(`(programId, lift)` and `(sessionId, plannedExerciseId)`) rather than a
generated id, which makes an upsert replace the existing row instead of
accumulating duplicates.

## Adding a schema change

1. Add or change the `@Entity`.
2. Write the migration in `AppDatabase.Companion`, bump `version`.
3. Register it in `AppModule.provideDatabase`.
4. Run the grep above and confirm two hits.
5. Build — the annotation processor validates entity against SQL.
6. Install **over an existing install** and confirm prior data survives.
   A fresh install proves nothing; it never runs the migration.

Step 6 is the only test the migration gets. Do not skip it.
