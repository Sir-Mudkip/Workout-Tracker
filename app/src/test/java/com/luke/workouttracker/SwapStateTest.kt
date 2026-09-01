package com.luke.workouttracker

import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.SessionExerciseSwap
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.ui.session.ActiveSessionState
import com.luke.workouttracker.ui.session.canSwapExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwapStateTest {

    private val pulldown = PlannedExercise(
        id = 1, dayId = 1, name = "Neutral Grip Lat Pulldown",
        orderInDay = 0, startingWeight = 60.0, isBodyweight = false,
    )
    private val pullUp = SessionExerciseSwap(
        sessionId = 1, plannedExerciseId = 1,
        replacementName = "Neutral Grip Pull Up", isBodyweight = true,
    )

    private fun state(swaps: Map<Long, SessionExerciseSwap> = emptyMap()) = ActiveSessionState(
        sessionId = 1,
        programId = 1,
        weekNumber = 3,
        dayName = "Pull",
        programName = "PPL",
        exercises = listOf(pulldown),
        setsByExercise = mapOf(1L to listOf(PlannedSet(id = 1, exerciseId = 1, setNumber = 1, targetReps = 10))),
        priorLogsByExercise = emptyMap(),
        currentExerciseIdx = 0,
        currentSetIdx = 0,
        completed = false,
        swapsByExercise = swaps,
    )

    private fun log(exerciseId: Long) = SetLog(
        id = 0, sessionId = 1, plannedExerciseId = exerciseId,
        setNumber = 1, actualReps = 10, actualWeight = 60.0,
    )

    // displayName

    @Test fun display_name_is_the_planned_name_when_not_swapped() {
        assertEquals("Neutral Grip Lat Pulldown", state().displayName(pulldown))
    }

    @Test fun display_name_is_the_replacement_when_swapped() {
        assertEquals("Neutral Grip Pull Up", state(mapOf(1L to pullUp)).displayName(pulldown))
    }

    // isBodyweight

    @Test fun bodyweight_follows_the_planned_exercise_when_not_swapped() {
        assertFalse(state().isBodyweight(pulldown))
    }

    @Test fun swapping_a_weighted_exercise_for_a_bodyweight_one_flips_the_flag() {
        assertTrue(state(mapOf(1L to pullUp)).isBodyweight(pulldown))
    }

    @Test fun swapping_a_bodyweight_exercise_for_a_weighted_one_flips_the_flag_back() {
        val dip = pulldown.copy(name = "Weighted Dip", isBodyweight = true)
        val machine = SessionExerciseSwap(
            sessionId = 1, plannedExerciseId = 1,
            replacementName = "Machine Chest Press", isBodyweight = false,
        )
        assertFalse(state(mapOf(1L to machine)).isBodyweight(dip))
    }

    // prefillWeight

    @Test fun prefill_weight_is_null_for_a_swapped_exercise() {
        // Prior logs describe the original movement, so suggesting them is wrong.
        assertNull(state(mapOf(1L to pullUp)).prefillWeight())
    }

    @Test fun prefill_weight_still_works_for_an_unswapped_exercise() {
        assertEquals(60.0, state().prefillWeight()!!, 0.0001)
    }

    // canSwapExercise

    @Test fun swapping_is_allowed_before_any_set_is_logged() {
        assertTrue(canSwapExercise(1L, emptyList()))
    }

    @Test fun swapping_is_blocked_once_a_set_is_logged_for_that_exercise() {
        assertFalse(canSwapExercise(1L, listOf(log(1L))))
    }

    @Test fun a_set_logged_for_a_different_exercise_does_not_block_swapping() {
        assertTrue(canSwapExercise(1L, listOf(log(99L))))
    }
}
