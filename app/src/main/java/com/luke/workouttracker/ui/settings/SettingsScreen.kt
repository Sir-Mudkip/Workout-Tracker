package com.luke.workouttracker.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luke.workouttracker.BuildConfig
import com.luke.workouttracker.data.prefs.BodyweightPrefs
import com.luke.workouttracker.data.prefs.ThemeMode
import com.luke.workouttracker.data.prefs.ThemePrefs
import com.luke.workouttracker.data.updates.UpdateChecker
import com.luke.workouttracker.data.updates.UpdateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val info: UpdateResult.Available) : UpdateUiState
    data class Downloading(val fraction: Float) : UpdateUiState
    data class ReadyToInstall(val apk: File, val version: String) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePrefs,
    private val bodyweightPrefs: BodyweightPrefs,
    private val updateChecker: UpdateChecker,
) : ViewModel() {
    val mode: StateFlow<ThemeMode> = themePrefs.mode
    val bodyweight: StateFlow<Double> = bodyweightPrefs.kg
    fun setMode(m: ThemeMode) = themePrefs.set(m)
    fun setBodyweight(kg: Double) = bodyweightPrefs.set(kg)

    private val _update = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    fun checkForUpdates() {
        if (_update.value is UpdateUiState.Checking || _update.value is UpdateUiState.Downloading) return
        _update.value = UpdateUiState.Checking
        viewModelScope.launch {
            _update.value = when (val r = updateChecker.check()) {
                is UpdateResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateResult.Available -> UpdateUiState.Available(r)
                is UpdateResult.Error -> UpdateUiState.Error(r.message)
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val available = (_update.value as? UpdateUiState.Available) ?: return
        _update.value = UpdateUiState.Downloading(0f)
        viewModelScope.launch {
            runCatching {
                updateChecker.downloadApk(context, available.info.apkUrl) { f ->
                    _update.value = UpdateUiState.Downloading(f)
                }
            }.onSuccess { file ->
                _update.value = UpdateUiState.ReadyToInstall(file, available.info.latestVersion)
            }.onFailure { t ->
                _update.value = UpdateUiState.Error(t.message ?: "Download failed")
            }
        }
    }

    fun launchInstall(activity: Activity) {
        val ready = (_update.value as? UpdateUiState.ReadyToInstall) ?: return
        updateChecker.launchInstaller(activity, ready.apk)
    }

    fun dismissUpdateState() {
        _update.value = UpdateUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val current by vm.mode.collectAsState()
    val bodyweight by vm.bodyweight.collectAsState()
    val updateState by vm.update.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("My bodyweight")
            Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                BodyweightField(
                    value = bodyweight,
                    onChange = vm::setBodyweight,
                )
            }

            SectionHeader("Theme")
            Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setMode(mode) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == current, onClick = { vm.setMode(mode) })
                        Text(
                            text = when (mode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }

            SectionHeader("App version")
            Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                UpdateSection(
                    state = updateState,
                    onCheck = vm::checkForUpdates,
                    onDownload = { vm.downloadUpdate(context) },
                    onInstall = {
                        context.findActivity()?.let(vm::launchInstall)
                    },
                    onDismiss = vm::dismissUpdateState,
                )
            }
        }
    }
}

@Composable
private fun UpdateSection(
    state: UpdateUiState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(16.dp)) {
        Text("Installed: v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)

        val (statusText, busy) = when (state) {
            UpdateUiState.Idle -> "" to false
            UpdateUiState.Checking -> "Checking GitHub for a newer release…" to true
            UpdateUiState.UpToDate -> "You're on the latest version." to false
            is UpdateUiState.Available ->
                "Update available: v${state.info.latestVersion} (${formatBytes(state.info.sizeBytes)})" to false
            is UpdateUiState.Downloading -> "Downloading: ${(state.fraction * 100).toInt()}%" to true
            is UpdateUiState.ReadyToInstall -> "Downloaded v${state.version}. Tap install." to false
            is UpdateUiState.Error -> "Couldn't check: ${state.message}" to false
        }
        if (statusText.isNotEmpty()) {
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (state is UpdateUiState.Downloading) {
            LinearProgressIndicator(
                progress = { state.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is UpdateUiState.Available -> {
                    Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                        Text("Download")
                    }
                    Button(onClick = onDismiss) { Text("Later") }
                }
                is UpdateUiState.ReadyToInstall -> {
                    Button(onClick = onInstall, modifier = Modifier.weight(1f)) {
                        Text("Install")
                    }
                    Button(onClick = onDismiss) { Text("Cancel") }
                }
                else -> {
                    Button(
                        onClick = onCheck,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (busy) "Working…" else "Check for updates") }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BodyweightField(value: Double, onChange: (Double) -> Unit) {
    val initial = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    var text by remember(value) { mutableStateOf(initial) }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                val filtered = v.filter { c -> c.isDigit() || c == '.' }
                text = filtered
                filtered.toDoubleOrNull()?.let { onChange(it) }
            },
            label = { Text("Bodyweight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Used to calculate volume on bodyweight exercises (reps × (added + bodyweight)).",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
