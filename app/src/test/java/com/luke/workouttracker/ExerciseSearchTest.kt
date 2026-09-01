package com.luke.workouttracker

import com.luke.workouttracker.data.library.filterExercises
import com.luke.workouttracker.data.library.mergeExerciseNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {

    private val sample = listOf(
        "Barbell Bench Press",
        "Bench Dip",
        "Close Grip Barbell Bench Press",
        "Neutral Grip Lat Pulldown",
        "Wide Grip Lat Pulldown",
        "Pull Up",
    )

    // filterExercises — matching

    @Test fun a_blank_query_returns_everything_up_to_the_limit() {
        assertEquals(sample, filterExercises(sample, "", limit = 10))
    }

    @Test fun a_blank_query_still_respects_the_limit() {
        assertEquals(3, filterExercises(sample, "", limit = 3).size)
    }

    @Test fun matching_is_case_insensitive_in_both_directions() {
        assertTrue(filterExercises(sample, "BENCH DIP").contains("Bench Dip"))
        assertTrue(filterExercises(sample, "bench dip").contains("Bench Dip"))
    }

    @Test fun every_token_must_match_somewhere_in_the_name() {
        // "lat pull" matches "Neutral Grip Lat Pulldown": "lat" and "pull" both appear.
        val result = filterExercises(sample, "lat pull")
        assertTrue(result.contains("Neutral Grip Lat Pulldown"))
        assertTrue(result.contains("Wide Grip Lat Pulldown"))
        assertTrue(!result.contains("Pull Up"))
    }

    @Test fun tokens_may_match_in_any_order() {
        assertTrue(filterExercises(sample, "pulldown lat").contains("Neutral Grip Lat Pulldown"))
    }

    @Test fun a_query_matching_nothing_returns_an_empty_list() {
        assertEquals(emptyList<String>(), filterExercises(sample, "zercher"))
    }

    @Test fun extra_whitespace_in_the_query_is_ignored() {
        assertTrue(filterExercises(sample, "  lat   pull  ").contains("Wide Grip Lat Pulldown"))
    }

    // filterExercises — ranking

    @Test fun word_boundary_matches_rank_above_mid_word_matches() {
        // "bench" starts a word in all three, but "Barbell Bench Press" and
        // "Bench Dip" should not be pushed below by the longer name.
        val result = filterExercises(sample, "bench")
        assertEquals(3, result.size)
        assertTrue(result.indexOf("Bench Dip") < result.indexOf("Close Grip Barbell Bench Press"))
    }

    @Test fun a_name_starting_with_the_query_ranks_first() {
        val result = filterExercises(sample, "pull")
        assertEquals("Pull Up", result.first())
    }

    @Test fun results_are_capped_at_the_limit() {
        val many = (1..50).map { "Exercise $it" }
        assertEquals(8, filterExercises(many, "exercise").size)
    }

    // mergeExerciseNames

    @Test fun merging_combines_both_sources_alphabetically() {
        assertEquals(
            listOf("Alpha", "Beta", "Gamma"),
            mergeExerciseNames(stock = listOf("Gamma", "Alpha"), custom = listOf("Beta")),
        )
    }

    @Test fun a_custom_name_duplicating_a_stock_name_appears_once() {
        val merged = mergeExerciseNames(
            stock = listOf("Barbell Bench Press"),
            custom = listOf("Barbell Bench Press"),
        )
        assertEquals(listOf("Barbell Bench Press"), merged)
    }

    @Test fun duplicate_detection_ignores_case_and_keeps_the_custom_spelling() {
        val merged = mergeExerciseNames(
            stock = listOf("Barbell Bench Press"),
            custom = listOf("barbell bench press"),
        )
        assertEquals(listOf("barbell bench press"), merged)
    }

    @Test fun merging_empty_sources_yields_an_empty_list() {
        assertEquals(emptyList<String>(), mergeExerciseNames(emptyList(), emptyList()))
    }
}
