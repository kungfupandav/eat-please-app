package com.eatplease.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eatplease.app.detection.SessionStats
import com.eatplease.app.detection.WatchState
import com.eatplease.app.detection.roundedTo1
import com.eatplease.app.di.AppGraph
import com.eatplease.app.platform.currentEpochMillis
import com.eatplease.app.settings.CameraFacing
import com.eatplease.app.ui.theme.NeoBox
import com.eatplease.app.ui.theme.NeoButton
import com.eatplease.app.ui.theme.NeoColors
import com.eatplease.app.ui.theme.NeoPill
import com.eatplease.app.ui.theme.NeoStatCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(graph: AppGraph) {
    val watchState by graph.sessionManager.state.collectAsState()
    val facing by graph.cameraSettings.facing.collectAsState()
    val scope = rememberCoroutineScope()
    val watching = watchState as? WatchState.Watching

    // Ticks once a second while watching so the live stats stay fresh.
    var now by remember { mutableLongStateOf(currentEpochMillis()) }
    LaunchedEffect(watchState is WatchState.Watching) {
        while (watchState is WatchState.Watching) {
            now = currentEpochMillis()
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Eat Please", style = MaterialTheme.typography.headlineMedium, color = NeoColors.Ink)
            if (watching != null) NeoPill("WATCHING")
        }

        VerdictHero(watchState, now)

        if (watching != null) {
            val stats = liveStats(graph, watching, now)
            StatGrid(watching, stats)
        }

        CameraSection(
            active = watching != null,
            isEating = watching?.isEatingNow == true,
            facing = facing,
            onToggleFacing = { graph.cameraSettings.toggle() },
        )

        NeoButton(
            text = if (watching != null) "STOP WATCHING" else "START WATCHING",
            backgroundColor = if (watching != null) NeoColors.Coral else NeoColors.Green,
            onClick = {
                scope.launch {
                    if (watching != null) graph.watchController.stopWatching()
                    else graph.watchController.startWatching()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun liveStats(graph: AppGraph, watching: WatchState.Watching, now: Long): SessionStats {
    // Events grow as detection records seconds and `now` ticks, so both update in place.
    val events by remember(watching.sessionId) {
        graph.repository.eventsForSession(watching.sessionId)
    }.collectAsState(initial = emptyList())
    return remember(events, now) {
        graph.paceAnalyzer.analyze(
            eatingSeconds = events.map { it.atEpochSecond },
            sessionStartEpochSecond = watching.startedAtEpochMs / 1000,
            sessionEndEpochSecond = now / 1000,
        )
    }
}

/** Big color-block verdict — the emotional center of Home, mirroring the reference hero. */
@Composable
private fun VerdictHero(watchState: WatchState, now: Long) {
    val (color, verdict, sub) = when (watchState) {
        is WatchState.Idle -> Triple(
            NeoColors.CreamDeep, "Ready",
            "Prop the phone at the table and press start.",
        )
        is WatchState.Watching -> when {
            watchState.isEatingNow -> Triple(NeoColors.Green, "Eating", "Nice steady pace — keep going.")
            watchState.lastEatingAtEpochMs != null -> {
                val ago = ((now - watchState.lastEatingAtEpochMs) / 1000).coerceAtLeast(0)
                Triple(NeoColors.Coral, "Paused", "No bite for ${ago}s.")
            }
            else -> Triple(NeoColors.Yellow, "Watching", "Waiting for the first bite.")
        }
    }
    NeoBox(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = color,
        cornerRadius = 20.dp,
        contentPadding = PaddingValues(vertical = 28.dp, horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(verdict, style = MaterialTheme.typography.displayMedium, color = NeoColors.Ink)
            Text(
                sub,
                style = MaterialTheme.typography.bodyMedium,
                color = NeoColors.Ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StatGrid(watching: WatchState.Watching, stats: SessionStats) {
    val paceValue = if (stats.eatingSeconds == 0) "—" else stats.bitesPerMinute.roundedTo1().toString()
    val gapValue = if (stats.averageGapSeconds <= 0) "—" else "${stats.averageGapSeconds}s"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            NeoStatCard(
                "Pace", paceValue, "bites / min",
                NeoColors.OrangeHead, NeoColors.OrangeBody, modifier = Modifier.weight(1f),
            )
            NeoStatCard(
                "Avg gap", gapValue, "between bites",
                NeoColors.MagentaHead, NeoColors.MagentaBody,
                headerTextColor = NeoColors.Cream, modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            NeoStatCard(
                "Elapsed", mmss(stats.durationSeconds), "since ${formatTimeHm(watching.startedAtEpochMs)}",
                NeoColors.CyanHead, NeoColors.CyanBody, modifier = Modifier.weight(1f),
            )
            NeoStatCard(
                "Eating", mmss(stats.eatingSeconds.toLong()), "this session",
                NeoColors.LimeHead, NeoColors.LimeBody, modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Small kept camera thumbnail plus a Front/Back neo toggle. */
@Composable
private fun CameraSection(
    active: Boolean,
    isEating: Boolean,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetectionCameraFrame(active = active, isEating = isEating)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text("CAMERA", style = MaterialTheme.typography.labelMedium, color = NeoColors.Ink)
            CameraFacing.entries.forEach { entry ->
                val selected = facing == entry
                NeoBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = if (selected) NeoColors.CyanBody else NeoColors.Cream,
                    cornerRadius = 12.dp,
                    shadowOffset = 3.dp,
                    onClick = { if (!selected) onToggleFacing() },
                    contentPadding = PaddingValues(vertical = 9.dp, horizontal = 12.dp),
                ) {
                    Text(
                        if (entry == CameraFacing.FRONT) "Front" else "Back",
                        style = MaterialTheme.typography.titleSmall,
                        color = NeoColors.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun mmss(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
