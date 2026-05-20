package com.luke.workouttracker.data.repo

import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.WorkoutSession

data class ProgramProgress(
    val totalWeeks: Int,
    val daysPerWeek: Int,
    val currentWeek: Int,        // 1..totalWeeks; equals totalWeeks+1 when the program is complete
    val isComplete: Boolean,
    val completedDayIdsThisWeek: Set<Long>,
) {
    val daysCompletedThisWeek: Int get() = completedDayIdsThisWeek.size
    val displayWeek: Int get() = if (isComplete) totalWeeks else currentWeek

    companion object {
        fun compute(program: Program, completedSessions: List<WorkoutSession>): ProgramProgress {
            val byWeek = completedSessions.groupBy { it.weekNumber }
                .mapValues { (_, list) -> list.map { it.dayId }.toSet() }
            val currentWeek = (1..program.totalWeeks).firstOrNull { week ->
                (byWeek[week]?.size ?: 0) < program.daysPerWeek
            } ?: (program.totalWeeks + 1)
            val isComplete = currentWeek > program.totalWeeks
            val effectiveWeek = if (isComplete) program.totalWeeks else currentWeek
            return ProgramProgress(
                totalWeeks = program.totalWeeks,
                daysPerWeek = program.daysPerWeek,
                currentWeek = currentWeek,
                isComplete = isComplete,
                completedDayIdsThisWeek = byWeek[effectiveWeek].orEmpty(),
            )
        }
    }
}
