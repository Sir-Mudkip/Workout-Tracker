package com.luke.workouttracker.data.db.entities

/**
 * How hard a logged set felt, recorded optionally after completing it.
 *
 * [stored] is what goes in the `set_logs.difficulty` column. The values are
 * ordered easiest to hardest so difficulty can be compared and aggregated
 * numerically without mapping back through the enum.
 */
enum class SetDifficulty(val stored: Int, val label: String) {
    VERY_EASY(1, "Very easy"),
    EASY(2, "Easy"),
    MODERATE(3, "Moderate"),
    HARD(4, "Hard"),
    VERY_HARD(5, "Very hard"),
    FAILURE(6, "Failure");

    companion object {
        /** Null for an unrated set, or for a value outside the current scale. */
        fun fromStored(stored: Int?): SetDifficulty? =
            entries.firstOrNull { it.stored == stored }
    }
}
