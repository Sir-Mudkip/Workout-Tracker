package com.luke.workouttracker.data.json

import com.luke.workouttracker.data.repo.FullProgram
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProgramJson(
    val name: String,
    val daysPerWeek: Int,
    val totalWeeks: Int,
    val days: List<DayJson>,
) {
    @Serializable
    data class DayJson(
        val dayIndex: Int,
        val name: String,
        val exercises: List<ExerciseJson>,
    )

    @Serializable
    data class ExerciseJson(
        val name: String,
        val startingWeight: Double,
        val sets: List<SetJson>,
        val isBodyweight: Boolean = false,
    )

    @Serializable
    data class SetJson(
        val setNumber: Int,
        val targetReps: Int,
        val targetWeightOverride: Double? = null,
    )

    companion object {
        val json: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun fromFull(full: FullProgram): ProgramJson = ProgramJson(
            name = full.program.name,
            daysPerWeek = full.program.daysPerWeek,
            totalWeeks = full.program.totalWeeks,
            days = full.days.map { dw ->
                DayJson(
                    dayIndex = dw.day.dayIndex,
                    name = dw.day.name,
                    exercises = dw.exercises.map { ew ->
                        ExerciseJson(
                            name = ew.exercise.name,
                            startingWeight = ew.exercise.startingWeight,
                            isBodyweight = ew.exercise.isBodyweight,
                            sets = ew.sets.map { s ->
                                SetJson(
                                    setNumber = s.setNumber,
                                    targetReps = s.targetReps,
                                    targetWeightOverride = s.targetWeightOverride,
                                )
                            },
                        )
                    },
                )
            },
        )

        fun parse(raw: String): ProgramJson = json.decodeFromString(serializer(), raw)
        fun encode(p: ProgramJson): String = json.encodeToString(serializer(), p)
    }
}
