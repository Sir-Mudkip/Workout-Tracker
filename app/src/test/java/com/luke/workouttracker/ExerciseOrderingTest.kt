package com.luke.workouttracker

import com.luke.workouttracker.data.repo.ExerciseOrdering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseOrderingTest {

    // swapTarget

    @Test fun swaps_with_previous_when_moving_up_from_the_middle() {
        assertEquals(1, ExerciseOrdering.swapTarget(size = 4, index = 2, direction = -1))
    }

    @Test fun swaps_with_next_when_moving_down_from_the_middle() {
        assertEquals(3, ExerciseOrdering.swapTarget(size = 4, index = 2, direction = 1))
    }

    @Test fun moving_up_from_the_first_position_is_a_no_op() {
        assertNull(ExerciseOrdering.swapTarget(size = 4, index = 0, direction = -1))
    }

    @Test fun moving_down_from_the_last_position_is_a_no_op() {
        assertNull(ExerciseOrdering.swapTarget(size = 4, index = 3, direction = 1))
    }

    @Test fun a_missing_exercise_is_a_no_op() {
        assertNull(ExerciseOrdering.swapTarget(size = 4, index = -1, direction = 1))
    }

    @Test fun a_single_exercise_cannot_move_in_either_direction() {
        assertNull(ExerciseOrdering.swapTarget(size = 1, index = 0, direction = -1))
        assertNull(ExerciseOrdering.swapTarget(size = 1, index = 0, direction = 1))
    }

    // repackTargets

    @Test fun repacking_an_already_dense_list_changes_nothing() {
        assertEquals(emptyMap<Int, Int>(), ExerciseOrdering.repackTargets(listOf(0, 1, 2)))
    }

    @Test fun repacking_closes_the_gap_left_by_deleting_the_first_exercise() {
        // Orders 0,1,2 with the first deleted leaves 1,2 — which must become 0,1.
        assertEquals(mapOf(0 to 0, 1 to 1), ExerciseOrdering.repackTargets(listOf(1, 2)))
    }

    @Test fun repacking_closes_the_gap_left_by_deleting_from_the_middle() {
        // Orders 0,1,2 with the middle deleted leaves 0,2 — only the tail moves.
        assertEquals(mapOf(1 to 1), ExerciseOrdering.repackTargets(listOf(0, 2)))
    }

    @Test fun repacking_an_empty_list_changes_nothing() {
        assertEquals(emptyMap<Int, Int>(), ExerciseOrdering.repackTargets(emptyList()))
    }

    // movedOrder — positions after a move, immune to duplicate or gapped orders

    @Test fun moving_up_swaps_the_two_positions() {
        assertEquals(listOf(0, 2, 1, 3), ExerciseOrdering.movedOrder(size = 4, index = 2, direction = -1))
    }

    @Test fun moving_down_swaps_the_two_positions() {
        assertEquals(listOf(0, 1, 3, 2), ExerciseOrdering.movedOrder(size = 4, index = 2, direction = 1))
    }

    @Test fun moving_off_either_end_is_a_no_op() {
        assertNull(ExerciseOrdering.movedOrder(size = 4, index = 0, direction = -1))
        assertNull(ExerciseOrdering.movedOrder(size = 4, index = 3, direction = 1))
    }

    @Test fun a_missing_position_is_a_no_op() {
        assertNull(ExerciseOrdering.movedOrder(size = 4, index = -1, direction = 1))
    }

    @Test fun the_result_is_always_a_dense_permutation() {
        // This is what makes the move survive duplicate orderInDay values in
        // existing data: positions are reassigned 0..N-1 rather than having
        // two order values swapped, which is a no-op when they are equal.
        val moved = ExerciseOrdering.movedOrder(size = 5, index = 3, direction = -1)!!
        assertEquals((0 until 5).toSet(), moved.toSet())
        assertEquals(5, moved.size)
    }

    // nextOrderInDay

    @Test fun the_first_exercise_in_an_empty_day_takes_order_zero() {
        assertEquals(0, ExerciseOrdering.nextOrderInDay(emptyList()))
    }

    @Test fun a_new_exercise_goes_after_the_current_last() {
        assertEquals(3, ExerciseOrdering.nextOrderInDay(listOf(0, 1, 2)))
    }

    @Test fun a_new_exercise_never_collides_with_a_gapped_order() {
        // The bug: three exercises 0,1,2 with the first deleted leaves 1,2.
        // Using the list size would produce 2 — a duplicate of the existing tail.
        assertEquals(3, ExerciseOrdering.nextOrderInDay(listOf(1, 2)))
    }
}
