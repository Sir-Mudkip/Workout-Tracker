# Mid-Session Exercise Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user swap an exercise for a close alternative during a live session, affecting only that session, while keeping volume counted toward the original exercise and visibly marked as swapped.

**Architecture:** A `session_exercise_swaps` table keyed by `(sessionId, plannedExerciseId)` holds the replacement name and its bodyweight flag. The active session resolves display through the swap; the weekly-volume query left-joins it, using `COALESCE` for the bodyweight component and surfacing the replacement name for annotation. `SetLog` is untouched, so existing history needs no migration.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Material 3, Room, Hilt, kotlinx.coroutines Flow, JUnit 4

**Spec:** `docs/superpowers/specs/2026-09-01-mid-session-swap-design.md`

## Global Constraints

- **Database version goes 5 → 6.** Version 5 is current (set by the exercise library feature).
- **`MIGRATION_5_6` must be registered in BOTH `AppDatabase.Companion` and `AppModule.provideDatabase`'s `addMigrations(...)`.** `fallbackToDestructiveMigration()` is active — an unregistered migration silently destroys all user data instead of failing.
- **`observeWeeklyVolume` and `weeklyVolume` in `SessionDao` are byte-identical and must stay so.** Every change to one is made to the other in the same step.
- **`GROUP BY pe.id, pe.name, s.weekNumber` must not change.** It is what makes swapped work count toward the original exercise.
- **Swapping is allowed only before the first set of that exercise is logged in the session.**
- **The swap never changes the set or rep scheme.**
- **`SetLog` is not modified.** No new columns, no changed foreign keys.
- **Package root:** `com.luke.workouttracker`
- **Build commands** (no `gradlew`; use the distribution and JDK Android Studio downloaded — Studio's own JBR is version 25, which Gradle 8.7 rejects):
  ```bash
  export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
  export ANDROID_HOME=$HOME/Android/Sdk
  GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
  "$GRADLE" testDebugUnitTest --console=plain
  ```

---

### Task 1: Swap persistence

Entity, DAO, migration, DI. Carries the destructive-migration risk, so registration is verified explicitly before committing.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/data/db/entities/SessionExerciseSwap.kt`
- Create: `app/src/main/java/com/luke/workouttracker/data/db/dao/SwapDao.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/di/AppModule.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `data class SessionExerciseSwap(val sessionId: Long, val plannedExerciseId: Long, val replacementName: String, val isBodyweight: Boolean)`
  - `interface SwapDao` with `suspend fun upsert(swap: SessionExerciseSwap)`, `suspend fun swapsForSession(sessionId: Long): List<SessionExerciseSwap>`, `fun observeSwapsForSession(sessionId: Long): Flow<List<SessionExerciseSwap>>`
  - `AppDatabase.swapDao(): SwapDao`
  - `AppDatabase.Companion.MIGRATION_5_6`

- [ ] **Step 1: Create the entity**

Create `app/src/main/java/com/luke/workouttracker/data/db/entities/SessionExerciseSwap.kt`:

```kotlin
package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * An exercise replaced for the duration of one session.
 *
 * Set logs continue to reference the original [plannedExerciseId], so swapped
 * work counts toward the original exercise's progression. Only the displayed
 * name and the bodyweight flag change.
 *
 * The composite primary key means re-swapping replaces the row rather than
 * accumulating history.
 */
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

- [ ] **Step 2: Create the DAO**

Create `app/src/main/java/com/luke/workouttracker/data/db/dao/SwapDao.kt`:

```kotlin
package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
import kotlinx.coroutines.flow.Flow

@Dao
interface SwapDao {
    /** Replaces any existing swap for the same session and exercise. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(swap: SessionExerciseSwap)

    @Query("SELECT * FROM session_exercise_swaps WHERE sessionId = :sessionId")
    suspend fun swapsForSession(sessionId: Long): List<SessionExerciseSwap>

    @Query("SELECT * FROM session_exercise_swaps WHERE sessionId = :sessionId")
    fun observeSwapsForSession(sessionId: Long): Flow<List<SessionExerciseSwap>>
}
```

- [ ] **Step 3: Register entity, version, DAO accessor, and migration in AppDatabase**

In `app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt`:

Add imports:
```kotlin
import com.luke.workouttracker.data.db.dao.SwapDao
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
```

Add `SessionExerciseSwap::class` to the `entities` array and change `version = 5` to `version = 6`.

Add the DAO accessor beside the others:
```kotlin
abstract fun swapDao(): SwapDao
```

Add the migration inside `companion object`:
```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS session_exercise_swaps (
                sessionId INTEGER NOT NULL,
                plannedExerciseId INTEGER NOT NULL,
                replacementName TEXT NOT NULL,
                isBodyweight INTEGER NOT NULL,
                PRIMARY KEY(sessionId, plannedExerciseId),
                FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON DELETE CASCADE,
                FOREIGN KEY(plannedExerciseId) REFERENCES planned_exercises(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_session_exercise_swaps_sessionId ON session_exercise_swaps(sessionId)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_session_exercise_swaps_plannedExerciseId ON session_exercise_swaps(plannedExerciseId)"
        )
    }
}
```

- [ ] **Step 4: Register the migration and DAO provider in AppModule**

In `app/src/main/java/com/luke/workouttracker/di/AppModule.kt`, add to the existing `addMigrations(...)`:
```kotlin
AppDatabase.MIGRATION_5_6,
```

Add the import and provider:
```kotlin
import com.luke.workouttracker.data.db.dao.SwapDao
```
```kotlin
@Provides
fun provideSwapDao(db: AppDatabase): SwapDao = db.swapDao()
```

- [ ] **Step 5: Verify the migration is registered in both places**

Run:
```bash
grep -n "MIGRATION_5_6" \
  app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
  app/src/main/java/com/luke/workouttracker/di/AppModule.kt
grep -n "version = " app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt
```
Expected: `MIGRATION_5_6` in **both** files, and `version = 6`.

Do not proceed if either is missing.

- [ ] **Step 6: Build**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL. Room validates the entity against the migration SQL at compile time.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/db/entities/SessionExerciseSwap.kt \
        app/src/main/java/com/luke/workouttracker/data/db/dao/SwapDao.kt \
        app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
        app/src/main/java/com/luke/workouttracker/di/AppModule.kt
git commit -m "feat: add session_exercise_swaps table and migration 5->6"
```

---

### Task 2: Swap resolution logic

The pure logic, with tests. Also changes `prefillWeight()` to return `Double?`, which requires updating the five existing prefill tests in the same task — they currently pass a `Double` to a three-argument `assertEquals`.

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt` (the `ActiveSessionState` data class only)
- Modify: `app/src/test/java/com/luke/workouttracker/ActiveSessionStateTest.kt`
- Test: `app/src/test/java/com/luke/workouttracker/SwapStateTest.kt`

**Interfaces:**
- Consumes: `SessionExerciseSwap` (Task 1)
- Produces:
  - `ActiveSessionState.swapsByExercise: Map<Long, SessionExerciseSwap>` (new constructor parameter, defaulting to `emptyMap()`)
  - `ActiveSessionState.displayName(ex: PlannedExercise): String`
  - `ActiveSessionState.isBodyweight(ex: PlannedExercise): Boolean`
  - `ActiveSessionState.prefillWeight(): Double?` — **return type changed from `Double`**
  - top-level `fun canSwapExercise(plannedExerciseId: Long, logs: List<SetLog>): Boolean`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/luke/workouttracker/SwapStateTest.kt`:

```kotlin
package com.luke.workouttracker

import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.ui.session.ActiveSessionState
import com.luke.workouttracker.ui.session.canSwapExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapStateTest {

    private val pulldown = PlannedExercise(
        id = 1, dayId = 1, name = "Neutral Grip Lat Pulldown",
        orderInDay = 0, startingWeight = 60.0, isBodyweight = false,
    )
    private val pullUp = SessionExerciseSwap(
        sessionId = 1, plannedExerciseId = 1,
        replacementName = "Neutral Grip Pull Up", isBodyweight = true,
    )

    private fun state(swaps: Map<Long, SessionExerciseSwap> = emptyMap()) = ActiveSessionState(
        sessionId = 1,
        programId = 1,
        weekNumber = 3,
        dayName = "Pull",
        programName = "PPL",
        exercises = listOf(pulldown),
        setsByExercise = mapOf(1L to listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 10))),
        priorLogsByExercise = emptyMap(),
        currentExerciseIdx = 0,
        currentSetIdx = 0,
        completed = false,
        swapsByExercise = swaps,
    )

    private fun log(exerciseId: Long) = SetLog(
        id = 0, sessionId = 1, plannedExerciseId = exerciseId,
        setNumber = 1, actualReps = 10, actualWeight = 60.0,
    )

    // displayName

    @Test fun display_name_is_the_planned_name_when_not_swapped() {
        assertEquals("Neutral Grip Lat Pulldown", state().displayName(pulldown))
    }

    @Test fun display_name_is_the_replacement_when_swapped() {
        assertEquals("Neutral Grip Pull Up", state(mapOf(1L to pullUp)).displayName(pulldown))
    }

    // isBodyweight

    @Test fun bodyweight_follows_the_planned_exercise_when_not_swapped() {
        assertFalse(state().isBodyweight(pulldown))
    }

    @Test fun swapping_a_weighted_exercise_for_a_bodyweight_one_flips_the_flag() {
        assertTrue(state(mapOf(1L to pullUp)).isBodyweight(pulldown))
    }

    @Test fun swapping_a_bodyweight_exercise_for_a_weighted_one_flips_the_flag_back() {
        val dip = pulldown.copy(name = "Weighted Dip", isBodyweight = true)
        val machine = SessionExerciseSwap(
            sessionId = 1, plannedExerciseId = 1,
            replacementName = "Machine Chest Press", isBodyweight = false,
        )
        assertFalse(state(mapOf(1L to machine)).isBodyweight(dip))
    }

    // prefillWeight

    @Test fun prefill_weight_is_null_for_a_swapped_exercise() {
        // Prior logs describe the original movement, so suggesting them is wrong.
        assertNull(state(mapOf(1L to pullUp)).prefillWeight())
    }

    @Test fun prefill_weight_still_works_for_an_unswapped_exercise() {
        assertEquals(60.0, state().prefillWeight()!!, 0.0001)
    }

    // canSwapExercise

    @Test fun swapping_is_allowed_before_any_set_is_logged() {
        assertTrue(canSwapExercise(1L, emptyList()))
    }

    @Test fun swapping_is_blocked_once_a_set_is_logged_for_that_exercise() {
        assertFalse(canSwapExercise(1L, listOf(log(1L))))
    }

    @Test fun a_set_logged_for_a_different_exercise_does_not_block_swapping() {
        assertTrue(canSwapExercise(1L, listOf(log(99L))))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run the build command from Global Constraints.
Expected: FAIL — `Unresolved reference 'canSwapExercise'`, and `swapsByExercise` is not a known parameter.

- [ ] **Step 3: Update ActiveSessionState**

In `app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt`:

Add the import:
```kotlin
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
```

Add the constructor parameter as the last one, defaulted so existing construction sites keep compiling:
```kotlin
val completed: Boolean,
val swapsByExercise: Map<Long, SessionExerciseSwap> = emptyMap(),
```

Add the two accessors inside the class body, beside `prefillReps()`:
```kotlin
/** The replacement name when this exercise is swapped for the session. */
fun displayName(ex: PlannedExercise): String =
    swapsByExercise[ex.id]?.replacementName ?: ex.name

/** The swap's bodyweight setting when swapped, otherwise the planned one. */
fun isBodyweight(ex: PlannedExercise): Boolean =
    swapsByExercise[ex.id]?.isBodyweight ?: ex.isBodyweight
```

Change `prefillWeight()` to return null for a swapped exercise. Replace the whole function:
```kotlin
/**
 * Weight to pre-fill, or null to leave the field empty.
 *
 * Null for a swapped exercise: prior logs and the starting weight both
 * describe the original movement, so suggesting them would be misleading.
 */
fun prefillWeight(): Double? {
    val ex = currentExercise ?: return 0.0
    if (swapsByExercise.containsKey(ex.id)) return null
    val set = currentSet ?: return ex.startingWeight
    val priorMap = priorLogsByExercise[ex.id].orEmpty()
    val lastWeekEntries = priorMap.filterKeys { it.first < weekNumber && it.second == set.setNumber }
    val mostRecent = lastWeekEntries.maxByOrNull { it.key.first }?.value
    if (mostRecent != null) return mostRecent
    set.targetWeightOverride?.let { return it }
    return ex.startingWeight
}
```

Add the top-level function after the `ActiveSessionState` class:
```kotlin
/**
 * Swapping is allowed only before the first set of [plannedExerciseId] is
 * logged in this session, so sets already performed are never relabelled.
 */
fun canSwapExercise(plannedExerciseId: Long, logs: List<SetLog>): Boolean =
    logs.none { it.plannedExerciseId == plannedExerciseId }
```

- [ ] **Step 4: Update the five existing prefill tests**

`prefillWeight()` now returns `Double?`, which does not compile against
`assertEquals(expected, actual, delta)`. In
`app/src/test/java/com/luke/workouttracker/ActiveSessionStateTest.kt`, add `!!`
to each of the five call sites:

```kotlin
assertEquals(30.0, s.prefillWeight()!!, 0.0001)
assertEquals(27.5, s.prefillWeight()!!, 0.0001)
assertEquals(35.0, s.prefillWeight()!!, 0.0001)
assertEquals(32.5, s.prefillWeight()!!, 0.0001)
assertEquals(30.0, s.prefillWeight()!!, 0.0001)
```

These assertions are unchanged in meaning: none of those states is swapped, so
`prefillWeight()` is non-null.

- [ ] **Step 5: Fix the ActiveCard call site so it compiles**

`ActiveCard` calls `state.prefillWeight()` and uses it as a `Double`. Make the
minimum change needed to compile here; the full UI work is Task 5.

Replace:
```kotlin
val targetWeight = state.prefillWeight()
```
with:
```kotlin
val targetWeight = state.prefillWeight() ?: 0.0
```

- [ ] **Step 6: Run tests to verify they pass**

Run the build command from Global Constraints.
Expected: PASS — `SwapStateTest` 10 tests, `ActiveSessionStateTest` 5 tests, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt \
        app/src/test/java/com/luke/workouttracker/ActiveSessionStateTest.kt \
        app/src/test/java/com/luke/workouttracker/SwapStateTest.kt
git commit -m "feat: add swap resolution logic to active session state"
```

---

### Task 3: Repository swap methods

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/data/repo/SessionRepository.kt`

**Interfaces:**
- Consumes: `SwapDao` (Task 1)
- Produces:
  - `suspend fun swapExercise(sessionId: Long, plannedExerciseId: Long, replacementName: String, isBodyweight: Boolean)`
  - `suspend fun swapsForSession(sessionId: Long): Map<Long, SessionExerciseSwap>`

- [ ] **Step 1: Write the implementation**

No unit test: this is a thin adapter over a DAO, matching every other method in
this repository. None of them are unit tested, and there is no Room harness.

In `app/src/main/java/com/luke/workouttracker/data/repo/SessionRepository.kt`:

Add imports:
```kotlin
import com.luke.workouttracker.data.db.dao.SwapDao
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
```

Add `SwapDao` to the constructor:
```kotlin
@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val swapDao: SwapDao,
) {
```

Add the methods:
```kotlin
/** Replaces an exercise for this session only. Re-swapping overwrites. */
suspend fun swapExercise(
    sessionId: Long,
    plannedExerciseId: Long,
    replacementName: String,
    isBodyweight: Boolean,
) {
    swapDao.upsert(
        SessionExerciseSwap(
            sessionId = sessionId,
            plannedExerciseId = plannedExerciseId,
            replacementName = replacementName.trim(),
            isBodyweight = isBodyweight,
        )
    )
}

/** Swaps for this session, keyed by the planned exercise they replace. */
suspend fun swapsForSession(sessionId: Long): Map<Long, SessionExerciseSwap> =
    swapDao.swapsForSession(sessionId).associateBy { it.plannedExerciseId }
```

- [ ] **Step 2: Build**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL. Hilt resolves `SwapDao` via the provider from Task 1.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/repo/SessionRepository.kt
git commit -m "feat: add swap read and write to session repository"
```

---

### Task 4: Swap-aware volume queries

The highest-risk change in this feature and the one with no automated coverage.

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt`

**Interfaces:**
- Consumes: the `session_exercise_swaps` table (Task 1)
- Produces: `ExerciseWeeklyVolume` gains `val swappedTo: String? = null`

- [ ] **Step 1: Add the projection field**

In `app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt`:

```kotlin
data class ExerciseWeeklyVolume(
    val plannedExerciseId: Long,
    val exerciseName: String,
    val weekNumber: Int,
    val totalVolume: Double,
    /** Replacement name when this week's work was swapped, else null. */
    val swappedTo: String? = null,
)
```

- [ ] **Step 2: Update BOTH volume queries**

`observeWeeklyVolume` and `weeklyVolume` are byte-identical. Replace the SQL in
**both** with exactly this — do not change one and leave the other:

```sql
SELECT pe.id AS plannedExerciseId,
       pe.name AS exerciseName,
       s.weekNumber AS weekNumber,
       SUM(sl.actualReps * (sl.actualWeight + CASE WHEN COALESCE(sw.isBodyweight, pe.isBodyweight) THEN :bodyweight ELSE 0 END)) AS totalVolume,
       MAX(sw.replacementName) AS swappedTo
FROM set_logs sl
INNER JOIN workout_sessions s ON s.id = sl.sessionId
INNER JOIN planned_exercises pe ON pe.id = sl.plannedExerciseId
LEFT JOIN session_exercise_swaps sw ON sw.sessionId = sl.sessionId AND sw.plannedExerciseId = sl.plannedExerciseId
WHERE s.programId = :programId
GROUP BY pe.id, pe.name, s.weekNumber
ORDER BY pe.name, s.weekNumber
```

Three changes from the current SQL, and nothing else:
1. `pe.isBodyweight` becomes `COALESCE(sw.isBodyweight, pe.isBodyweight)`
2. a `LEFT JOIN` onto `session_exercise_swaps`
3. a new `MAX(sw.replacementName) AS swappedTo` column

`GROUP BY` is unchanged — that is what keeps swapped work counted toward the
original exercise. `MAX` exists only to satisfy the grouping; there is at most
one swap per group.

- [ ] **Step 3: Verify both queries are identical**

Run:
```bash
grep -c "COALESCE(sw.isBodyweight, pe.isBodyweight)" app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
grep -c "MAX(sw.replacementName)" app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
grep -c "LEFT JOIN session_exercise_swaps" app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
```
Expected: `2` for each. A `1` means only one of the two queries was updated.

- [ ] **Step 4: Build**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL. Room validates the SQL and the projection columns at
compile time, so a typo in a column name fails here.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/db/dao/SessionDao.kt
git commit -m "feat: make weekly volume swap-aware"
```

---

### Task 5: Swap UI in the active session

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt`

**Interfaces:**
- Consumes: `ExercisePicker` (feature #3), `canSwapExercise`, `displayName`, `isBodyweight` (Task 2), `SessionRepository.swapExercise` / `swapsForSession` (Task 3)
- Produces: nothing for later tasks

- [ ] **Step 1: Load swaps into state**

In `ActiveSessionViewModel.load()`, fetch swaps and pass them into the state.

After the `existingLogs` line add:
```kotlin
val swaps = sessions.swapsForSession(sessionId)
```

Add to the `ActiveSessionState(...)` construction, after `completed`:
```kotlin
swapsByExercise = swaps,
```

- [ ] **Step 2: Add the swap action to the ViewModel**

Add after `logCurrentSet`:
```kotlin
/** Replace the current exercise for this session only. */
fun swapCurrentExercise(replacementName: String, isBodyweight: Boolean) {
    val s = _state.value ?: return
    val ex = s.currentExercise ?: return
    viewModelScope.launch {
        sessions.swapExercise(sessionId, ex.id, replacementName, isBodyweight)
        _state.value = s.copy(
            swapsByExercise = sessions.swapsForSession(sessionId),
        )
    }
}
```

Expose the library names for the picker. Add `ExerciseLibraryRepository` to the
constructor:
```kotlin
private val library: ExerciseLibraryRepository,
```
and the flow beside `logs`:
```kotlin
val libraryNames: StateFlow<List<String>> =
    library.observeNames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

Add imports:
```kotlin
import com.luke.workouttracker.data.repo.ExerciseLibraryRepository
import com.luke.workouttracker.ui.library.ExercisePicker
```

- [ ] **Step 3: Route display through the swap accessors**

In `ActiveCard`, replace the two direct field reads:

```kotlin
val isBw = ex.isBodyweight
```
becomes
```kotlin
val isBw = state.isBodyweight(ex)
```

```kotlin
Text(ex.name, style = MaterialTheme.typography.headlineSmall)
```
becomes
```kotlin
Text(state.displayName(ex), style = MaterialTheme.typography.headlineSmall)
```

In `LoggedSetsCard`, replace the name and bodyweight reads. The card already has
`state`, so:

```kotlin
val exName = ex?.name ?: "?"
```
becomes
```kotlin
val exName = ex?.let { state.displayName(it) } ?: "?"
val swappedMark = if (ex != null && state.swapsByExercise.containsKey(ex.id)) " (swapped)" else ""
```

and the two bodyweight checks:
```kotlin
ex?.isBodyweight == true && log.actualWeight == 0.0 -> "BW"
ex?.isBodyweight == true -> "BW + ${trim(log.actualWeight)} kg"
```
become
```kotlin
ex != null && state.isBodyweight(ex) && log.actualWeight == 0.0 -> "BW"
ex != null && state.isBodyweight(ex) -> "BW + ${trim(log.actualWeight)} kg"
```

and append the marker to the row text:
```kotlin
"$exName · set ${log.setNumber}: ${log.actualReps} × $weightText$restPart$difficultyPart$swappedMark",
```

- [ ] **Step 4: Empty the weight field for a swapped exercise**

In `ActiveCard`, `targetWeight` was made non-null in Task 2 Step 5. Now use the
nullable value properly so the hint disappears when swapped.

Replace:
```kotlin
val targetWeight = state.prefillWeight() ?: 0.0
```
with:
```kotlin
val prefill = state.prefillWeight()
val targetWeight = prefill ?: 0.0
```

Replace the hint line:
```kotlin
val weightHint = trim(targetWeight)
```
with:
```kotlin
val weightHint = prefill?.let { trim(it) } ?: ""
```

And make the target line reflect that there is no target when swapped. Replace:
```kotlin
Text(
    "Set ${set.setNumber} of ${state.totalSetsForCurrent} · target ${set.targetReps} reps @ $targetWeightText",
    style = MaterialTheme.typography.bodyMedium,
)
```
with:
```kotlin
val targetText = if (prefill == null) {
    "Set ${set.setNumber} of ${state.totalSetsForCurrent} · target ${set.targetReps} reps"
} else {
    "Set ${set.setNumber} of ${state.totalSetsForCurrent} · target ${set.targetReps} reps @ $targetWeightText"
}
Text(targetText, style = MaterialTheme.typography.bodyMedium)
```

- [ ] **Step 5: Add the swap button and dialog**

`ActiveCard` needs the logs to decide whether swapping is still allowed, plus a
callback. Change its signature:

```kotlin
@Composable
private fun ActiveCard(
    state: ActiveSessionState,
    bodyweight: Double,
    logs: List<SetLog>,
    libraryNames: List<String>,
    onComplete: (Int, Double) -> Unit,
    onSwap: (String, Boolean) -> Unit,
) {
```

Update its call site in `ActiveSessionScreen`:
```kotlin
ActiveCard(
    state = s,
    bodyweight = bodyweight,
    logs = logs,
    libraryNames = libraryNames,
    onComplete = { reps, weight -> vm.logCurrentSet(reps, weight) },
    onSwap = { name, isBw -> vm.swapCurrentExercise(name, isBw) },
)
```

and collect the names near the other `collectAsState` calls:
```kotlin
val libraryNames by vm.libraryNames.collectAsState()
```

Inside `ActiveCard`, add dialog state near the `reps`/`weight` state:
```kotlin
var showSwap by remember(state.currentExerciseIdx) { mutableStateOf(false) }
```

Add the button after the "Complete set" `Button`, shown only while swapping is
allowed:
```kotlin
if (canSwapExercise(ex.id, logs)) {
    TextButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showSwap = true },
    ) { Text("Swap exercise") }
}
```

Add the dialog at the end of `ActiveCard`, outside the `Card`:
```kotlin
if (showSwap) {
    SwapExerciseDialog(
        libraryNames = libraryNames,
        initialIsBodyweight = state.isBodyweight(ex),
        onDismiss = { showSwap = false },
        onConfirm = { name, isBw ->
            onSwap(name, isBw)
            showSwap = false
        },
    )
}
```

Add the new imports:
```kotlin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
```

- [ ] **Step 6: Write the dialog composable**

Add at the end of `ActiveSessionScreen.kt`, before the private helper functions:

```kotlin
@Composable
private fun SwapExerciseDialog(
    libraryNames: List<String>,
    initialIsBodyweight: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (name: String, isBodyweight: Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var isBodyweight by remember { mutableStateOf(initialIsBodyweight) }
    var saveToLibrary by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Swap exercise") },
        text = {
            Column {
                ExercisePicker(
                    names = libraryNames,
                    query = query,
                    onQueryChange = { query = it },
                    saveToLibrary = saveToLibrary,
                    onSaveToLibraryChange = { saveToLibrary = it },
                    onNameSelected = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBodyweight, onCheckedChange = { isBodyweight = it })
                    Text("Bodyweight exercise", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = query.isNotBlank(),
                onClick = { onConfirm(query.trim(), isBodyweight) },
            ) { Text("Swap") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
```

Note: the picker's save-to-library checkbox is shown but the swap does not write
to the library — saving from a swap is out of scope, and the exercise is chosen
from existing names in the common case.

- [ ] **Step 7: Build and run the full suite**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, all tests passing.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/session/ActiveSessionScreen.kt
git commit -m "feat: swap an exercise mid-session"
```

---

### Task 6: Progress chart annotation

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/progress/ProgressScreen.kt`

**Interfaces:**
- Consumes: `ExerciseWeeklyVolume.swappedTo` (Task 4)
- Produces: nothing

- [ ] **Step 1: Carry swaps through the view model mapping**

In `app/src/main/java/com/luke/workouttracker/ui/progress/ProgressScreen.kt`,
add a field to `ExerciseProgress`:
```kotlin
/** Week number to the replacement name performed that week. */
val swapsByWeek: Map<Int, String>,
```

Populate it in `toProgress()`, inside the `ExerciseProgress(...)` construction:
```kotlin
swapsByWeek = sorted.mapNotNull { row ->
    row.swappedTo?.let { row.weekNumber to it }
}.toMap(),
```

- [ ] **Step 2: Mark swapped weeks and add the footnote**

In `ExerciseProgressCard`, mark the week label. Replace:
```kotlin
Text("W$week", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
```
with:
```kotlin
val weekLabel = if (p.swapsByWeek.containsKey(week)) "W$week*" else "W$week"
Text(weekLabel, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.bodySmall)
```

Add the footnote after the week `Column`, still inside the card's outer
`Column`:
```kotlin
if (p.swapsByWeek.isNotEmpty()) {
    Column(Modifier.padding(top = 6.dp)) {
        p.swapsByWeek.toSortedMap().forEach { (week, name) ->
            Text(
                "* W$week swapped: $name",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
```

- [ ] **Step 3: Build and run the full suite**

Run the build command from Global Constraints.
Expected: BUILD SUCCESSFUL, all tests passing.

- [ ] **Step 4: Install and verify manually**

Install from Android Studio. A CLI install fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` because of a different debug signing key,
and uninstalling would destroy the data the migration must preserve.

Verify in order:

1. **Existing data survives.** Open a program from before this change — days,
   exercises, logged sets, and difficulty ratings all intact. If anything is
   missing, `MIGRATION_5_6` is wrong; stop and fix it.
2. **Swap before the first set.** Start a session, tap `Swap exercise`, pick an
   alternative, confirm. The card shows the new name.
3. **The button disappears** after logging the first set of that exercise.
4. **The swap survives a resume.** Leave the session and re-enter it — the
   replacement name is still shown.
5. **Bodyweight volume is correct.** Swap a weighted exercise for a bodyweight
   one, tick the bodyweight box, log `10 reps × 0 kg`, finish the session, and
   open Progress. That week's volume must be roughly `10 × your bodyweight`,
   **not zero**. This is the `COALESCE` and it has no automated coverage.
6. **The chart is annotated.** The swapped week shows `W3*` and the footnote
   `* W3 swapped: <name>`.
7. **Unswapped exercises are unaffected** — names, prefill, and volume unchanged.
8. **Session log** shows the swapped name with `(swapped)`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/progress/ProgressScreen.kt
git commit -m "feat: annotate swapped weeks on the progress chart"
```

---

## Verification

After all tasks, the full suite should show:

| Test class | Tests |
|---|---|
| `ActiveSessionStateTest` | 5 |
| `ExerciseLibraryTest` | 5 |
| `ExerciseOrderingTest` | 13 |
| `ExerciseSearchTest` | 14 |
| `ProgramJsonTest` | 2 |
| `SetDifficultyTest` | 6 |
| `SwapStateTest` | 10 |
| **Total** | **55** |

## Notes for the implementer

- **The volume SQL has no automated coverage.** Manual check 5 in Task 6 is the
  only guard against a silent zero or double-counted bodyweight. Do not skip it.
- **Both volume queries must change together.** Task 4 Step 3 greps for a count
  of 2 precisely because updating only one is easy and silent.
- **`fallbackToDestructiveMigration()` is active.** Task 1 Step 5 exists to stop
  an unregistered migration from deleting all user data.
- **Do not modify `SetLog`.** Attribution works because set logs keep pointing at
  the original planned exercise.
- **Do not change `GROUP BY pe.id, pe.name, s.weekNumber`.** It is what makes
  swapped work count toward the original exercise.
- **The swap does not change sets or reps.** Out of scope by design.
