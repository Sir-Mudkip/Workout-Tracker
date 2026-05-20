package com.luke.workouttracker.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.WorkoutDay
import com.luke.workouttracker.data.repo.ProgramProgress
import com.luke.workouttracker.data.repo.ProgramRepository
import com.luke.workouttracker.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PickerState(
    val program: Program,
    val days: List<WorkoutDay>,
    val progress: ProgramProgress,
)

@HiltViewModel
class SessionPickerViewModel @Inject constructor(
    handle: SavedStateHandle,
    repo: ProgramRepository,
    private val sessions: SessionRepository,
) : ViewModel() {
    val programId: Long = checkNotNull(handle["programId"])

    val state: StateFlow<PickerState?> = combine(
        repo.observeProgram(programId),
        repo.observeDays(programId),
        sessions.observeCompletedSessions(programId),
    ) { program, days, completed ->
        if (program == null) null else PickerState(
            program = program,
            days = days,
            progress = ProgramProgress.compute(program, completed),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun start(weekNumber: Int, dayId: Long, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val sessionId = sessions.startOrResumeSession(programId, weekNumber, dayId)
            onStarted(sessionId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionPickerScreen(
    onStart: (Long) -> Unit,
    onOpenPeakDay: (Long) -> Unit,
    onBack: () -> Unit,
    vm: SessionPickerViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Start workout") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            Text(s.program.name, style = MaterialTheme.typography.titleLarge)

            ProgressHeader(s.progress)

            if (s.progress.isComplete) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Program complete!", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Open Progress to see your gains.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                PeakDayCard(onOpen = { onOpenPeakDay(s.program.id) }, isFinalDay = true)
            } else {
                Text("Pick a day", style = MaterialTheme.typography.titleMedium)
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(s.days, key = { it.id }) { day ->
                        DayRow(
                            day = day,
                            completed = day.id in s.progress.completedDayIdsThisWeek,
                            onStart = { vm.start(s.progress.currentWeek, day.id, onStart) },
                        )
                    }
                    item {
                        val isFinalWeek = s.progress.currentWeek == s.progress.totalWeeks
                        PeakDayCard(
                            onOpen = { onOpenPeakDay(s.program.id) },
                            isFinalDay = isFinalWeek,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(progress: ProgramProgress) {
    val totalWorkouts = progress.totalWeeks * progress.daysPerWeek
    val completedWorkouts = (progress.currentWeek - 1).coerceAtLeast(0) * progress.daysPerWeek +
        progress.daysCompletedThisWeek
    val fraction = if (totalWorkouts > 0) completedWorkouts.toFloat() / totalWorkouts else 0f

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (progress.isComplete) "Program complete"
                else "Week ${progress.currentWeek} of ${progress.totalWeeks}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${progress.daysCompletedThisWeek}/${progress.daysPerWeek} days done this week · $completedWorkouts/$totalWorkouts overall",
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun PeakDayCard(onOpen: () -> Unit, isFinalDay: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = if (isFinalDay) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Peak day · 1RM test", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isFinalDay) "Final week — go test your maxes!"
                    else "Set starting 1RMs · open anytime to record",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onOpen) { Text("Open") }
        }
    }
}

@Composable
private fun DayRow(
    day: WorkoutDay,
    completed: Boolean,
    onStart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (completed) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Column {
                    Text("Day ${day.dayIndex}: ${day.name}", style = MaterialTheme.typography.titleMedium)
                    if (completed) {
                        Text(
                            "Completed this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Button(onClick = onStart, enabled = !completed) {
                Text(if (completed) "Done" else "Start")
            }
        }
    }
}
