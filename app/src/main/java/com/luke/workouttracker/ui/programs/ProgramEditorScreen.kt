package com.luke.workouttracker.ui.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.entities.Program
import com.luke.workouttracker.data.db.entities.WorkoutDay
import com.luke.workouttracker.data.json.ExportHelper
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

@HiltViewModel
class ProgramEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProgramRepository,
    sessions: SessionRepository,
) : ViewModel() {
    val programId: Long = checkNotNull(handle["programId"])

    val program: StateFlow<Program?> = repo.observeProgram(programId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val days: StateFlow<List<WorkoutDay>> = repo.observeDays(programId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<ProgramProgress?> = combine(
        repo.observeProgram(programId),
        sessions.observeCompletedSessions(programId),
    ) { p, completed -> p?.let { ProgramProgress.compute(it, completed) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun renameProgram(newName: String) {
        viewModelScope.launch { repo.renameProgram(programId, newName) }
    }

    fun renameDay(dayId: Long, newName: String) {
        viewModelScope.launch { repo.renameDay(dayId, newName) }
    }

    fun moveDayUp(dayId: Long) {
        viewModelScope.launch { repo.moveDay(programId, dayId, -1) }
    }

    fun moveDayDown(dayId: Long) {
        viewModelScope.launch { repo.moveDay(programId, dayId, +1) }
    }

    fun deleteDay(day: WorkoutDay) {
        viewModelScope.launch { repo.deleteDay(day) }
    }

    fun export(context: android.content.Context, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val full = repo.getFullProgram(programId)
            if (full == null) { onResult(null); return@launch }
            val body = ProgramJson.encode(ProgramJson.fromFull(full))
            val uri = ExportHelper.writeProgramJson(context, full.program.name, body)
            onResult(uri?.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramEditorScreen(
    onOpenDay: (Long, Long) -> Unit,
    onOpenPeakDay: (Long) -> Unit,
    onBack: () -> Unit,
    vm: ProgramEditorViewModel = hiltViewModel(),
) {
    val program by vm.program.collectAsState()
    val days by vm.days.collectAsState()
    val progress by vm.progress.collectAsState()
    val ctx = LocalContext.current
    var renameProgramOpen by remember { mutableStateOf(false) }
    var renameDayTarget by remember { mutableStateOf<WorkoutDay?>(null) }
    var deleteDayTarget by remember { mutableStateOf<WorkoutDay?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(program?.name ?: "Program") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { renameProgramOpen = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename program")
                    }
                    IconButton(onClick = {
                        vm.export(ctx) { uri ->
                            val msg = if (uri != null) "Exported to Downloads" else "Export failed"
                            android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Download, contentDescription = "Export JSON")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                program?.let { p ->
                    Text(
                        "${p.daysPerWeek} days/week · ${p.totalWeeks} weeks",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    progress?.let { pr ->
                        Text(
                            if (pr.isComplete) "Program complete"
                            else "Currently on Week ${pr.currentWeek} of ${pr.totalWeeks} · ${pr.daysCompletedThisWeek}/${pr.daysPerWeek} days done this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPeakDay(vm.programId) },
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
                                "Set starting 1RMs now; record ending on the last day.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            itemsIndexed(days) { idx, day ->
                DayCard(
                    day = day,
                    isFirst = idx == 0,
                    isLast = idx == days.lastIndex,
                    onOpen = { onOpenDay(vm.programId, day.id) },
                    onRename = { renameDayTarget = day },
                    onMoveUp = { vm.moveDayUp(day.id) },
                    onMoveDown = { vm.moveDayDown(day.id) },
                    onDelete = { deleteDayTarget = day },
                )
            }
        }
    }

    if (renameProgramOpen) {
        program?.let { p ->
            TextInputDialog(
                title = "Rename program",
                initialValue = p.name,
                label = "Name",
                onDismiss = { renameProgramOpen = false },
                onConfirm = { newName ->
                    vm.renameProgram(newName)
                    renameProgramOpen = false
                },
            )
        }
    }

    renameDayTarget?.let { day ->
        TextInputDialog(
            title = "Rename day",
            initialValue = day.name,
            label = "Day name",
            onDismiss = { renameDayTarget = null },
            onConfirm = { newName ->
                vm.renameDay(day.id, newName)
                renameDayTarget = null
            },
        )
    }

    deleteDayTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteDayTarget = null },
            title = { Text("Delete day?") },
            text = {
                Text(
                    "“Day ${day.dayIndex}: ${day.name}” will be removed, along with its exercises and any logged sessions for that day. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteDay(day)
                    deleteDayTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteDayTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DayCard(
    day: WorkoutDay,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen),
                ) {
                    Text("Day ${day.dayIndex}: ${day.name}", style = MaterialTheme.typography.titleMedium)
                    Text("Tap to edit exercises", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete day")
                }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initialValue: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank() && value != initialValue,
                onClick = { onConfirm(value.trim()) },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
