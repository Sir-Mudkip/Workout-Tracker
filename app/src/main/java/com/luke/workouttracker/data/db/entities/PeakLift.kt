package com.luke.workouttracker.data.db.entities

enum class PeakLift(val displayName: String) {
    SQUAT("Squat"),
    BENCH("Bench"),
    DEADLIFT("Deadlift"),
    CLEAN("Clean"),
    POWER_CLEAN("Power Clean"),
    CLEAN_AND_JERK("Clean and Jerk"),
    SNATCH("Snatch");

    companion object {
        fun fromName(name: String): PeakLift? = entries.firstOrNull { it.name == name }
    }
}
