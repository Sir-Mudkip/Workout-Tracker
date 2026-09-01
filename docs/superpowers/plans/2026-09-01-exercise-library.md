# Exercise Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick exercise names from a searchable library when building a program, free-typing anything not in it and optionally saving that name for next time.

**Architecture:** Stock exercise names ship as a Kotlin `List<String>` in code; user-saved names live in a one-column Room table. A repository merges the two into a single `Flow<List<String>>`. A pure `filterExercises` function does the searching, and a shared `ExercisePicker` composable presents it — used by the day editor now and by the mid-session swap feature (#4) later.

**Tech Stack:** Kotlin 2.0.20, Jetpack Compose (BOM 2024.09.00), Material 3, Room, Hilt, kotlinx.coroutines Flow, JUnit 4

**Spec:** `docs/superpowers/specs/2026-09-01-exercise-library-design.md`

## Global Constraints

- **Database version goes 4 → 5.** Version 4 is current (set by the set-difficulty feature).
- **`MIGRATION_4_5` must be registered in BOTH `AppDatabase.Companion` and `AppModule.provideDatabase`'s `addMigrations(...)`.** `fallbackToDestructiveMigration()` is active — an unregistered migration silently destroys all user data instead of failing.
- **No abbreviations in exercise names.** `Dumbbell`, never `DB`.
- **The unmodified name is the conventional default.** `Barbell Bench Press`, never `Flat Medium Grip Barbell Bench Press`.
- **Search result cap is 8.**
- **The save-to-library checkbox defaults to checked.**
- **The library is not a foreign key.** `PlannedExercise.name` stays a plain `String` column. No existing entity changes.
- **Package root:** `com.luke.workouttracker`
- **Build commands** (no `gradlew` in this repo; Android Studio generates it on sync — if absent, use the distribution and JDK that Studio downloaded):
  ```bash
  export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
  export ANDROID_HOME=$HOME/Android/Sdk
  GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
  "$GRADLE" testDebugUnitTest --console=plain
  ```
  Android Studio's bundled JBR is version 25, which Gradle 8.7 rejects. Use JDK 21.

---

### Task 1: Pure search and merge logic

No Android or Room dependencies. This is the whole search behaviour, and it is fully testable in isolation.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/data/library/ExerciseSearch.kt`
- Test: `app/src/test/java/com/luke/workouttracker/ExerciseSearchTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `fun filterExercises(all: List<String>, query: String, limit: Int = 8): List<String>`
  - `fun mergeExerciseNames(stock: List<String>, custom: List<String>): List<String>`

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/luke/workouttracker/ExerciseSearchTest.kt`:

```kotlin
package com.luke.workouttracker

import com.luke.workouttracker.data.library.filterExercises
import com.luke.workouttracker.data.library.mergeExerciseNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {

    private val sample = listOf(
        "Barbell Bench Press",
        "Bench Dip",
        "Close Grip Barbell Bench Press",
        "Neutral Grip Lat Pulldown",
        "Wide Grip Lat Pulldown",
        "Pull Up",
    )

    // filterExercises — matching

    @Test fun a_blank_query_returns_everything_up_to_the_limit() {
        assertEquals(sample, filterExercises(sample, "", limit = 10))
    }

    @Test fun a_blank_query_still_respects_the_limit() {
        assertEquals(3, filterExercises(sample, "", limit = 3).size)
    }

    @Test fun matching_is_case_insensitive_in_both_directions() {
        assertTrue(filterExercises(sample, "BENCH DIP").contains("Bench Dip"))
        assertTrue(filterExercises(sample, "bench dip").contains("Bench Dip"))
    }

    @Test fun every_token_must_match_somewhere_in_the_name() {
        // "lat pull" matches "Neutral Grip Lat Pulldown": "lat" and "pull" both appear.
        val result = filterExercises(sample, "lat pull")
        assertTrue(result.contains("Neutral Grip Lat Pulldown"))
        assertTrue(result.contains("Wide Grip Lat Pulldown"))
        assertTrue(!result.contains("Pull Up"))
    }

    @Test fun tokens_may_match_in_any_order() {
        assertTrue(filterExercises(sample, "pulldown lat").contains("Neutral Grip Lat Pulldown"))
    }

    @Test fun a_query_matching_nothing_returns_an_empty_list() {
        assertEquals(emptyList<String>(), filterExercises(sample, "zercher"))
    }

    @Test fun extra_whitespace_in_the_query_is_ignored() {
        assertTrue(filterExercises(sample, "  lat   pull  ").contains("Wide Grip Lat Pulldown"))
    }

    // filterExercises — ranking

    @Test fun word_boundary_matches_rank_above_mid_word_matches() {
        // "bench" starts a word in all three, but "Barbell Bench Press" and
        // "Bench Dip" should not be pushed below by the longer name.
        val result = filterExercises(sample, "bench")
        assertEquals(3, result.size)
        assertTrue(result.indexOf("Bench Dip") < result.indexOf("Close Grip Barbell Bench Press"))
    }

    @Test fun a_name_starting_with_the_query_ranks_first() {
        val result = filterExercises(sample, "pull")
        assertEquals("Pull Up", result.first())
    }

    @Test fun results_are_capped_at_the_limit() {
        val many = (1..50).map { "Exercise $it" }
        assertEquals(8, filterExercises(many, "exercise").size)
    }

    // mergeExerciseNames

    @Test fun merging_combines_both_sources_alphabetically() {
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            mergeExerciseNames(stock = listOf("Gamma", "Alpha"), custom = listOf("Beta")),
        )
    }

    @Test fun a_custom_name_duplicating_a_stock_name_appears_once() {
        val merged = mergeExerciseNames(
            stock = listOf("Barbell Bench Press"),
            custom = listOf("Barbell Bench Press"),
        )
        assertEquals(listOf("Barbell Bench Press"), merged)
    }

    @Test fun duplicate_detection_ignores_case_and_keeps_the_custom_spelling() {
        val merged = mergeExerciseNames(
            stock = listOf("Barbell Bench Press"),
            custom = listOf("barbell bench press"),
        )
        assertEquals(listOf("barbell bench press"), merged)
    }

    @Test fun merging_empty_sources_yields_an_empty_list() {
        assertEquals(emptyList<String>(), mergeExerciseNames(emptyList(), emptyList()))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: FAIL — compilation error, `Unresolved reference: filterExercises`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/luke/workouttracker/data/library/ExerciseSearch.kt`:

```kotlin
package com.luke.workouttracker.data.library

/**
 * Names matching [query], best matches first, capped at [limit].
 *
 * Every whitespace-separated token in the query must appear somewhere in the
 * name (case-insensitive), so "lat pull" finds "Neutral Grip Lat Pulldown".
 * A blank query returns everything up to [limit].
 */
fun filterExercises(all: List<String>, query: String, limit: Int = 8): List<String> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return all.take(limit)

    return all
        .mapNotNull { name ->
            val lower = name.lowercase()
            if (tokens.all { lower.contains(it) }) name to rank(lower, tokens) else null
        }
        .sortedWith(compareBy({ it.second }, { it.first }))
        .map { it.first }
        .take(limit)
}

/** Lower is better: 0 = name starts with the query, 1 = token starts a word, 2 = mid-word only. */
private fun rank(lowerName: String, tokens: List<String>): Int {
    val joined = tokens.joinToString(" ")
    if (lowerName.startsWith(joined)) return 0
    val words = lowerName.split(Regex("\\s+"))
    return if (tokens.all { token -> words.any { it.startsWith(token) } }) 1 else 2
}

/**
 * Stock and custom names as one sorted list, deduplicated case-insensitively.
 * A custom entry duplicating a stock one wins, so the user's own spelling shows.
 */
fun mergeExerciseNames(stock: List<String>, custom: List<String>): List<String> {
    val byLowercase = LinkedHashMap<String, String>()
    stock.forEach { byLowercase[it.lowercase()] = it }
    custom.forEach { byLowercase[it.lowercase()] = it }
    return byLowercase.values.sortedBy { it.lowercase() }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run the same command as Step 2.
Expected: PASS — `ExerciseSearchTest` 14 tests, 0 failures.

Confirm the count:
```bash
python3 -c "
import xml.etree.ElementTree as ET
r=ET.parse('app/build/test-results/testDebugUnitTest/TEST-com.luke.workouttracker.ExerciseSearchTest.xml').getroot()
print(r.get('tests'),'tests',r.get('failures'),'failures')"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/library/ExerciseSearch.kt \
        app/src/test/java/com/luke/workouttracker/ExerciseSearchTest.kt
git commit -m "feat: add exercise name search and merge logic"
```

---

### Task 2: Stock exercise list

Pure data. No tests beyond a sanity check on the invariants the spec sets, because a list of string literals cannot meaningfully fail any other way.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/data/library/ExerciseLibrary.kt`
- Test: `app/src/test/java/com/luke/workouttracker/ExerciseLibraryTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `val STOCK_EXERCISES: List<String>` in package `com.luke.workouttracker.data.library`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/luke/workouttracker/ExerciseLibraryTest.kt`:

```kotlin
package com.luke.workouttracker

import com.luke.workouttracker.data.library.STOCK_EXERCISES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {

    @Test fun the_list_has_the_expected_number_of_entries() {
        assertEquals(140, STOCK_EXERCISES.size)
    }

    @Test fun there_are_no_duplicate_names() {
        val duplicates = STOCK_EXERCISES.groupBy { it.lowercase() }
            .filter { it.value.size > 1 }
            .keys
        assertEquals(emptySet<String>(), duplicates)
    }

    @Test fun no_name_is_blank_or_has_stray_whitespace() {
        STOCK_EXERCISES.forEach { name ->
            assertTrue("blank entry", name.isNotBlank())
            assertEquals("untrimmed: '$name'", name.trim(), name)
            assertTrue("double space in '$name'", !name.contains("  "))
        }
    }

    @Test fun names_avoid_abbreviations() {
        // The spec requires spelled-out equipment names so search stays predictable.
        val banned = listOf("DB ", "BB ", "OHP", "RDL", "SLDL")
        STOCK_EXERCISES.forEach { name ->
            banned.forEach { abbrev ->
                assertTrue("'$name' uses abbreviation '$abbrev'", !name.contains(abbrev))
            }
        }
    }

    @Test fun the_documented_variant_examples_are_all_present() {
        listOf(
            "High Bar Back Squat",
            "Low Bar Back Squat",
            "Barbell Bench Press",
            "Close Grip Barbell Bench Press",
            "Neutral Grip Lat Pulldown",
            "Wide Grip Lat Pulldown",
            "Incline Dumbbell Press",
            "Flat Dumbbell Press",
            "Neutral Grip Pull Up",
        ).forEach { assertTrue("missing: $it", STOCK_EXERCISES.contains(it)) }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: FAIL — `Unresolved reference: STOCK_EXERCISES`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/luke/workouttracker/data/library/ExerciseLibrary.kt`. The comment groupings are for humans editing this file; the app treats it as one flat list.

```kotlin
package com.luke.workouttracker.data.library

/**
 * Exercise names shipped with the app.
 *
 * Naming convention: [Modifier] + [Equipment] + [Movement]. The modifier —
 * grip, stance, angle, or bar position — appears only when it distinguishes a
 * variant that trains differently. The unmodified name is the conventional
 * default, so "Barbell Bench Press" is flat and medium-grip. No abbreviations.
 *
 * Edit freely: this is shipped content, not user data, so changes need no
 * database migration. User-added names live in the `custom_exercises` table.
 */
val STOCK_EXERCISES: List<String> = listOf(
    // Squat / quad
    "High Bar Back Squat",
    "Low Bar Back Squat",
    "Front Squat",
    "Safety Bar Squat",
    "Goblet Squat",
    "Bulgarian Split Squat",
    "Walking Lunge",
    "Reverse Lunge",
    "Step Up",
    "Hack Squat",
    "Leg Press",
    "Pendulum Squat",
    "Sissy Squat",
    "Leg Extension",

    // Hinge / posterior chain
    "Conventional Deadlift",
    "Sumo Deadlift",
    "Romanian Deadlift",
    "Stiff Leg Deadlift",
    "Trap Bar Deadlift",
    "Deficit Deadlift",
    "Rack Pull",
    "Good Morning",
    "Barbell Hip Thrust",
    "Single Leg Hip Thrust",
    "Glute Bridge",
    "Back Extension",
    "Reverse Hyperextension",
    "Seated Leg Curl",
    "Lying Leg Curl",
    "Nordic Curl",
    "Cable Pull Through",
    "Kettlebell Swing",

    // Horizontal push
    "Barbell Bench Press",
    "Close Grip Barbell Bench Press",
    "Wide Grip Barbell Bench Press",
    "Incline Barbell Bench Press",
    "Decline Barbell Bench Press",
    "Flat Dumbbell Press",
    "Incline Dumbbell Press",
    "Decline Dumbbell Press",
    "Neutral Grip Dumbbell Press",
    "Machine Chest Press",
    "Incline Machine Chest Press",
    "Smith Machine Bench Press",
    "Push Up",
    "Weighted Push Up",
    "Deficit Push Up",
    "Dip",
    "Weighted Dip",
    "Cable Fly",
    "Low To High Cable Fly",
    "High To Low Cable Fly",
    "Pec Deck",

    // Vertical push
    "Standing Overhead Press",
    "Seated Overhead Press",
    "Seated Dumbbell Shoulder Press",
    "Standing Dumbbell Shoulder Press",
    "Neutral Grip Dumbbell Shoulder Press",
    "Arnold Press",
    "Push Press",
    "Machine Shoulder Press",
    "Landmine Press",

    // Horizontal pull
    "Barbell Row",
    "Pendlay Row",
    "Yates Row",
    "Single Arm Dumbbell Row",
    "Chest Supported Dumbbell Row",
    "Chest Supported T Bar Row",
    "T Bar Row",
    "Seated Cable Row",
    "Neutral Grip Seated Cable Row",
    "Wide Grip Seated Cable Row",
    "Machine Row",
    "Inverted Row",
    "Meadows Row",
    "Kroc Row",

    // Vertical pull
    "Pull Up",
    "Weighted Pull Up",
    "Chin Up",
    "Weighted Chin Up",
    "Neutral Grip Pull Up",
    "Wide Grip Pull Up",
    "Lat Pulldown",
    "Wide Grip Lat Pulldown",
    "Neutral Grip Lat Pulldown",
    "Close Grip Lat Pulldown",
    "Reverse Grip Lat Pulldown",
    "Single Arm Lat Pulldown",
    "Straight Arm Pulldown",
    "Machine Pullover",

    // Shoulders / delts
    "Dumbbell Lateral Raise",
    "Cable Lateral Raise",
    "Machine Lateral Raise",
    "Leaning Cable Lateral Raise",
    "Front Raise",
    "Rear Delt Fly",
    "Cable Rear Delt Fly",
    "Reverse Pec Deck",
    "Face Pull",
    "Upright Row",
    "Barbell Shrug",
    "Dumbbell Shrug",

    // Biceps
    "Barbell Curl",
    "EZ Bar Curl",
    "Dumbbell Curl",
    "Alternating Dumbbell Curl",
    "Hammer Curl",
    "Incline Dumbbell Curl",
    "Preacher Curl",
    "Spider Curl",
    "Cable Curl",
    "Concentration Curl",
    "Reverse Curl",

    // Triceps
    "Skull Crusher",
    "EZ Bar Skull Crusher",
    "Overhead Cable Extension",
    "Overhead Dumbbell Extension",
    "Cable Triceps Pushdown",
    "Rope Triceps Pushdown",
    "Reverse Grip Pushdown",
    "Triceps Kickback",
    "JM Press",
    "Bench Dip",

    // Core
    "Plank",
    "Side Plank",
    "Hanging Leg Raise",
    "Hanging Knee Raise",
    "Cable Crunch",
    "Machine Crunch",
    "Ab Wheel Rollout",
    "Russian Twist",
    "Pallof Press",
    "Decline Sit Up",
    "Dead Bug",
    "Toes To Bar",

    // Calves
    "Standing Calf Raise",
    "Seated Calf Raise",
    "Leg Press Calf Raise",
    "Smith Machine Calf Raise",
    "Single Leg Calf Raise",
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run the same command as Step 2.
Expected: PASS — `ExerciseLibraryTest` 5 tests, 0 failures.

If `the_list_has_the_expected_number_of_entries` fails, count the actual entries and fix the list, not the test — 140 is the spec's number.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/library/ExerciseLibrary.kt \
        app/src/test/java/com/luke/workouttracker/ExerciseLibraryTest.kt
git commit -m "feat: add stock exercise list"
```

---

### Task 3: Custom exercise persistence

Entity, DAO, migration, DI registration. This task carries the destructive-migration risk, so the migration registration is verified explicitly before committing.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/data/db/entities/CustomExercise.kt`
- Create: `app/src/main/java/com/luke/workouttracker/data/db/dao/ExerciseLibraryDao.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/luke/workouttracker/di/AppModule.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks
- Produces:
  - `data class CustomExercise(val id: Long = 0, val name: String, val createdAt: Long = System.currentTimeMillis())`
  - `interface ExerciseLibraryDao` with `fun observeCustom(): Flow<List<CustomExercise>>` and `suspend fun insert(exercise: CustomExercise)`
  - `AppDatabase.exerciseLibraryDao(): ExerciseLibraryDao`
  - `AppDatabase.Companion.MIGRATION_4_5`

- [ ] **Step 1: Create the entity**

Create `app/src/main/java/com/luke/workouttracker/data/db/entities/CustomExercise.kt`:

```kotlin
package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An exercise name the user saved from the picker.
 *
 * Stock names live in code (`STOCK_EXERCISES`); only user additions are stored.
 * The unique index on [name] makes a repeated save a no-op.
 */
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

- [ ] **Step 2: Create the DAO**

Create `app/src/main/java/com/luke/workouttracker/data/db/dao/ExerciseLibraryDao.kt`:

```kotlin
package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luke.workouttracker.data.db.entities.CustomExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseLibraryDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name")
    fun observeCustom(): Flow<List<CustomExercise>>

    /** Ignores the insert when the name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: CustomExercise)
}
```

- [ ] **Step 3: Register the entity, DAO, and migration in AppDatabase**

In `app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt`:

Add the imports:
```kotlin
import com.luke.workouttracker.data.db.dao.ExerciseLibraryDao
import com.luke.workouttracker.data.db.entities.CustomExercise
```

Add `CustomExercise::class` to the `entities` array, and change `version = 4` to `version = 5`:
```kotlin
@Database(
    entities = [
        Program::class,
        WorkoutDay::class,
        PlannedExercise::class,
        PlannedSet::class,
        WorkoutSession::class,
        SetLog::class,
        PeakResult::class,
        CustomExercise::class,
    ],
    version = 5,
    exportSchema = false,
)
```

Add the DAO accessor beside the existing ones:
```kotlin
abstract fun exerciseLibraryDao(): ExerciseLibraryDao
```

Add the migration inside `companion object`, beside `MIGRATION_3_4`:
```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_exercises (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_exercises_name ON custom_exercises(name)"
        )
    }
}
```

- [ ] **Step 4: Register the migration and DAO provider in AppModule**

In `app/src/main/java/com/luke/workouttracker/di/AppModule.kt`, add `MIGRATION_4_5` to the existing `addMigrations(...)` call:

```kotlin
.addMigrations(
    AppDatabase.MIGRATION_1_2,
    AppDatabase.MIGRATION_2_3,
    AppDatabase.MIGRATION_3_4,
    AppDatabase.MIGRATION_4_5,
)
```

Add the import and the provider:
```kotlin
import com.luke.workouttracker.data.db.dao.ExerciseLibraryDao
```
```kotlin
@Provides
fun provideExerciseLibraryDao(db: AppDatabase): ExerciseLibraryDao = db.exerciseLibraryDao()
```

- [ ] **Step 5: Verify the migration is registered in both places**

This guard exists because `fallbackToDestructiveMigration()` turns a missing registration into silent data loss.

Run:
```bash
grep -n "MIGRATION_4_5" \
  app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
  app/src/main/java/com/luke/workouttracker/di/AppModule.kt
grep -n "version = " app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt
```
Expected: `MIGRATION_4_5` appears in **both** files (definition in `AppDatabase.kt`, registration in `AppModule.kt`), and `version = 5`.

Do not proceed if either is missing.

- [ ] **Step 6: Build to verify Room generates the schema**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: BUILD SUCCESSFUL. Room's annotation processor validates the entity against the migration at compile time; a mismatch between the `CREATE TABLE` columns and the entity fields fails here.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/db/entities/CustomExercise.kt \
        app/src/main/java/com/luke/workouttracker/data/db/dao/ExerciseLibraryDao.kt \
        app/src/main/java/com/luke/workouttracker/data/db/AppDatabase.kt \
        app/src/main/java/com/luke/workouttracker/di/AppModule.kt
git commit -m "feat: add custom_exercises table and migration 4->5"
```

---

### Task 4: Library repository

Merges the two sources behind one Flow. Follows the existing `@Singleton class ... @Inject constructor` repository pattern used by `ProgramRepository` and `SessionRepository`.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/data/repo/ExerciseLibraryRepository.kt`

**Interfaces:**
- Consumes: `STOCK_EXERCISES` (Task 2), `mergeExerciseNames` (Task 1), `ExerciseLibraryDao` (Task 3)
- Produces:
  - `fun observeNames(): Flow<List<String>>`
  - `suspend fun save(name: String)`

- [ ] **Step 1: Write the implementation**

There is no unit test for this task: it is a three-line adapter over a DAO, and the logic it delegates to (`mergeExerciseNames`) is already tested in Task 1. Testing it would require a Room harness, which this codebase does not have for any repository.

Create `app/src/main/java/com/luke/workouttracker/data/repo/ExerciseLibraryRepository.kt`:

```kotlin
package com.luke.workouttracker.data.repo

import com.luke.workouttracker.data.db.dao.ExerciseLibraryDao
import com.luke.workouttracker.data.db.entities.CustomExercise
import com.luke.workouttracker.data.library.STOCK_EXERCISES
import com.luke.workouttracker.data.library.mergeExerciseNames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ExerciseLibraryRepository @Inject constructor(
    private val dao: ExerciseLibraryDao,
) {
    /** Stock names merged with the user's saved ones, sorted and deduplicated. */
    fun observeNames(): Flow<List<String>> =
        dao.observeCustom().map { custom ->
            mergeExerciseNames(STOCK_EXERCISES, custom.map { it.name })
        }

    /** No-op for a blank name; the unique index makes a repeat save harmless. */
    suspend fun save(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.insert(CustomExercise(name = trimmed))
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: BUILD SUCCESSFUL. Hilt resolves `ExerciseLibraryDao` via the provider added in Task 3.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/data/repo/ExerciseLibraryRepository.kt
git commit -m "feat: add exercise library repository"
```

---

### Task 5: ExercisePicker composable

The shared UI. Written to be reused by feature #4's swap dialog, so it takes its name list as a parameter rather than reaching for a ViewModel.

**Files:**
- Create: `app/src/main/java/com/luke/workouttracker/ui/library/ExercisePicker.kt`

**Interfaces:**
- Consumes: `filterExercises` (Task 1)
- Produces:
  ```kotlin
  @Composable
  fun ExercisePicker(
      names: List<String>,
      query: String,
      onQueryChange: (String) -> Unit,
      saveToLibrary: Boolean,
      onSaveToLibraryChange: (Boolean) -> Unit,
      onNameSelected: (String) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```

State is hoisted to the caller so the day editor (and later the swap dialog) owns the chosen name.

- [ ] **Step 1: Write the implementation**

This is UI with no extractable logic — the searching and ranking it displays are already tested in Task 1. Verification is the manual pass in Task 6.

Create `app/src/main/java/com/luke/workouttracker/ui/library/ExercisePicker.kt`:

```kotlin
package com.luke.workouttracker.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luke.workouttracker.data.library.filterExercises

/**
 * Name field backed by the exercise library.
 *
 * Shows up to 8 matches as the user types. When nothing matches, offers to use
 * the typed text as-is, with a checkbox to add it to the library.
 *
 * State is hoisted: the caller owns [query] and [saveToLibrary].
 */
@Composable
fun ExercisePicker(
    names: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    saveToLibrary: Boolean,
    onSaveToLibraryChange: (Boolean) -> Unit,
    onNameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches = filterExercises(names, query)
    val isExactMatch = names.any { it.equals(query.trim(), ignoreCase = true) }
    val showCreateOption = query.isNotBlank() && !isExactMatch

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Exercise") },
            modifier = Modifier.fillMaxWidth(),
        )

        matches.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNameSelected(name) }
                    .padding(vertical = 10.dp),
            )
        }

        if (showCreateOption) {
            if (matches.isNotEmpty()) HorizontalDivider()
            Text(
                "Use \"${query.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNameSelected(query.trim()) }
                    .padding(vertical = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveToLibrary, onCheckedChange = onSaveToLibraryChange)
                Text("Also save to library", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: BUILD SUCCESSFUL.

If `HorizontalDivider` is unresolved, this Material 3 version still calls it `Divider` — swap the import and the call.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/library/ExercisePicker.kt
git commit -m "feat: add shared exercise picker composable"
```

---

### Task 6: Day editor integration

Wires the picker into the one place exercises are created. `addExercise` has no other caller, so this is the complete integration.

**Files:**
- Modify: `app/src/main/java/com/luke/workouttracker/ui/programs/DayEditorScreen.kt`

**Interfaces:**
- Consumes: `ExercisePicker` (Task 5), `ExerciseLibraryRepository` (Task 4)
- Produces: nothing for later tasks in this plan. Feature #4 will reuse `ExercisePicker` directly.

- [ ] **Step 1: Add the library to the ViewModel**

In `DayEditorViewModel`, inject the repository and expose the names.

Add imports:
```kotlin
import com.luke.workouttracker.data.repo.ExerciseLibraryRepository
```

Change the constructor:
```kotlin
@HiltViewModel
class DayEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProgramRepository,
    private val library: ExerciseLibraryRepository,
) : ViewModel() {
```

Add the names flow beside the existing `exercises` flow:
```kotlin
val libraryNames: StateFlow<List<String>> =
    library.observeNames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
```

Change `addExercise` to take and honour the save flag:
```kotlin
fun addExercise(
    name: String,
    startingWeight: Double,
    sets: List<Pair<Int, Double?>>,
    isBodyweight: Boolean,
    saveToLibrary: Boolean,
) {
    viewModelScope.launch {
        if (saveToLibrary) library.save(name)
        repo.addExercise(dayId, name, startingWeight, sets, isBodyweight)
    }
}
```

- [ ] **Step 2: Pass the names into the dialog**

In `DayEditorScreen`, collect the names and hand them to the dialog. Add beside the existing `collectAsState` calls:
```kotlin
val libraryNames by vm.libraryNames.collectAsState()
```

Update the dialog call site:
```kotlin
if (showAdd) {
    AddExerciseDialog(
        libraryNames = libraryNames,
        onDismiss = { showAdd = false },
        onConfirm = { name, weight, setRows, isBw, save ->
            vm.addExercise(name, weight, setRows, isBw, save)
            showAdd = false
        },
    )
}
```

- [ ] **Step 3: Replace the name field in AddExerciseDialog**

Change the signature:
```kotlin
@Composable
private fun AddExerciseDialog(
    libraryNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        startingWeight: Double,
        sets: List<Pair<Int, Double?>>,
        isBodyweight: Boolean,
        saveToLibrary: Boolean,
    ) -> Unit,
) {
```

Add the save-flag state beside the existing `name` state, defaulting to checked per the spec:
```kotlin
var saveToLibrary by remember { mutableStateOf(true) }
```

Replace the `OutlinedTextField` for `Name` with the picker. Add the import:
```kotlin
import com.luke.workouttracker.ui.library.ExercisePicker
```

Replace this block:
```kotlin
OutlinedTextField(
    value = name, onValueChange = { name = it },
    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
)
```

with:
```kotlin
ExercisePicker(
    names = libraryNames,
    query = name,
    onQueryChange = { name = it },
    saveToLibrary = saveToLibrary,
    onSaveToLibraryChange = { saveToLibrary = it },
    onNameSelected = { name = it },
    modifier = Modifier.fillMaxWidth(),
)
```

Update the confirm button to pass the flag. Selecting an existing library name means there is nothing to save, so compute it rather than passing the checkbox blindly:
```kotlin
onClick = {
    val parsed = rows.map { row -> row.reps.toInt() to row.weight.toDoubleOrNull() }
    val trimmed = name.trim()
    val alreadyKnown = libraryNames.any { it.equals(trimmed, ignoreCase = true) }
    onConfirm(trimmed, effectiveStartWeight ?: 0.0, parsed, isBodyweight, saveToLibrary && !alreadyKnown)
},
```

- [ ] **Step 4: Build and run the full test suite**

Run:
```bash
export JAVA_HOME=$HOME/.jdks/jbr-21.0.11
export ANDROID_HOME=$HOME/Android/Sdk
GRADLE=$(find ~/.gradle/wrapper/dists/gradle-8.7-bin -name gradle -type f -path '*/bin/*' | head -1)
"$GRADLE" testDebugUnitTest --console=plain
```
Expected: BUILD SUCCESSFUL, all test classes passing:
```bash
for f in app/build/test-results/testDebugUnitTest/TEST-*.xml; do
  python3 -c "
import xml.etree.ElementTree as ET,sys
r=ET.parse('$f').getroot()
print(f\"{r.get('name'):<50} tests={r.get('tests')} failures={r.get('failures')}\")"
done
```

- [ ] **Step 5: Install and verify manually**

The app must be installed from Android Studio if a differently-signed build is already on the device — a CLI install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and uninstalling would destroy the data the migration must preserve.

Verify, in order:

1. **Existing data survives.** Open a program created before this change — all days, exercises, and logged sets are intact. If anything is missing, `MIGRATION_4_5` is wrong; stop and fix it.
2. **Suggestions appear.** Program → day → Add exercise. Typing `bench` lists `Bench Dip` and `Barbell Bench Press` before `Close Grip Barbell Bench Press`.
3. **Multi-token search.** Typing `lat pull` finds `Neutral Grip Lat Pulldown`.
4. **Selecting a suggestion** fills the field, and the save checkbox disappears (the name is an exact match).
5. **Unknown name, checkbox checked.** Type `Zercher Squat`, confirm `Use "Zercher Squat"` appears with the checkbox checked, add it. Re-open the dialog and type `zerch` — it now appears as a suggestion.
6. **Unknown name, checkbox cleared.** Type another unknown name, clear the checkbox, add it. Re-open and search — it does **not** appear.
7. **Reordering still works** (feature #2 regression check): the up/down arrows still move exercises.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/luke/workouttracker/ui/programs/DayEditorScreen.kt
git commit -m "feat: use exercise library when adding exercises"
```

---

## Verification

After all tasks, the full suite should show:

| Test class | Tests |
|---|---|
| `ActiveSessionStateTest` | 5 |
| `ExerciseOrderingTest` | 13 |
| `ProgramJsonTest` | 2 |
| `SetDifficultyTest` | 6 |
| `ExerciseSearchTest` | 14 |
| `ExerciseLibraryTest` | 5 |
| **Total** | **45** |

## Notes for the implementer

- **This repo has no `gradlew`.** Android Studio generates it on first sync. Until then use the Gradle distribution and JDK 21 that Studio downloaded — see Global Constraints. Studio's own JBR is version 25, which Gradle 8.7 rejects.
- **There is no Room test harness.** No repository or DAO in this codebase has unit tests. Do not add one for this feature; the untested-migration gap is recorded as a known risk in the spec and is being handled separately.
- **`fallbackToDestructiveMigration()` is active.** Any mistake in a migration deletes all user data silently rather than throwing. Task 3 Step 5 exists to catch that.
- **Do not add muscle group, equipment, or category fields.** Explicitly out of scope per the spec.
- **Do not populate the library from JSON import.** Explicitly out of scope per the spec.
