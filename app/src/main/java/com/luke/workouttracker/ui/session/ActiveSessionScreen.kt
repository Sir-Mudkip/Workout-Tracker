package com.luke.workouttracker.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.db.entities.SetLog
import com.luke.workouttracker.data.prefs.BodyweightPrefs
import com.luke.workouttracker.data.repo.ProgramRepository
import com.luke.workouttracker.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ActiveSessionState(
    val sessionId: Long,
    val programId: Long,
    val weekNumber: Int,
    val dayName: String,
    val programName: String,
    val exercises: List<PlannedExercise>,
    val setsByExercise: Map<Long, List<PlannedSet>>,
    val priorLogsByExercise: Map<Long, Map<Pair<Int, Int>, Double>>,
    val currentExerciseIdx: Int,
    val currentSetIdx: Int,
    val completed: Boolean,
) {
    val currentExercise: PlannedExercise? = exercises.getOrNull(currentExerciseIdx)
    val currentSet: PlannedSet? = currentExercise?.let { setsByExercise[it.id]?.getOrNull(currentSetIdx) }
    val totalSetsForCurrent: Int = currentExercise?.let { setsByExercise[it.id]?.size } ?: 0

    val isLastSetOfSession: Boolean = run {
        val ex = currentExercise ?: return@run false
        val isLastExercise = currentExerciseIdx == exercises.lastIndex
        val isLastSet = currentSetIdx == (setsByExercise[ex.id]?.lastIndex ?: -1)
        isLastExercise && isLastSet
    }

    fun prefillWeight(): Double {
        val ex = currentExercise ?: return 0.0
        val set = currentSet ?: return ex.startingWeight
        val priorMap = priorLogsByExercise[ex.id].orEmpty()
        val lastWeekEntries = priorMap.filterKeys { it.first < weekNumber && it.second == set.setNumber }
        val mostRecent = lastWeekEntries.maxByOrNull { it.key.first }?.value
        if (mostRecent != null) return mostRecent
        set.targetWeightOverride?.let { return it }
        return ex.startingWeight
    }

    fun prefillReps(): Int = currentSet?.targetReps ?: 0
}

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val programs: ProgramRepository,
    bodyweightPrefs: BodyweightPrefs,
) : ViewModel() {
    val sessionId: Long = checkNotNull(handle["sessionId"])

    private val _state = MutableStateFlow<ActiveSessionState?>(null)
    val state: StateFlow<ActiveSessionState?> = _state.asStateFlow()

    private val _restingSinceMs = MutableStateFlow<Long?>(null)
    val restingSinceMs: StateFlow<Long?> = _restingSinceMs.asStateFlow()

    val bodyweight: StateFlow<Double> = bodyweightPrefs.kg

    private var lastLoggedSetId: Long? = null

    val logs: StateFlow<List<SetLog>> = sessions.observeLogs(sessionId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { load() } }

    private suspend fun load() {
        val session = sessions.getSession(sessionId) ?: return
        val full = programs.getFullProgram(session.programId) ?: return
        val day = full.days.firstOrNull { it.day.id == session.dayId } ?: return
        val exercises = day.exercises.map { it.exercise }
        val setsByExercise = day.exercises.associate { it.exercise.id to it.sets }
        val priorMap = mutableMapOf<Long, Map<Pair<Int, Int>, Double>>()
        exercises.forEach { ex ->
            val rows = sessions.priorLogs(session.programId, ex.id)
            priorMap[ex.id] = rows.associate { (it.weekNumber to it.setNumber) to it.actualWeight }
        }
        val existingLogs = sessions.logsForSession(sessionId)
        val (curExIdx, curSetIdx) = computeResume(exercises, setsByExercise, existingLogs)
        _state.value = ActiveSessionState(
            sessionId = sessionId,
            programId = session.programId,
            weekNumber = session.weekNumber,
            dayName = day.day.name,
            programName = full.program.name,
            exercises = exercises,
            setsByExercise = setsByExercise,
            priorLogsByExercise = priorMap,
            currentExerciseIdx = curExIdx,
            currentSetIdx = curSetIdx,
            completed = session.completedAt != null,
        )
    }

    private fun computeResume(
        exercises: List<PlannedExercise>,
        setsByExercise: Map<Long, List<PlannedSet>>,
        logs: List<SetLog>,
    ): Pair<Int, Int> {
        val countByExercise = logs.groupingBy { it.plannedExerciseId }.eachCount()
        exercises.forEachIndexed { idx, ex ->
            val planned = setsByExercise[ex.id].orEmpty().size
            val logged = countByExercise[ex.id] ?: 0
            if (logged < planned) return idx to logged
        }
        return exercises.size to 0
    }

    /** Log the current set and start the rest timer. Does NOT advance to the next set. */
    fun logCurrentSet(reps: Int, weight: Double) {
        val s = _state.value ?: return
        val ex = s.currentExercise ?: return
        val set = s.currentSet ?: return
        viewModelScope.launch {
            lastLoggedSetId = sessions.logSet(sessionId, ex.id, set.setNumber, reps, weight)
            _restingSinceMs.value = System.currentTimeMillis()
        }
    }

    /** Advance to next set / next exercise / mark session complete. Dismisses rest timer. */
    fun advance(onAllDone: () -> Unit) {
        val s = _state.value ?: return
        val ex = s.currentExercise ?: return
        val restedSince = _restingSinceMs.value
        val justLoggedId = lastLoggedSetId
        viewModelScope.launch {
            if (restedSince != null && justLoggedId != null) {
                val restMs = (System.currentTimeMillis() - restedSince).coerceAtLeast(0)
                sessions.setRestAfter(justLoggedId, restMs)
            }
            val nextSetIdx = s.currentSetIdx + 1
            val totalSets = s.setsByExercise[ex.id]?.size ?: 0
            val (newExIdx, newSetIdx) = if (nextSetIdx < totalSets) {
                s.currentExerciseIdx to nextSetIdx
            } else {
                (s.currentExerciseIdx + 1) to 0
            }
            val completed = newExIdx >= s.exercises.size
            if (completed) sessions.completeSession(sessionId)
            _state.value = s.copy(
                currentExerciseIdx = newExIdx,
                currentSetIdx = newSetIdx,
                completed = completed,
            )
            _restingSinceMs.value = null
            lastLoggedSetId = null
            if (completed) onAllDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    onFinished: () -> Unit,
    vm: ActiveSessionViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val logs by vm.logs.collectAsState()
    val restingSince by vm.restingSinceMs.collectAsState()
    val bodyweight by vm.bodyweight.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout") },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${s.programName} · Week ${s.weekNumber} · ${s.dayName}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (s.completed) {
                CompletedCard(onFinished)
            } else {
                ActiveCard(
                    state = s,
                    bodyweight = bodyweight,
                    onComplete = { reps, weight -> vm.logCurrentSet(reps, weight) },
                )
                LoggedSetsCard(logs, s)
            }
        }
    }

    restingSince?.let { startMs ->
        val isFinishing = state?.isLastSetOfSession == true
        RestTimerDialog(
            startMs = startMs,
            buttonLabel = if (isFinishing) "Finish workout" else "Next set",
            onAdvance = { vm.advance(onAllDone = onFinished) },
        )
    }
}

