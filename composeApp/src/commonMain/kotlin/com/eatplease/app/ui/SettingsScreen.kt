package com.eatplease.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eatplease.app.di.AppGraph
import com.eatplease.app.di.Di
import com.eatplease.app.settings.AUDIO_POKE_MAX_PACE
import com.eatplease.app.settings.AUDIO_POKE_MIN_PACE
import com.eatplease.app.ui.theme.NeoBox
import com.eatplease.app.ui.theme.NeoButton
import com.eatplease.app.ui.theme.NeoColors
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Settings is a top-level bottom-nav tab, so it renders its own big
        // inline title with no back arrow (no shared Header dependency).
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = NeoColors.Ink,
        )

        // Master enable toggle — magenta section card.
        NeoSectionCard(
            title = "Audio poke",
            headerColor = NeoColors.MagentaHead,
            bodyColor = NeoColors.MagentaBody,
            headerTextColor = NeoColors.Cream,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Play your reminder when eating pace falls below the minimum.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoColors.Ink,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { audioPoke.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeoColors.Ink,
                        checkedTrackColor = NeoColors.Green,
                        checkedBorderColor = NeoColors.Ink,
                        uncheckedThumbColor = NeoColors.Ink,
                        uncheckedTrackColor = NeoColors.Cream,
                        uncheckedBorderColor = NeoColors.Ink,
                    ),
                )
            }
        }

        if (enabled) {
            // Minimum-pace slider — cyan section card.
            NeoSectionCard(
                title = "Minimum pace",
                headerColor = NeoColors.CyanHead,
                bodyColor = NeoColors.CyanBody,
            ) {
                Text(
                    "Minimum pace: $minPace bites/min",
                    style = MaterialTheme.typography.titleSmall,
                    color = NeoColors.Ink,
                )
                Slider(
                    value = minPace.toFloat(),
                    onValueChange = { audioPoke.setMinPaceBitesPerMin(it.roundToInt()) },
                    valueRange = AUDIO_POKE_MIN_PACE.toFloat()..AUDIO_POKE_MAX_PACE.toFloat(),
                    steps = AUDIO_POKE_MAX_PACE - AUDIO_POKE_MIN_PACE - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = NeoColors.Ink,
                        activeTrackColor = NeoColors.MagentaHead,
                        inactiveTrackColor = NeoColors.Cream,
                        activeTickColor = NeoColors.Ink,
                        inactiveTickColor = NeoColors.Ink,
                    ),
                )
                Text(
                    "1–5 bites per minute",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoColors.Ink,
                )
            }

            // Recording controls — orange section card.
            NeoSectionCard(
                title = "Reminder recording",
                headerColor = NeoColors.OrangeHead,
                bodyColor = NeoColors.OrangeBody,
            ) {
                Text(
                    when {
                        isRecording -> "Recording… (up to 5 seconds)"
                        hasRecording -> "Reminder saved on this device"
                        else -> "No reminder recorded yet"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeoColors.Ink,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRecording) {
                        NeoButton(
                            text = "Stop",
                            backgroundColor = NeoColors.Coral,
                            onClick = { audioPoke.stopRecording() },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        NeoButton(
                            text = if (hasRecording) "Re-record" else "Record",
                            backgroundColor = NeoColors.Coral,
                            onClick = { scope.launch { audioPoke.startRecording() } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (hasRecording && !isRecording) {
                        NeoButton(
                            text = "Erase",
                            backgroundColor = NeoColors.Cream,
                            onClick = { audioPoke.eraseRecording() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A neo section card: a colored header strip carrying an uppercase title over a
 * hard ink divider, with the given [content] stacked on a lighter body fill.
 * Mirrors the stat-card / reference tile look, but sized for full-width settings
 * rows rather than the compact 2x2 grid.
 */
@Composable
private fun NeoSectionCard(
    title: String,
    headerColor: Color,
    bodyColor: Color,
    modifier: Modifier = Modifier,
    headerTextColor: Color = NeoColors.Ink,
    content: @Composable ColumnScope.() -> Unit,
) {
    NeoBox(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = bodyColor,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor),
            ) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = headerTextColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // Divider echoes the hard border between header and body.
            Box(Modifier.fillMaxWidth().height(3.dp).background(NeoColors.Ink))
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
