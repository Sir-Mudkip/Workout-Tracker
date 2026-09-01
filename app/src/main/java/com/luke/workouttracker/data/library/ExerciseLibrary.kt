package com.luke.workouttracker.data.library

/**
 * Exercise names shipped with the app.
 *
 * Naming convention: [Modifier] + [Equipment] + [Movement]. The modifier —
 * grip, stance, angle, or bar position — appears only when it distinguishes a
 * variant that trains differently. The unmodified name is the conventional
 * default, so "Barbell Bench Press" is flat and medium-grip. No abbreviations.
 *
 * Edit freely: this is shipped content, not user data, so changes need no
 * database migration. User-added names live in the `custom_exercises` table.
 */
val STOCK_EXERCISES: List<String> = listOf(
    // Squat / quad
    "High Bar Back Squat",
    "Low Bar Back Squat",
    "Front Squat",
    "Safety Bar Squat",
    "Goblet Squat",
    "Bulgarian Split Squat",
    "Walking Lunge",
    "Reverse Lunge",
    "Step Up",
    "Hack Squat",
    "Leg Press",
    "Pendulum Squat",
    "Sissy Squat",
    "Leg Extension",

    // Hinge / posterior chain
    "Conventional Deadlift",
    "Sumo Deadlift",
    "Romanian Deadlift",
    "Stiff Leg Deadlift",
    "Trap Bar Deadlift",
    "Deficit Deadlift",
    "Rack Pull",
    "Good Morning",
    "Barbell Hip Thrust",
    "Single Leg Hip Thrust",
    "Glute Bridge",
    "Back Extension",
    "Reverse Hyperextension",
    "Seated Leg Curl",
    "Lying Leg Curl",
    "Nordic Curl",
    "Cable Pull Through",
    "Kettlebell Swing",

    // Horizontal push
    "Barbell Bench Press",
    "Close Grip Barbell Bench Press",
    "Wide Grip Barbell Bench Press",
    "Incline Barbell Bench Press",
    "Decline Barbell Bench Press",
    "Flat Dumbbell Press",
    "Incline Dumbbell Press",
    "Decline Dumbbell Press",
    "Neutral Grip Dumbbell Press",
    "Machine Chest Press",
    "Incline Machine Chest Press",
    "Smith Machine Bench Press",
    "Push Up",
    "Weighted Push Up",
    "Deficit Push Up",
    "Dip",
    "Weighted Dip",
    "Cable Fly",
    "Low To High Cable Fly",
    "High To Low Cable Fly",
    "Pec Deck",

    // Vertical push
    "Standing Overhead Press",
    "Seated Overhead Press",
    "Seated Dumbbell Shoulder Press",
    "Standing Dumbbell Shoulder Press",
    "Neutral Grip Dumbbell Shoulder Press",
    "Arnold Press",
    "Push Press",
    "Machine Shoulder Press",
    "Landmine Press",

    // Horizontal pull
    "Barbell Row",
    "Pendlay Row",
    "Yates Row",
    "Single Arm Dumbbell Row",
    "Chest Supported Dumbbell Row",
    "Chest Supported T Bar Row",
    "T Bar Row",
    "Seated Cable Row",
    "Neutral Grip Seated Cable Row",
    "Wide Grip Seated Cable Row",
    "Machine Row",
    "Inverted Row",
    "Meadows Row",
    "Kroc Row",

    // Vertical pull
    "Pull Up",
    "Weighted Pull Up",
    "Chin Up",
    "Weighted Chin Up",
    "Neutral Grip Pull Up",
    "Wide Grip Pull Up",
    "Lat Pulldown",
    "Wide Grip Lat Pulldown",
    "Neutral Grip Lat Pulldown",
    "Close Grip Lat Pulldown",
    "Reverse Grip Lat Pulldown",
    "Single Arm Lat Pulldown",
    "Straight Arm Pulldown",
    "Machine Pullover",

    // Shoulders / delts
    "Dumbbell Lateral Raise",
    "Cable Lateral Raise",
    "Machine Lateral Raise",
    "Leaning Cable Lateral Raise",
    "Front Raise",
    "Rear Delt Fly",
    "Cable Rear Delt Fly",
    "Reverse Pec Deck",
    "Face Pull",
    "Upright Row",
    "Barbell Shrug",
    "Dumbbell Shrug",

    // Biceps
    "Barbell Curl",
    "EZ Bar Curl",
    "Dumbbell Curl",
    "Alternating Dumbbell Curl",
    "Hammer Curl",
    "Incline Dumbbell Curl",
    "Preacher Curl",
    "Spider Curl",
    "Cable Curl",
    "Concentration Curl",
    "Reverse Curl",

    // Triceps
    "Skull Crusher",
    "EZ Bar Skull Crusher",
    "Overhead Cable Extension",
    "Overhead Dumbbell Extension",
    "Cable Triceps Pushdown",
    "Rope Triceps Pushdown",
    "Reverse Grip Pushdown",
    "Triceps Kickback",
    "JM Press",
    "Bench Dip",

    // Core
    "Plank",
    "Side Plank",
    "Hanging Leg Raise",
    "Hanging Knee Raise",
    "Cable Crunch",
    "Machine Crunch",
    "Ab Wheel Rollout",
    "Russian Twist",
    "Pallof Press",
    "Decline Sit Up",
    "Dead Bug",
    "Toes To Bar",

    // Calves
    "Standing Calf Raise",
    "Seated Calf Raise",
    "Leg Press Calf Raise",
    "Smith Machine Calf Raise",
    "Single Leg Calf Raise",
)
