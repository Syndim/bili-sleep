package org.syndim.bilisleep.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.syndim.bilisleep.BuildConfig
import org.syndim.bilisleep.data.model.SleepTimerSettings

/**
 * Dialog for configuring sleep timer settings.
 * Shows "Start Timer" when timer is stopped, "Save" when timer is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentSettings: SleepTimerSettings,
    onSaveSettings: (durationMinutes: Int, fadeOutEnabled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDuration by remember { mutableIntStateOf(currentSettings.durationMinutes) }
    var fadeOutEnabled by remember { mutableStateOf(currentSettings.fadeOutEnabled) }
    val isTimerActive = currentSettings.enabled
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sleep Timer Settings")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Auto-pause after:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Duration options
                val durations = if (BuildConfig.DEBUG) {
                    listOf(1) + SleepTimerSettings.PRESET_DURATIONS
                } else {
                    SleepTimerSettings.PRESET_DURATIONS
                }
                
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(durations) { duration ->
                        DurationOption(
                            duration = duration,
                            isSelected = selectedDuration == duration,
                            onClick = { selectedDuration = duration }
                        )
                    }
                }
                
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
                
                // Fade out option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Fade out",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Gradually decrease volume",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = fadeOutEnabled,
                        onCheckedChange = { fadeOutEnabled = it }
                    )
                }
                
                // Info text about timer status
                Text(
                    text = if (isTimerActive) {
                        "Timer is currently running"
                    } else {
                        "Timer starts automatically when playback begins"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveSettings(selectedDuration, fadeOutEnabled) }
            ) {
                Text(if (isTimerActive) "Save" else "Start Timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DurationOption(
    duration: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isSelected) 0.dp else 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            if (isSelected) {
                RadioButton(
                    selected = true,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

private fun formatDuration(minutes: Int): String {
    return when {
        minutes < 60 -> "$minutes minutes"
        minutes == 60 -> "1 hour"
        else -> {
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            if (remainingMinutes == 0) {
                "$hours hours"
            } else {
                "$hours hour ${remainingMinutes} minutes"
            }
        }
    }
}
