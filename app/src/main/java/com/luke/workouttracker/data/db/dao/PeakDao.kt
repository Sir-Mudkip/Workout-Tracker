package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luke.workouttracker.data.db.entities.PeakResult
import kotlinx.coroutines.flow.Flow

@Dao
interface PeakDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: PeakResult)

    @Query("SELECT * FROM peak_results WHERE programId = :programId ORDER BY lift")
    fun observeForProgram(programId: Long): Flow<List<PeakResult>>

    @Query("SELECT * FROM peak_results WHERE programId = :programId AND lift = :lift LIMIT 1")
    suspend fun getOne(programId: Long, lift: String): PeakResult?
}
