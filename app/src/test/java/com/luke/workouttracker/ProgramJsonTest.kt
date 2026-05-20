package com.luke.workouttracker

import com.luke.workouttracker.data.json.ProgramJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramJsonTest {

    private val raw = """
        {
          "name": "T",
          "daysPerWeek": 2,
          "totalWeeks": 4,
          "days": [
            { "dayIndex": 1, "name": "A",
              "exercises": [
                { "name": "Squat", "startingWeight": 80,
                  "sets": [
                    { "setNumber": 1, "targetReps": 5 },
                    { "setNumber": 2, "targetReps": 5, "targetWeightOverride": 70 }
                  ]
                }
              ]
            },
            { "dayIndex": 2, "name": "B", "exercises": [] }
          ]
        }
    """.trimIndent()

    @Test fun parses_starting_weight_and_optional_override() {
        val p = ProgramJson.parse(raw)
        assertEquals("T", p.name)
        assertEquals(2, p.daysPerWeek)
        val ex = p.days[0].exercises[0]
        assertEquals("Squat", ex.name)
        assertEquals(80.0, ex.startingWeight, 0.0001)
        assertNull(ex.sets[0].targetWeightOverride)
        assertEquals(70.0, ex.sets[1].targetWeightOverride!!, 0.0001)
    }

    @Test fun round_trip_preserves_fields() {
        val p1 = ProgramJson.parse(raw)
        val encoded = ProgramJson.encode(p1)
        val p2 = ProgramJson.parse(encoded)
        assertEquals(p1, p2)
    }
}
