package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_days",
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
data class WorkoutDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val dayIndex: Int,
    val name: String,
)
