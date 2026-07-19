package com.eatplease.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eatplease.app.detection.WatchState
import com.eatplease.app.di.AppGraph
import com.eatplease.app.platform.currentEpochMillis
import com.eatplease.app.settings.CameraFacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(graph: AppGraph, onOpenLog: () -> Unit) {
    val watchState by graph.sessionManager.state.collectAsState()
    val facing by graph.cameraSettings.facing.collectAsState()
    val scope = rememberCoroutineScope()

    // Ticks once a second while watching so "last seen Xs ago" stays fresh.
    var now by remember { mutableLongStateOf(currentEpochMillis()) }
    LaunchedEffect(watchState is WatchState.Watching) {
        while (watchState is WatchState.Watching) {
            now = currentEpochMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Eat Please", style = MaterialTheme.typography.headlineMedium)

        StatusCard(watchState, now)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Camera", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                CameraFacing.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = facing == entry,
                        onClick = { if (facing != entry) graph.cameraSettings.toggle() },
                        shape = SegmentedButtonDefaults.itemShape(index, CameraFacing.entries.size),
                    ) {
                        Text(if (entry == CameraFacing.FRONT) "Front" else "Back")
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                scope.launch {
                    if (watchState is WatchState.Watching) {
                        graph.watchController.stopWatching()
                    } else {
                        graph.watchController.startWatching()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(if (watchState is WatchState.Watching) "STOP WATCHING" else "START WATCHING")
        }

        TextButton(onClick = onOpenLog, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("View log ▸")
        }
    }
}

@Composable
private fun StatusCard(watchState: WatchState, nowEpochMillis: Long) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (watchState) {
                is WatchState.Idle -> {
                    Text("Not watching", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Prop the phone facing the table and press start.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is WatchState.Watching -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text("WATCHING", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "since ${formatTimeHm(watchState.startedAtEpochMs)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        eatingStatusLine(watchState, nowEpochMillis),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private fun eatingStatusLine(state: WatchState.Watching, nowEpochMillis: Long): String =
    when {
        state.isEatingNow -> "eating now ✓"
        state.lastEatingAtEpochMs != null -> {
            val secondsAgo = ((nowEpochMillis - state.lastEatingAtEpochMs) / 1000).coerceAtLeast(0)
            "last seen eating ${secondsAgo}s ago"
        }
        else -> "no eating detected yet"
    }
