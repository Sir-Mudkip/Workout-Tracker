package com.luke.workouttracker

import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.ui.session.ActiveSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveSessionStateTest {

    private fun base(
        priorLogs: Map<Pair<Int, Int>, Double> = emptyMap(),
        sets: List<PlannedSet>,
        startingWeight: Double = 30.0,
        weekNumber: Int = 2,
        currentSetIdx: Int = 0,
    ): ActiveSessionState {
        val ex = PlannedExercise(id = 1, dayId = 1, name = "Incline DB", orderInDay = 0, startingWeight = startingWeight)
        return ActiveSessionState(
            sessionId = 100,
            programId = 1,
            weekNumber = weekNumber,
            dayName = "Push",
            programName = "P",
            exercises = listOf(ex),
            setsByExercise = mapOf(ex.id to sets),
            priorLogsByExercise = mapOf(ex.id to priorLogs),
            currentExerciseIdx = 0,
            currentSetIdx = currentSetIdx,
            completed = false,
        )
    }

    @Test fun prefill_uses_starting_weight_when_no_history_and_no_override() {
        val s = base(sets = listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 8)))
        assertEquals(30.0, s.prefillWeight(), 0.0001)
        assertEquals(8, s.prefillReps())
    }

    @Test fun prefill_uses_override_when_present_and_no_history() {
        val s = base(sets = listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 8, targetWeightOverride = 27.5)))
        assertEquals(27.5, s.prefillWeight(), 0.0001)
    }

    @Test fun prefill_prefers_last_week_actual_over_override_and_start() {
        val s = base(
            priorLogs = mapOf((1 to 1) to 35.0),
            sets = listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 8, targetWeightOverride = 27.5)),
        )
        assertEquals(35.0, s.prefillWeight(), 0.0001)
    }

    @Test fun prefill_picks_most_recent_prior_week() {
        val s = base(
            priorLogs = mapOf((1 to 1) to 30.0, (2 to 1) to 32.5),
            sets = listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 8)),
            weekNumber = 3,
        )
        assertEquals(32.5, s.prefillWeight(), 0.0001)
    }

    @Test fun prefill_ignores_logs_from_current_or_future_weeks() {
        val s = base(
            priorLogs = mapOf((2 to 1) to 40.0, (3 to 1) to 50.0),
            sets = listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 8)),
            weekNumber = 2,
        )
        assertEquals(30.0, s.prefillWeight(), 0.0001)
    }
}
