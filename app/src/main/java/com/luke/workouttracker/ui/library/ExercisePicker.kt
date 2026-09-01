package com.luke.workouttracker.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luke.workouttracker.data.library.filterExercises

/**
 * Name field backed by the exercise library.
 *
 * Shows up to 8 matches as the user types. When nothing matches, offers to use
 * the typed text as-is, with a checkbox to add it to the library.
 *
 * State is hoisted: the caller owns [query] and [saveToLibrary].
 */
@Composable
fun ExercisePicker(
    names: List<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    saveToLibrary: Boolean,
    onSaveToLibraryChange: (Boolean) -> Unit,
    onNameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val matches = filterExercises(names, query)
    val isExactMatch = names.any { it.equals(query.trim(), ignoreCase = true) }
    val showCreateOption = query.isNotBlank() && !isExactMatch

    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Exercise") },
            modifier = Modifier.fillMaxWidth(),
        )

        matches.forEach { name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNameSelected(name) }
                    .padding(vertical = 10.dp),
            )
        }

        if (showCreateOption) {
            if (matches.isNotEmpty()) HorizontalDivider()
            Text(
                "Use \"${query.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNameSelected(query.trim()) }
                    .padding(vertical = 10.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = saveToLibrary, onCheckedChange = onSaveToLibraryChange)
                Text("Also save to library", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