@Composable
private fun ActiveCard(
    state: ActiveSessionState,
    bodyweight: Double,
    onComplete: (Int, Double) -> Unit,
) {
    val ex = state.currentExercise ?: return
    val set = state.currentSet ?: return
    var reps by remember(state.currentExerciseIdx, state.currentSetIdx) {
        mutableStateOf("")
    }
    var weight by remember(state.currentExerciseIdx, state.currentSetIdx) {
        mutableStateOf("")
    }
    val isBw = ex.isBodyweight
    val weightLabel = if (isBw) "Added weight (kg)" else "Weight (kg)"
    val targetWeight = state.prefillWeight()
    val targetWeightText = if (isBw) {
        if (targetWeight == 0.0) "bodyweight" else "BW + ${trim(targetWeight)} kg"
    } else {
        "${trim(targetWeight)} kg"
    }
    val repsHint = state.prefillReps().toString()
    val weightHint = trim(targetWeight)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(ex.name, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Set ${set.setNumber} of ${state.totalSetsForCurrent} · target ${set.targetReps} reps @ $targetWeightText",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isBw) {
                Text(
                    "+ bodyweight (${trim(bodyweight)} kg, set in Settings)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit) },
                    label = { Text("Reps") },
                    placeholder = { Text(repsHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(weightLabel) },
                    placeholder = { Text(weightHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = reps.toIntOrNull() != null && weight.toDoubleOrNull() != null,
                onClick = { onComplete(reps.toInt(), weight.toDouble()) },
            ) { Text("Complete set") }
        }
    }
}

@Composable
private fun RestTimerDialog(
    startMs: Long,
    buttonLabel: String,
    onAdvance: () -> Unit,
) {
    var elapsedMs by remember(startMs) { mutableLongStateOf(System.currentTimeMillis() - startMs) }
    LaunchedEffect(startMs) {
        while (isActive) {
            elapsedMs = System.currentTimeMillis() - startMs
            delay(250)
        }
    }
    AlertDialog(
        onDismissRequest = onAdvance,
        title = { Text("Rest") },
        text = {
            Text(
                formatDuration(elapsedMs),
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = onAdvance) { Text(buttonLabel) }
        },
    )
}

@Composable
private fun LoggedSetsCard(logs: List<SetLog>, state: ActiveSessionState) {
    if (logs.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Logged this session", style = MaterialTheme.typography.titleSmall)
            logs.forEach { log ->
                val ex = state.exercises.firstOrNull { it.id == log.plannedExerciseId }
                val exName = ex?.name ?: "?"
                val weightText = when {
                    ex?.isBodyweight == true && log.actualWeight == 0.0 -> "BW"
                    ex?.isBodyweight == true -> "BW + ${trim(log.actualWeight)} kg"
                    else -> "${trim(log.actualWeight)} kg"
                }
                val restPart = log.restAfterMs?.let { " · rest ${formatDuration(it)}" } ?: ""
                Text(
                    "$exName · set ${log.setNumber}: ${log.actualReps} × $weightText$restPart",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CompletedCard(onFinished: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Workout complete!", style = MaterialTheme.typography.headlineSmall)
            Button(modifier = Modifier.padding(top = 12.dp), onClick = onFinished) { Text("Done") }
        }
    }
}

private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
