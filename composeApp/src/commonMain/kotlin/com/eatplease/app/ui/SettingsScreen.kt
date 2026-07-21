package com.eatplease.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eatplease.app.di.AppGraph
import com.eatplease.app.di.Di
import com.eatplease.app.settings.AUDIO_POKE_MAX_PACE
import com.eatplease.app.settings.AUDIO_POKE_MIN_PACE
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    graph: AppGraph = Di.graph,
) {
    val audioPoke = graph.audioPokeSettings
    val enabled by audioPoke.enabled.collectAsState()
    val minPace by audioPoke.minPaceBitesPerMin.collectAsState()
    val hasRecording by audioPoke.hasRecording.collectAsState()
    val isRecording by audioPoke.recording.isRecording.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(title = "Settings")

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Audio poke", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Play your reminder when eating pace falls below the minimum.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { audioPoke.setEnabled(it) },
            )
        }

        if (enabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Minimum pace: $minPace bites/min",
                    style = MaterialTheme.typography.titleSmall,
                )
                Slider(
                    value = minPace.toFloat(),
                    onValueChange = { audioPoke.setMinPaceBitesPerMin(it.roundToInt()) },
                    valueRange = AUDIO_POKE_MIN_PACE.toFloat()..AUDIO_POKE_MAX_PACE.toFloat(),
                    steps = AUDIO_POKE_MAX_PACE - AUDIO_POKE_MIN_PACE - 1,
                )
                Text(
                    "1–5 bites per minute",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reminder recording", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        isRecording -> "Recording… (up to 5 seconds)"
                        hasRecording -> "Reminder saved on this device"
                        else -> "No reminder recorded yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRecording) {
                        Button(onClick = { audioPoke.stopRecording() }) {
                            Text("Stop")
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch { audioPoke.startRecording() }
                            },
                        ) {
                            Text(if (hasRecording) "Re-record" else "Record")
                        }
                    }
                    if (hasRecording && !isRecording) {
                        OutlinedButton(onClick = { audioPoke.eraseRecording() }) {
                            Text("Erase")
                        }
                    }
                }
            }
        }
    }
}
