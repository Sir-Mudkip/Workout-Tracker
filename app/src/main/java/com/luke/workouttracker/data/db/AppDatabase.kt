package com.luke.workouttracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luke.workouttracker.data.db.dao.ExerciseLibraryDao
import com.luke.workouttracker.data.db.dao.PeakDao
import com.luke.workouttracker.data.db.dao.ProgramDao
import com.luke.workouttracker.data.db.dao.SessionDao
import com.luke.workouttracker.data.db.entities.CustomExercise
import com.luke.workouttracker.data.db.entities.PeakResult
import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.data.db.entities.WorkoutDay
import com.luke.workouttracker.data.db.entities.WorkoutSession

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
abstract class AppDatabase : RoomDatabase() {
    abstract fun programDao(): ProgramDao
    abstract fun sessionDao(): SessionDao
    abstract fun peakDao(): PeakDao

    abstract fun exerciseLibraryDao(): ExerciseLibraryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE set_logs ADD COLUMN restAfterMs INTEGER")
            }
        }

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Nullable: existing logs stay unrated.
                db.execSQL("ALTER TABLE set_logs ADD COLUMN difficulty INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE planned_exercises ADD COLUMN isBodyweight INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS peak_results (
                        programId INTEGER NOT NULL,
                        lift TEXT NOT NULL,
                        startingOneRm REAL,
                        endingOneRm REAL,
                        PRIMARY KEY(programId, lift),
                        FOREIGN KEY(programId) REFERENCES programs(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_peak_results_programId ON peak_results(programId)")
            }
        }
    }
}
