package com.luke.workouttracker.ui.progress

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.data.db.dao.ExerciseWeeklyVolume
import com.luke.workouttracker.data.prefs.BodyweightPrefs
import com.luke.workouttracker.data.repo.SessionRepository
import com.luke.workouttracker.ui.theme.TraceChart
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ProgressViewModel @Inject constructor(
    handle: SavedStateHandle,
    sessions: SessionRepository,
    bodyweightPrefs: BodyweightPrefs,
) : ViewModel() {
    val programId: Long = checkNotNull(handle["programId"])

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<ExerciseWeeklyVolume>> =
        bodyweightPrefs.kg
            .flatMapLatest { bw -> sessions.observeWeeklyVolume(programId, bw) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

data class ExerciseProgress(
    val exerciseId: Long,
    val name: String,
    val weeks: List<Int>,
    val volumes: List<Double>,
    val firstVolume: Double,
    val lastVolume: Double,
    val pctChange: Double?,
    /** Week number to the replacement name performed that week. */
    val swapsByWeek: Map<Int, String>,
)

private fun List<ExerciseWeeklyVolume>.toProgress(): List<ExerciseProgress> =
    groupBy { it.plannedExerciseId }
        .map { (id, list) ->
            val sorted = list.sortedBy { it.weekNumber }
            val first = sorted.first().totalVolume
            val last = sorted.last().totalVolume
            ExerciseProgress(
                exerciseId = id,
                name = sorted.first().exerciseName,
                weeks = sorted.map { it.weekNumber },
                volumes = sorted.map { it.totalVolume },
                firstVolume = first,
                lastVolume = last,
                pctChange = if (first > 0) (last - first) / first * 100.0 else null,
                swapsByWeek = sorted.mapNotNull { row ->
                    row.swappedTo?.let { row.weekNumber to it }
                }.toMap(),
            )
        }
        .sortedBy { it.name }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    vm: ProgressViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsState()
    val progress = remember(rows) { rows.toProgress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress") },
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
            if (progress.isEmpty()) {
                item {
                    Text(
                        "No logged sets yet. Complete a workout to see progress.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                item { TopGainsCard(progress) }
                itemsIndexed(progress, key = { _, p -> p.exerciseId }) { index, p ->
                    ExerciseProgressCard(p, highlighted = index == 0)
                }
            }
        }
    }
}

@Composable
private fun TopGainsCard(progress: List<ExerciseProgress>) {
    val ranked = progress.filter { it.pctChange != null && it.weeks.size > 1 }
        .sortedByDescending { it.pctChange }
    if (ranked.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Top gains", style = MaterialTheme.typography.titleMedium)
            ranked.take(5).forEach { p ->
                val sign = if ((p.pctChange ?: 0.0) >= 0) "+" else ""
                Text(
                    "${p.name}: $sign${"%.1f".format(p.pctChange)}%  (${fmt(p.firstVolume)} → ${fmt(p.lastVolume)})",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ExerciseProgressCard(p: ExerciseProgress, highlighted: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium)
            val change = p.pctChange
            val subtitle = if (change != null) {
                val sign = if (change >= 0) "+" else ""
                "Week ${p.weeks.first()} → ${p.weeks.last()}: ${fmt(p.firstVolume)} → ${fmt(p.lastVolume)} ($sign${"%.1f".format(change)}%)"
            } else {
                "Latest volume: ${fmt(p.lastVolume)}"
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
            TraceChart(
                values = p.volumes,
                highlighted = highlighted,
                hollowAt = p.weeks.withIndex()
                    .filter { (_, week) -> p.swapsByWeek.containsKey(week) }
                    .map { it.index }
                    .toSet(),
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "W${p.weeks.first()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "W${p.weeks.last()} · ${fmt(p.lastVolume)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.swapsByWeek.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp)) {
                    p.swapsByWeek.toSortedMap().forEach { (week, name) ->
                        Text(
                            "* W$week swapped: $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

private fun fmt(d: Double): String = "%.0f".format(d)
