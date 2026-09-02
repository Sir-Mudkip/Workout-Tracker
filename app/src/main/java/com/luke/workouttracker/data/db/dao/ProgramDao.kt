package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.WorkoutDay
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Program>>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: Long): Program?

    @Query("SELECT * FROM programs WHERE id = :id")
    fun observeById(id: Long): Flow<Program?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(program: Program): Long

    @Update
    suspend fun update(program: Program)

    @Delete
    suspend fun delete(program: Program)

    // Days
    @Insert
    suspend fun insertDay(day: WorkoutDay): Long

    @Query("SELECT * FROM workout_days WHERE programId = :programId ORDER BY dayIndex")
    suspend fun daysForProgram(programId: Long): List<WorkoutDay>

    @Query("SELECT * FROM workout_days WHERE programId = :programId ORDER BY dayIndex")
    fun observeDaysForProgram(programId: Long): Flow<List<WorkoutDay>>

    @Query("SELECT * FROM workout_days WHERE id = :id")
    suspend fun getDay(id: Long): WorkoutDay?

    @Update
    suspend fun updateDay(day: WorkoutDay)

    @Delete
    suspend fun deleteDay(day: WorkoutDay)

    // Exercises
    @Insert
    suspend fun insertExercise(exercise: PlannedExercise): Long

    @Query("SELECT * FROM planned_exercises WHERE dayId = :dayId ORDER BY orderInDay, id")
    suspend fun exercisesForDay(dayId: Long): List<PlannedExercise>

    @Query("SELECT * FROM planned_exercises WHERE dayId = :dayId ORDER BY orderInDay, id")
    fun observeExercisesForDay(dayId: Long): Flow<List<PlannedExercise>>

    @Update
    suspend fun updateExercise(exercise: PlannedExercise)

    @Delete
    suspend fun deleteExercise(exercise: PlannedExercise)

    @Query("DELETE FROM planned_exercises WHERE dayId = :dayId")
    suspend fun deleteExercisesForDay(dayId: Long)

    // Sets
    @Insert
    suspend fun insertSet(set: PlannedSet): Long

    @Query("SELECT * FROM planned_sets WHERE exerciseId = :exerciseId ORDER BY setNumber")
    suspend fun setsForExercise(exerciseId: Long): List<PlannedSet>

    @Query("SELECT * FROM planned_sets WHERE exerciseId IN (:exerciseIds) ORDER BY exerciseId, setNumber")
    suspend fun setsForExercises(exerciseIds: List<Long>): List<PlannedSet>

    @Update
    suspend fun updateSet(set: PlannedSet)

    @Delete
    suspend fun deleteSet(set: PlannedSet)
}
