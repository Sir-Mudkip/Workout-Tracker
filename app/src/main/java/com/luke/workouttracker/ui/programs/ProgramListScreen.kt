package com.luke.workouttracker.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.json.ProgramJson
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

data class ProgramRow(val program: Program, val progress: ProgramProgress)

@HiltViewModel
class ProgramListViewModel @Inject constructor(
    private val repo: ProgramRepository,
    sessions: SessionRepository,
) : ViewModel() {
    val rows: StateFlow<List<ProgramRow>> = combine(
        repo.observePrograms(),
        sessions.observeAllCompletedSessions(),
    ) { programs, completed ->
        programs.map { p ->
            ProgramRow(p, ProgramProgress.compute(p, completed.filter { it.programId == p.id }))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun createBlank(name: String, daysPerWeek: Int, totalWeeks: Int, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repo.createProgram(name, daysPerWeek, totalWeeks)
            onCreated(id)
        }
    }

    fun importJson(raw: String, onResult: (Result<Long>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repo.importJson(ProgramJson.parse(raw)) })
        }
    }

    fun deleteProgram(program: Program) {
        viewModelScope.launch { repo.deleteProgram(program) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramListScreen(
    onOpenProgram: (Long) -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenProgress: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: ProgramListViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Program?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Tracker") },
                actions = {
                    IconButton(onClick = { showImport = true }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Import JSON")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New Program") },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(rows, key = { it.program.id }) { row ->
                    ProgramCard(
                        program = row.program,
                        progress = row.progress,
                        onOpen = { onOpenProgram(row.program.id) },
                        onStart = { onStartSession(row.program.id) },
                        onProgress = { onOpenProgress(row.program.id) },
                        onDelete = { deleteTarget = row.program },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateProgramDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, days, weeks ->
                vm.createBlank(name, days, weeks) { id ->
                    showCreate = false
                    onOpenProgram(id)
                }
            },
        )
    }

    if (showImport) {
        ImportJsonDialog(
            onDismiss = { showImport = false },
            onImport = { raw ->
                vm.importJson(raw) { result ->
                    showImport = false
                    result.getOrNull()?.let(onOpenProgram)
                }
            },
        )
    }

    deleteTarget?.let { program ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete program?") },
            text = {
                Text(
                    "“${program.name}” and all of its days, exercises, logged sessions, and 1RM records will be permanently removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProgram(program)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "No programs yet. Tap “New Program” to create one, or import a JSON file.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ProgramCard(
    program: Program,
    progress: ProgramProgress,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onProgress: () -> Unit,
    onDelete: () -> Unit,
) {
    val totalWorkouts = progress.totalWeeks * progress.daysPerWeek
    val completedWorkouts = (progress.currentWeek - 1).coerceAtLeast(0) * progress.daysPerWeek +
        progress.daysCompletedThisWeek
    val fraction = if (totalWorkouts > 0) completedWorkouts.toFloat() / totalWorkouts else 0f
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(program.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.padding(2.dp))
                    Text(
                        "${program.daysPerWeek} days/week · ${program.totalWeeks} weeks",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete program") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.padding(6.dp))
            val statusText = when {
                progress.isComplete -> "Program complete · $completedWorkouts/$totalWorkouts workouts"
                else -> "Week ${progress.currentWeek} of ${progress.totalWeeks} · ${progress.daysCompletedThisWeek}/${progress.daysPerWeek} days this week"
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Spacer(Modifier.padding(4.dp))
            Row {
                TextButton(onClick = onStart, enabled = !progress.isComplete) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(4.dp))
                    Text(if (progress.isComplete) "Done" else "Start")
                }
                TextButton(onClick = onProgress) {
                    Icon(Icons.Default.BarChart, null); Spacer(Modifier.width(4.dp)); Text("Progress")
                }
            }
        }
    }
}

@Composable
private fun CreateProgramDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Int, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("3") }
    var weeks by remember { mutableStateOf("6") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && days.toIntOrNull() in 1..7 && weeks.toIntOrNull() in 1..52,
                onClick = { onCreate(name.trim(), days.toInt(), weeks.toInt()) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New program") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text("Days per week") })
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(value = weeks, onValueChange = { weeks = it.filter(Char::isDigit) }, label = { Text("Total weeks") })
            }
        },
    )
}

@Composable
private fun ImportJsonDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var raw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = raw.isNotBlank(), onClick = { onImport(raw) }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Import program JSON") },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it },
                label = { Text("Paste JSON") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
                maxLines = 12,
            )
        },
    )
}
