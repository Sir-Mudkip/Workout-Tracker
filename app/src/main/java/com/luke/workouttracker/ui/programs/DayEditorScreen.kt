package com.luke.workouttracker.ui.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.entities.PlannedExercise
import com.luke.workouttracker.data.db.entities.PlannedSet
import com.luke.workouttracker.data.repo.ProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DayEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: ProgramRepository,
) : ViewModel() {
    val programId: Long = checkNotNull(handle["programId"])
    val dayId: Long = checkNotNull(handle["dayId"])

    val exercises: StateFlow<List<PlannedExercise>> =
        repo.observeExercises(dayId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _setsByExercise = MutableStateFlow<Map<Long, List<PlannedSet>>>(emptyMap())
    val setsByExercise: StateFlow<Map<Long, List<PlannedSet>>> = _setsByExercise.asStateFlow()

    init {
        viewModelScope.launch {
            exercises.collect { list ->
                val map = mutableMapOf<Long, List<PlannedSet>>()
                list.forEach { ex -> map[ex.id] = repo.setsForExercise(ex.id) }
                _setsByExercise.value = map
            }
        }
    }

    fun addExercise(name: String, startingWeight: Double, sets: List<Pair<Int, Double?>>, isBodyweight: Boolean) {
        viewModelScope.launch { repo.addExercise(dayId, name, startingWeight, sets, isBodyweight) }
    }

    fun deleteExercise(exercise: PlannedExercise) {
        viewModelScope.launch { repo.deleteExercise(exercise) }
    }

    /** direction: -1 = up, +1 = down. */
    fun moveExercise(exercise: PlannedExercise, direction: Int) {
        viewModelScope.launch { repo.moveExercise(dayId, exercise.id, direction) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayEditorScreen(
    onBack: () -> Unit,
    vm: DayEditorViewModel = hiltViewModel(),
) {
    val exercises by vm.exercises.collectAsState()
    val sets by vm.setsByExercise.collectAsState()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit day") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add exercise") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(exercises, key = { _, ex -> ex.id }) { idx, ex ->
                ExerciseCard(
                    exercise = ex,
                    sets = sets[ex.id].orEmpty(),
                    canMoveUp = idx > 0,
                    canMoveDown = idx < exercises.lastIndex,
                    onMoveUp = { vm.moveExercise(ex, -1) },
                    onMoveDown = { vm.moveExercise(ex, 1) },
                    onDelete = { vm.deleteExercise(ex) },
                )
            }
        }
    }

    if (showAdd) {
        AddExerciseDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, weight, setRows, isBw ->
                vm.addExercise(name, weight, setRows, isBw)
                showAdd = false
            },
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: PlannedExercise,
    sets: List<PlannedSet>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    val startLabel = if (exercise.isBodyweight) {
                        if (exercise.startingWeight == 0.0) "Bodyweight"
                        else "Bodyweight + ${trimNumber(exercise.startingWeight)} kg"
                    } else {
                        "Starting weight: ${trimNumber(exercise.startingWeight)} kg"
                    }
                    Text(startLabel, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            sets.forEach { s ->
                val weightText = s.targetWeightOverride?.let { "${trimNumber(it)} kg" } ?: "↑ uses starting weight"
                Text("Set ${s.setNumber}: ${s.targetReps} reps · $weightText", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AddExerciseDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, startingWeight: Double, sets: List<Pair<Int, Double?>>, isBodyweight: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startWeight by remember { mutableStateOf("") }
    var isBodyweight by remember { mutableStateOf(false) }
    val rows = remember { mutableStateListOf(SetRowState("8", "")) }

    val effectiveStartWeight = if (isBodyweight && startWeight.isBlank()) 0.0 else startWeight.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && effectiveStartWeight != null && rows.all { it.reps.toIntOrNull() != null },
                onClick = {
                    val parsed = rows.map { row -> row.reps.toInt() to row.weight.toDoubleOrNull() }
                    onConfirm(name.trim(), effectiveStartWeight ?: 0.0, parsed, isBodyweight)
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add exercise") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = isBodyweight,
                        onCheckedChange = { isBodyweight = it },
                    )
                    Text("Bodyweight exercise")
                }
                OutlinedTextField(
                    value = startWeight,
                    onValueChange = { startWeight = it.filter { c -> c.isDigit() || c == '.' } },
                    label = {
                        Text(if (isBodyweight) "Added weight (kg, 0 = pure BW)" else "Starting weight (kg)")
                    },
                    placeholder = { if (isBodyweight) Text("0") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Sets", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                rows.forEachIndexed { idx, row ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Set ${idx + 1}", modifier = Modifier.width(56.dp))
                        OutlinedTextField(
                            value = row.reps,
                            onValueChange = { v -> rows[idx] = row.copy(reps = v.filter(Char::isDigit)) },
                            label = { Text("Reps") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.weight,
                            onValueChange = { v -> rows[idx] = row.copy(weight = v.filter { c -> c.isDigit() || c == '.' }) },
                            label = { Text("kg (opt)") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row {
                    OutlinedButton(onClick = { rows.add(SetRowState("8", "")) }) { Text("+ Set") }
                    if (rows.size > 1) {
                        TextButton(onClick = { rows.removeAt(rows.size - 1) }) { Text("− Set") }
                    }
                }
            }
        },
    )
}

private data class SetRowState(val reps: String, val weight: String)

private fun trimNumber(d: Double): String =
    if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
