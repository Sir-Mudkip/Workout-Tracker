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
