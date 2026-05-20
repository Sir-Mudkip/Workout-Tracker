package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.data.db.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow

data class ExerciseWeeklyVolume(
    val plannedExerciseId: Long,
    val exerciseName: String,
    val weekNumber: Int,
    val totalVolume: Double,
)

data class SetLogRow(
    val plannedExerciseId: Long,
    val weekNumber: Int,
    val setNumber: Int,
    val actualReps: Int,
    val actualWeight: Double,
)

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: WorkoutSession): Long

    @Update
    suspend fun updateSession(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: Long): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE programId = :programId ORDER BY weekNumber, startedAt")
    suspend fun sessionsForProgram(programId: Long): List<WorkoutSession>

    @Query("SELECT * FROM workout_sessions WHERE completedAt IS NOT NULL ORDER BY weekNumber")
    fun observeAllCompletedSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE programId = :programId AND completedAt IS NOT NULL ORDER BY weekNumber")
    fun observeCompletedSessions(programId: Long): Flow<List<WorkoutSession>>

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE programId = :programId AND weekNumber = :weekNumber AND dayId = :dayId
        ORDER BY startedAt DESC LIMIT 1
        """
    )
    suspend fun findSession(programId: Long, weekNumber: Int, dayId: Long): WorkoutSession?

    @Insert
    suspend fun insertSetLog(log: SetLog): Long

    @Query("UPDATE set_logs SET restAfterMs = :restMs WHERE id = :setLogId")
    suspend fun updateRestAfter(setLogId: Long, restMs: Long)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY plannedExerciseId, setNumber")
    suspend fun logsForSession(sessionId: Long): List<SetLog>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY plannedExerciseId, setNumber")
    fun observeLogsForSession(sessionId: Long): Flow<List<SetLog>>

    @Query(
        """
        SELECT sl.plannedExerciseId AS plannedExerciseId,
               s.weekNumber AS weekNumber,
               sl.setNumber AS setNumber,
               sl.actualReps AS actualReps,
               sl.actualWeight AS actualWeight
        FROM set_logs sl
        INNER JOIN workout_sessions s ON s.id = sl.sessionId
        WHERE s.programId = :programId AND sl.plannedExerciseId = :plannedExerciseId
        ORDER BY s.weekNumber, sl.setNumber
        """
    )
    suspend fun setLogsForExerciseInProgram(programId: Long, plannedExerciseId: Long): List<SetLogRow>

    @Query(
        """
        SELECT pe.id AS plannedExerciseId,
               pe.name AS exerciseName,
               s.weekNumber AS weekNumber,
               SUM(sl.actualReps * (sl.actualWeight + CASE WHEN pe.isBodyweight THEN :bodyweight ELSE 0 END)) AS totalVolume
        FROM set_logs sl
        INNER JOIN workout_sessions s ON s.id = sl.sessionId
        INNER JOIN planned_exercises pe ON pe.id = sl.plannedExerciseId
        WHERE s.programId = :programId
        GROUP BY pe.id, pe.name, s.weekNumber
        ORDER BY pe.name, s.weekNumber
        """
    )
    fun observeWeeklyVolume(programId: Long, bodyweight: Double): Flow<List<ExerciseWeeklyVolume>>

    @Query(
        """
        SELECT pe.id AS plannedExerciseId,
               pe.name AS exerciseName,
               s.weekNumber AS weekNumber,
               SUM(sl.actualReps * (sl.actualWeight + CASE WHEN pe.isBodyweight THEN :bodyweight ELSE 0 END)) AS totalVolume
        FROM set_logs sl
        INNER JOIN workout_sessions s ON s.id = sl.sessionId
        INNER JOIN planned_exercises pe ON pe.id = sl.plannedExerciseId
        WHERE s.programId = :programId
        GROUP BY pe.id, pe.name, s.weekNumber
        ORDER BY pe.name, s.weekNumber
        """
    )
    suspend fun weeklyVolume(programId: Long, bodyweight: Double): List<ExerciseWeeklyVolume>
}
