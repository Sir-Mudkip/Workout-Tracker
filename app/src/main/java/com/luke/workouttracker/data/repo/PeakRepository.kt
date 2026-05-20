package com.luke.workouttracker.data.repo

import com.luke.workouttracker.data.db.dao.PeakDao
import com.luke.workouttracker.data.db.entities.PeakLift
import com.luke.workouttracker.data.db.entities.PeakResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class PeakRepository @Inject constructor(
    private val dao: PeakDao,
) {
    fun observeForProgram(programId: Long): Flow<List<PeakResult>> = dao.observeForProgram(programId)

    suspend fun setStarting(programId: Long, lift: PeakLift, value: Double?) {
        val existing = dao.getOne(programId, lift.name)
        dao.upsert((existing ?: PeakResult(programId, lift.name)).copy(startingOneRm = value))
    }

    suspend fun setEnding(programId: Long, lift: PeakLift, value: Double?) {
        val existing = dao.getOne(programId, lift.name)
        dao.upsert((existing ?: PeakResult(programId, lift.name)).copy(endingOneRm = value))
    }
}
