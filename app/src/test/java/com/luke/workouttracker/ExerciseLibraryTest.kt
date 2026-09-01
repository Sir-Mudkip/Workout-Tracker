package com.luke.workouttracker

import com.luke.workouttracker.data.library.STOCK_EXERCISES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseLibraryTest {

    @Test fun the_list_has_the_expected_number_of_entries() {
        assertEquals(140, STOCK_EXERCISES.size)
    }

    @Test fun there_are_no_duplicate_names() {
        val duplicates = STOCK_EXERCISES.groupBy { it.lowercase() }
            .filter { it.value.size > 1 }
            .keys
        assertEquals(emptySet<String>(), duplicates)
    }

    @Test fun no_name_is_blank_or_has_stray_whitespace() {
        STOCK_EXERCISES.forEach { name ->
            assertTrue("blank entry", name.isNotBlank())
            assertEquals("untrimmed: '$name'", name.trim(), name)
            assertTrue("double space in '$name'", !name.contains("  "))
        }
    }

    @Test fun names_avoid_abbreviations() {
        // The spec requires spelled-out equipment names so search stays predictable.
        val banned = listOf("DB ", "BB ", "OHP", "RDL", "SLDL")
        STOCK_EXERCISES.forEach { name ->
            banned.forEach { abbrev ->
                assertTrue("'$name' uses abbreviation '$abbrev'", !name.contains(abbrev))
            }
        }
    }

    @Test fun the_documented_variant_examples_are_all_present() {
        listOf(
            "High Bar Back Squat",
            "Low Bar Back Squat",
            "Barbell Bench Press",
            "Close Grip Barbell Bench Press",
            "Neutral Grip Lat Pulldown",
            "Wide Grip Lat Pulldown",
            "Incline Dumbbell Press",
            "Flat Dumbbell Press",
            "Neutral Grip Pull Up",
        ).forEach { assertTrue("missing: $it", STOCK_EXERCISES.contains(it)) }
    }
}
