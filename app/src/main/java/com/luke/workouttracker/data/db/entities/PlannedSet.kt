package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_sets",
    foreignKeys = [
        ForeignKey(
            entity = PlannedExercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("exerciseId")],
)
data class PlannedSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setNumber: Int,
    val targetReps: Int,
    val targetWeightOverride: Double? = null,
)
