package com.luke.workouttracker.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.luke.workouttracker.data.db.entities.CustomExercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseLibraryDao {
    @Query("SELECT * FROM custom_exercises ORDER BY name")
    fun observeCustom(): Flow<List<CustomExercise>>

    /** Ignores the insert when the name already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: CustomExercise)
}
