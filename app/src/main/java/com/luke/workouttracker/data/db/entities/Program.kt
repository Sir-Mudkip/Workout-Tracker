package com.luke.workouttracker.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysPerWeek: Int,
    val totalWeeks: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
