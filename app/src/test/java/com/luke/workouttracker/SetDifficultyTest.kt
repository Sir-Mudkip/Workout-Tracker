package com.luke.workouttracker

import com.luke.workouttracker.data.db.entities.SetDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetDifficultyTest {

    @Test fun every_level_round_trips_through_its_stored_value() {
        SetDifficulty.entries.forEach { d ->
            assertEquals(d, SetDifficulty.fromStored(d.stored))
        }
    }

    @Test fun stored_values_are_one_through_six_in_increasing_difficulty() {
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6),
            SetDifficulty.entries.map { it.stored },
        )
        assertEquals(SetDifficulty.VERY_EASY, SetDifficulty.entries.first())
        assertEquals(SetDifficulty.FAILURE, SetDifficulty.entries.last())
    }

    @Test fun an_unrated_set_maps_to_null() {
        assertNull(SetDifficulty.fromStored(null))
    }

    @Test fun a_value_outside_the_scale_maps_to_null_rather_than_throwing() {
        // Guards old rows if the scale is ever renumbered.
        assertNull(SetDifficulty.fromStored(0))
        assertNull(SetDifficulty.fromStored(7))
        assertNull(SetDifficulty.fromStored(-1))
    }

    @Test fun labels_are_present_and_distinct() {
        val labels = SetDifficulty.entries.map { it.label }
        assertEquals(6, labels.toSet().size)
        assertEquals("Very easy", SetDifficulty.VERY_EASY.label)
        assertEquals("Very hard", SetDifficulty.VERY_HARD.label)
        assertEquals("Failure", SetDifficulty.FAILURE.label)
    }

    @Test fun harder_sets_compare_greater_than_easier_ones() {
        assert(SetDifficulty.FAILURE.stored > SetDifficulty.MODERATE.stored)
        assert(SetDifficulty.VERY_EASY.stored < SetDifficulty.EASY.stored)
    }
}
