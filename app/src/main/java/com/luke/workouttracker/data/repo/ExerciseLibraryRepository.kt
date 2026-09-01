package com.luke.workouttracker.data.repo

import com.luke.workouttracker.data.db.dao.ExerciseLibraryDao
import com.luke.workouttracker.data.db.entities.CustomExercise
import com.luke.workouttracker.data.library.STOCK_EXERCISES
import com.luke.workouttracker.data.library.mergeExerciseNames
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ExerciseLibraryRepository @Inject constructor(
    private val dao: ExerciseLibraryDao,
) {
    /** Stock names merged with the user's saved ones, sorted and deduplicated. */
    fun observeNames(): Flow<List<String>> =
        dao.observeCustom().map { custom ->
            mergeExerciseNames(STOCK_EXERCISES, custom.map { it.name })
        }

    /** No-op for a blank name; the unique index makes a repeat save harmless. */
    suspend fun save(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.insert(CustomExercise(name = trimmed))
    }
}
