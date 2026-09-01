package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An exercise name the user saved from the picker.
 *
 * Stock names live in code (`STOCK_EXERCISES`); only user additions are stored.
 * The unique index on [name] makes a repeated save a no-op.
 */
@Entity(
    tableName = "custom_exercises",
    indices = [Index(value = ["name"], unique = true)],
)
data class CustomExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)
