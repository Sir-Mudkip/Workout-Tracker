# Program JSON format

Programs can be exported to and imported from JSON, which is the only
way to move a program between devices — there is no sync. Import is on
the program list screen; export writes to `Downloads/` via MediaStore
(`data/json/ExportHelper.kt`).

## Schema

```json
{
  "name": "PPL 6-week",
  "daysPerWeek": 3,
  "totalWeeks": 6,
  "days": [
    {
      "dayIndex": 1,
      "name": "Push",
      "exercises": [
        {
          "name": "Incline Dumbbell Press",
          "startingWeight": 30,
          "isBodyweight": false,
          "sets": [
            { "setNumber": 1, "targetReps": 8 },
            { "setNumber": 2, "targetReps": 8, "targetWeightOverride": 27.5 }
          ]
        }
      ]
    }
  ]
}
```

| Field | Required | Notes |
|---|---|---|
| `name`, `daysPerWeek`, `totalWeeks` | Yes | `daysPerWeek` should match `days.length` |
| `days[].dayIndex` | Yes | 1-based |
| `days[].name` | Yes | |
| `exercises[].name` | Yes | |
| `exercises[].startingWeight` | Yes | kg. For a bodyweight exercise this is *added* weight — `0` for plain bodyweight |
| `exercises[].isBodyweight` | No | Defaults to `false` |
| `sets[].setNumber` | Yes | 1-based |
| `sets[].targetReps` | Yes | |
| `sets[].targetWeightOverride` | No | Omit to use `startingWeight` |

`ignoreUnknownKeys = true` and `encodeDefaults = false`
(`data/json/ProgramJson.kt`), so unknown fields are skipped on import and
defaulted fields are omitted on export. A round trip is lossless for
everything the schema covers.

## What the format does not carry

Only the **plan**. No sessions, set logs, difficulty ratings, swaps,
peak-lift entries, bodyweight, or theme. Exporting a program and
re-importing it gives a clean copy with no history — which is the
intended behaviour for sharing a program, but means **export is not a
backup**.

There is currently no way to export training history at all. Anyone
relying on JSON export as a safety net against the destructive-migration
risk in [`database.md`](./database.md) would lose every logged set.

## Import behaviour

`ProgramRepository.importJson` always creates a **new** program. There is
no merge or update-in-place, and no de-duplication by name — importing
the same file twice gives two programs.

`orderInDay` is assigned from array position, not from any field, so
exercise order in the file is the order in the app. `dayIndex` is taken
from the field as given, so a file numbering days `1, 3, 7` produces
those indices; nothing re-packs them on import.

Imported exercise names are deliberately **not** added to the exercise
library. A shared program carries its author's naming, which would
otherwise pollute the suggestions the library exists to keep consistent.

## Naming

`samples/ppl-6week.json` uses `Incline DB Press`. The exercise library
convention spells equipment out — `Incline Dumbbell Press` — and
`ExerciseLibraryTest` enforces that for the shipped list. The sample file
predates the library and is not covered by that test.

The mismatch is harmless, since imported names are free text and never
checked against the library. But new samples should follow the library
convention, and the existing one is worth updating whenever it is next
touched.
