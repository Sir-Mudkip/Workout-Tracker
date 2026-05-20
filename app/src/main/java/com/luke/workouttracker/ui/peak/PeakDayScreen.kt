package com.luke.workouttracker.ui.peak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.luke.workouttracker.data.db.entities.PeakLift
import com.luke.workouttracker.data.db.entities.PeakResult
import com.luke.workouttracker.data.repo.PeakRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PeakDayViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: PeakRepository,
) : ViewModel() {
    val programId: Long = checkNotNull(handle["programId"])

    val results: StateFlow<List<PeakResult>> = repo.observeForProgram(programId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setStarting(lift: PeakLift, value: Double?) {
        viewModelScope.launch { repo.setStarting(programId, lift, value) }
    }

    fun setEnding(lift: PeakLift, value: Double?) {
        viewModelScope.launch { repo.setEnding(programId, lift, value) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeakDayScreen(
    onBack: () -> Unit,
    vm: PeakDayViewModel = hiltViewModel(),
) {
    val results by vm.results.collectAsState()
    val byLift = results.associateBy { it.lift }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Peak day · 1RM test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                Text(
                    "Plug in your starting 1RM at the beginning of the block, then come back on the last day and enter your new 1RM.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(PeakLift.entries, key = { it.name }) { lift ->
                LiftCard(
                    lift = lift,
                    result = byLift[lift.name],
                    onStartChange = { vm.setStarting(lift, it) },
                    onEndChange = { vm.setEnding(lift, it) },
                )
            }
        }
    }
}

@Composable
private fun LiftCard(
    lift: PeakLift,
    result: PeakResult?,
    onStartChange: (Double?) -> Unit,
    onEndChange: (Double?) -> Unit,
) {
    val start = result?.startingOneRm
    val end = result?.endingOneRm
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(lift.displayName, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WeightField(
                    value = start,
                    label = "Starting 1RM",
                    onValueChange = onStartChange,
                    modifier = Modifier.weight(1f),
                )
                WeightField(
                    value = end,
                    label = "Ending 1RM",
                    onValueChange = onEndChange,
                    modifier = Modifier.weight(1f),
                )
            }
            if (start != null && end != null) {
                val delta = end - start
                val pct = if (start > 0) delta / start * 100.0 else 0.0
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "Δ $sign${trim(delta)} kg · $sign${"%.1f".format(pct)}%",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WeightField(
    value: Double?,
    label: String,
    onValueChange: (Double?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value?.let { trim(it) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            val filtered = v.filter { c -> c.isDigit() || c == '.' }
            text = filtered
            onValueChange(filtered.toDoubleOrNull())
        },
        label = { Text(label) },
        suffix = { Text("kg") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun trim(d: Double): String = if (d % 1.0 == 0.0) d.toInt().toString() else d.toString()
