package com.luke.workouttracker.data.repo

import androidx.room.withTransaction
import com.luke.workouttracker.data.db.AppDatabase
import com.luke.workouttracker.data.db.dao.ProgramDao
import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.WorkoutDay
import com.luke.workouttracker.data.json.ProgramJson
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

data class FullProgram(
    val program: Program,
    val days: List<DayWithExercises>,
)

data class DayWithExercises(
    val day: WorkoutDay,
    val exercises: List<ExerciseWithSets>,
)

data class ExerciseWithSets(
    val exercise: PlannedExercise,
    val sets: List<PlannedSet>,
)

@Singleton
class ProgramRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: ProgramDao,
) {
    fun observePrograms(): Flow<List<Program>> = dao.observeAll()

    fun observeProgram(id: Long): Flow<Program?> = dao.observeById(id)

    fun observeDays(programId: Long): Flow<List<WorkoutDay>> = dao.observeDaysForProgram(programId)

    fun observeExercises(dayId: Long): Flow<List<PlannedExercise>> = dao.observeExercisesForDay(dayId)

    suspend fun getFullProgram(programId: Long): FullProgram? {
        val program = dao.getById(programId) ?: return null
        val days = dao.daysForProgram(programId).map { day ->
            val exercises = dao.exercisesForDay(day.id)
            val setsByExercise = if (exercises.isEmpty()) emptyMap()
            else dao.setsForExercises(exercises.map { it.id }).groupBy { it.exerciseId }
            DayWithExercises(
                day = day,
                exercises = exercises.map { ex ->
                    ExerciseWithSets(
                        exercise = ex,
                        sets = setsByExercise[ex.id].orEmpty().sortedBy { it.setNumber },
                    )
                },
            )
        }
        return FullProgram(program, days)
    }

    suspend fun setsForExercise(exerciseId: Long): List<PlannedSet> = dao.setsForExercise(exerciseId)

    suspend fun createProgram(
        name: String,
        daysPerWeek: Int,
        totalWeeks: Int,
        dayNames: List<String> = (1..daysPerWeek).map { "Day $it" },
    ): Long = db.withTransaction {
        val programId = dao.insert(
            Program(name = name, daysPerWeek = daysPerWeek, totalWeeks = totalWeeks)
        )
        dayNames.forEachIndexed { idx, dn ->
            dao.insertDay(WorkoutDay(programId = programId, dayIndex = idx + 1, name = dn))
        }
        programId
    }

    suspend fun updateProgram(program: Program) = dao.update(program)

    suspend fun renameProgram(programId: Long, newName: String) {
        val p = dao.getById(programId) ?: return
        dao.update(p.copy(name = newName))
    }

    suspend fun updateDay(day: WorkoutDay) = dao.updateDay(day)

    suspend fun renameDay(dayId: Long, newName: String) {
        val d = dao.getDay(dayId) ?: return
        dao.updateDay(d.copy(name = newName))
    }

    suspend fun deleteDay(day: WorkoutDay) = db.withTransaction {
        dao.deleteDay(day)
        // Re-pack dayIndex on the remaining days so they stay 1..N
        val remaining = dao.daysForProgram(day.programId)
        remaining.forEachIndexed { idx, d ->
            if (d.dayIndex != idx + 1) dao.updateDay(d.copy(dayIndex = idx + 1))
        }
        val program = dao.getById(day.programId)
        if (program != null && program.daysPerWeek != remaining.size) {
            dao.update(program.copy(daysPerWeek = remaining.size))
        }
    }

    suspend fun moveDay(programId: Long, dayId: Long, direction: Int) = db.withTransaction {
        // direction: -1 = up, +1 = down. Swaps dayIndex with the neighbor.
        val days = dao.daysForProgram(programId)
        val idx = days.indexOfFirst { it.id == dayId }
        if (idx < 0) return@withTransaction
        val swapIdx = idx + direction
        if (swapIdx !in days.indices) return@withTransaction
        val a = days[idx]
        val b = days[swapIdx]
        dao.updateDay(a.copy(dayIndex = b.dayIndex))
        dao.updateDay(b.copy(dayIndex = a.dayIndex))
    }

    suspend fun deleteProgram(program: Program) = dao.delete(program)

    suspend fun addExercise(
        dayId: Long,
        name: String,
        startingWeight: Double,
        sets: List<Pair<Int, Double?>>,
        isBodyweight: Boolean = false,
    ): Long = db.withTransaction {
        val existing = dao.exercisesForDay(dayId)
        val exerciseId = dao.insertExercise(
            PlannedExercise(
                dayId = dayId,
                name = name,
                orderInDay = ExerciseOrdering.nextOrderInDay(existing.map { it.orderInDay }),
                startingWeight = startingWeight,
                isBodyweight = isBodyweight,
            )
        )
        sets.forEachIndexed { idx, (reps, weightOverride) ->
            dao.insertSet(
                PlannedSet(
                    exerciseId = exerciseId,
                    setNumber = idx + 1,
                    targetReps = reps,
                    targetWeightOverride = weightOverride,
                )
            )
        }
        exerciseId
    }

    suspend fun replaceExerciseSets(exerciseId: Long, sets: List<Pair<Int, Double?>>) = db.withTransaction {
        dao.setsForExercise(exerciseId).forEach { dao.deleteSet(it) }
        sets.forEachIndexed { idx, (reps, weightOverride) ->
            dao.insertSet(
                PlannedSet(
                    exerciseId = exerciseId,
                    setNumber = idx + 1,
                    targetReps = reps,
                    targetWeightOverride = weightOverride,
                )
            )
        }
    }

    suspend fun updateExercise(exercise: PlannedExercise) = dao.updateExercise(exercise)

    suspend fun deleteExercise(exercise: PlannedExercise) = db.withTransaction {
        dao.deleteExercise(exercise)
        // Re-pack orderInDay on the remaining exercises so they stay 0..N-1
        val remaining = dao.exercisesForDay(exercise.dayId)
        ExerciseOrdering.repackTargets(remaining.map { it.orderInDay })
            .forEach { (position, newOrder) ->
                dao.updateExercise(remaining[position].copy(orderInDay = newOrder))
            }
    }

    suspend fun moveExercise(dayId: Long, exerciseId: Long, direction: Int) = db.withTransaction {
        // direction: -1 = up, +1 = down. Re-packs the whole day to 0..N-1 in
        // the new order rather than swapping two order values, so a day whose
        // orderInDay is duplicated or gapped still moves. Older data can be:
        // addExercise once derived the order from the list size.
        val exercises = dao.exercisesForDay(dayId)
        val idx = exercises.indexOfFirst { it.id == exerciseId }
        val positions = ExerciseOrdering.movedOrder(exercises.size, idx, direction)
            ?: return@withTransaction
        positions.forEachIndexed { newOrder, oldPosition ->
            val ex = exercises[oldPosition]
            if (ex.orderInDay != newOrder) dao.updateExercise(ex.copy(orderInDay = newOrder))
        }
    }

    suspend fun importJson(json: ProgramJson): Long = db.withTransaction {
        val programId = dao.insert(
            Program(
                name = json.name,
                daysPerWeek = json.daysPerWeek,
                totalWeeks = json.totalWeeks,
            )
        )
        json.days.forEach { dayJson ->
            val dayId = dao.insertDay(
                WorkoutDay(
                    programId = programId,
                    dayIndex = dayJson.dayIndex,
                    name = dayJson.name,
                )
            )
            dayJson.exercises.forEachIndexed { idx, exJson ->
                val exerciseId = dao.insertExercise(
                    PlannedExercise(
                        dayId = dayId,
                        name = exJson.name,
                        orderInDay = idx,
                        startingWeight = exJson.startingWeight,
                        isBodyweight = exJson.isBodyweight,
                    )
                )
                exJson.sets.forEach { setJson ->
                    dao.insertSet(
                        PlannedSet(
                            exerciseId = exerciseId,
                            setNumber = setJson.setNumber,
                            targetReps = setJson.targetReps,
                            targetWeightOverride = setJson.targetWeightOverride,
                        )
                    )
                }
            }
        }
        programId
    }

    suspend fun replaceDayFromJson(dayId: Long, exercises: List<ProgramJson.ExerciseJson>) = db.withTransaction {
        dao.deleteExercisesForDay(dayId)
        exercises.forEachIndexed { idx, exJson ->
            val exerciseId = dao.insertExercise(
                PlannedExercise(
                    dayId = dayId,
                    name = exJson.name,
                    orderInDay = idx,
                    startingWeight = exJson.startingWeight,
                )
            )
            exJson.sets.forEach { setJson ->
                dao.insertSet(
                    PlannedSet(
                        exerciseId = exerciseId,
                        setNumber = setJson.setNumber,
                        targetReps = setJson.targetReps,
                        targetWeightOverride = setJson.targetWeightOverride,
                    )
                )
            }
        }
    }
}
