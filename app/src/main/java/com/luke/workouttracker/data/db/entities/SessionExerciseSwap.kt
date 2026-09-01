package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * An exercise replaced for the duration of one session.
 *
 * Set logs continue to reference the original [plannedExerciseId], so swapped
 * work counts toward the original exercise's progression. Only the displayed
 * name and the bodyweight flag change.
 *
 * The composite primary key means re-swapping replaces the row rather than
 * accumulating history.
 */
@Entity(
    tableName = "session_exercise_swaps",
    primaryKeys = ["sessionId", "plannedExerciseId"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlannedExercise::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId"), Index("plannedExerciseId")],
)
data class SessionExerciseSwap(
    val sessionId: Long,
    val plannedExerciseId: Long,
    val replacementName: String,
    val isBodyweight: Boolean,
)
