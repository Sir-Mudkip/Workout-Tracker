package com.luke.workouttracker.ui.nav

object Routes {
    const val ProgramList = "programs"
    const val ProgramEditor = "program/{programId}"
    const val DayEditor = "program/{programId}/day/{dayId}"
    const val SessionPicker = "session/pick/{programId}"
    const val ActiveSession = "session/active/{sessionId}"
    const val Progress = "program/{programId}/progress"
    const val PeakDay = "program/{programId}/peak"
    const val Settings = "settings"

    fun programEditor(id: Long) = "program/$id"
    fun dayEditor(programId: Long, dayId: Long) = "program/$programId/day/$dayId"
    fun sessionPicker(programId: Long) = "session/pick/$programId"
    fun activeSession(sessionId: Long) = "session/active/$sessionId"
    fun progress(programId: Long) = "program/$programId/progress"
    fun peakDay(programId: Long) = "program/$programId/peak"
}
