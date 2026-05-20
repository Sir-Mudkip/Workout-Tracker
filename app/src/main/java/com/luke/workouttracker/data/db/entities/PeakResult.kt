package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "peak_results",
    primaryKeys = ["programId", "lift"],
    foreignKeys = [
        ForeignKey(
            entity = Program::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("programId")],
)
data class PeakResult(
    val programId: Long,
    val lift: String,
    val startingOneRm: Double? = null,
    val endingOneRm: Double? = null,
)
