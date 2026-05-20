package com.luke.workouttracker.data.repo

import com.luke.workouttracker.data.db.dao.ExerciseWeeklyVolume
import com.luke.workouttracker.data.db.dao.SessionDao
import com.luke.workouttracker.data.db.dao.SetLogRow
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.data.db.entities.WorkoutSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
) {
    suspend fun startOrResumeSession(programId: Long, weekNumber: Int, dayId: Long): Long {
        val existing = dao.findSession(programId, weekNumber, dayId)
        if (existing != null && existing.completedAt == null) return existing.id
        return dao.insertSession(
            WorkoutSession(programId = programId, weekNumber = weekNumber, dayId = dayId)
        )
    }

    suspend fun getSession(id: Long): WorkoutSession? = dao.getSession(id)

    suspend fun logSet(
        sessionId: Long,
        plannedExerciseId: Long,
        setNumber: Int,
        reps: Int,
        weight: Double,
    ): Long = dao.insertSetLog(
        SetLog(
            sessionId = sessionId,
            plannedExerciseId = plannedExerciseId,
            setNumber = setNumber,
            actualReps = reps,
            actualWeight = weight,
        )
    )

    suspend fun setRestAfter(setLogId: Long, restMs: Long) {
        dao.updateRestAfter(setLogId, restMs)
    }

    suspend fun completeSession(sessionId: Long) {
        val s = dao.getSession(sessionId) ?: return
        dao.updateSession(s.copy(completedAt = System.currentTimeMillis()))
    }

    suspend fun logsForSession(sessionId: Long): List<SetLog> = dao.logsForSession(sessionId)

    fun observeLogs(sessionId: Long): Flow<List<SetLog>> = dao.observeLogsForSession(sessionId)

    suspend fun priorLogs(programId: Long, plannedExerciseId: Long): List<SetLogRow> =
        dao.setLogsForExerciseInProgram(programId, plannedExerciseId)

    fun observeWeeklyVolume(programId: Long, bodyweight: Double): Flow<List<ExerciseWeeklyVolume>> =
        dao.observeWeeklyVolume(programId, bodyweight)

    suspend fun weeklyVolume(programId: Long, bodyweight: Double): List<ExerciseWeeklyVolume> =
        dao.weeklyVolume(programId, bodyweight)

    suspend fun allSessions(programId: Long): List<WorkoutSession> = dao.sessionsForProgram(programId)

    fun observeAllCompletedSessions(): Flow<List<WorkoutSession>> = dao.observeAllCompletedSessions()

    fun observeCompletedSessions(programId: Long): Flow<List<WorkoutSession>> =
        dao.observeCompletedSessions(programId)
}
