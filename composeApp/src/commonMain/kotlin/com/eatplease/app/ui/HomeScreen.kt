package com.eatplease.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.Dp
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

    val onToggleWatch = {
        scope.launch {
            if (watching != null) graph.watchController.stopWatching()
            else graph.watchController.startWatching()
        }
        Unit
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            LandscapeHome(
                graph = graph,
                watchState = watchState,
                watching = watching,
                now = now,
                facing = facing,
                onToggleFacing = { graph.cameraSettings.toggle() },
                onToggleWatch = onToggleWatch,
            )
        } else {
            PortraitHome(
                graph = graph,
                watchState = watchState,
                watching = watching,
                now = now,
                facing = facing,
                onToggleFacing = { graph.cameraSettings.toggle() },
                onToggleWatch = onToggleWatch,
            )
        }
    }
}

@Composable
private fun PortraitHome(
    graph: AppGraph,
    watchState: WatchState,
    watching: WatchState.Watching?,
    now: Long,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        TitleRow(watching != null)
        VerdictHero(watchState, now)
        if (watching != null) {
            StatGrid(watching, liveStats(graph, watching, now))
        }
        CameraSection(
            active = watching != null,
            isEating = watching?.isEatingNow == true,
            facing = facing,
            onToggleFacing = onToggleFacing,
        )
        WatchButton(watching != null, onToggleWatch)
    }
}

/**
 * Landscape keeps every portrait box on screen: title, hero, 2×2 stats,
 * camera + facing toggles, and Start/Stop — no scrolling.
 *
 * Left column while watching: weighted hero (~35%) + stats (~65%) so the
 * 2×2 cards always get enough height for header + value + caption. Right
 * column: camera + CTA.
 */
@Composable
private fun LandscapeHome(
    graph: AppGraph,
    watchState: WatchState,
    watching: WatchState.Watching?,
    now: Long,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TitleRow(watching != null, compact = true)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (watching != null) {
                    VerdictHero(
                        watchState = watchState,
                        now = now,
                        compact = true,
                        fill = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.35f),
                    )
                    StatGrid(
                        watching = watching,
                        stats = liveStats(graph, watching, now),
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f),
                    )
                } else {
                    VerdictHero(
                        watchState = watchState,
                        now = now,
                        compact = true,
                        fill = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CameraSection(
                    active = watching != null,
                    isEating = watching?.isEatingNow == true,
                    facing = facing,
                    onToggleFacing = onToggleFacing,
                    cameraHeight = 80.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                WatchButton(watching != null, onToggleWatch)
            }
        }
    }
}

@Composable
private fun TitleRow(watching: Boolean, compact: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Eat Please",
            style = if (compact) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineMedium
            },
            color = NeoColors.Ink,
        )
        if (watching) NeoPill("WATCHING")
    }
}

@Composable
private fun WatchButton(watching: Boolean, onToggleWatch: () -> Unit) {
    NeoButton(
        text = if (watching) "STOP WATCHING" else "START WATCHING",
        backgroundColor = if (watching) NeoColors.Coral else NeoColors.Green,
        onClick = onToggleWatch,
        modifier = Modifier.fillMaxWidth(),
    )
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
private fun VerdictHero(
    watchState: WatchState,
    now: Long,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    fill: Boolean = false,
) {
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
    val verticalPad = if (compact) 4.dp else 28.dp
    NeoBox(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = color,
        cornerRadius = if (compact) 10.dp else 20.dp,
        shadowOffset = if (compact) 3.dp else 4.dp,
        contentPadding = PaddingValues(vertical = verticalPad, horizontal = 10.dp),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .then(if (fill) Modifier.fillMaxHeight() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                if (compact) 0.dp else 10.dp,
                if (fill) Alignment.CenterVertically else Alignment.Top,
            ),
        ) {
            Text(
                verdict,
                style = when {
                    compact -> MaterialTheme.typography.titleLarge
                    else -> MaterialTheme.typography.displayMedium
                },
                color = NeoColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                sub,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = NeoColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 1 else 3,
            )
        }
    }
}

@Composable
private fun StatGrid(
    watching: WatchState.Watching,
    stats: SessionStats,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val paceValue = if (stats.eatingSeconds == 0) "—" else stats.bitesPerMinute.roundedTo1().toString()
    val gapValue = if (stats.averageGapSeconds <= 0) "—" else "${stats.averageGapSeconds}s"
    val gap = if (compact) 4.dp else 12.dp
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth(),
    ) {
        StatRow(compact = compact, gap = gap) {
            NeoStatCard(
                "Pace", paceValue, "bites / min",
                NeoColors.OrangeHead, NeoColors.OrangeBody,
                expandBody = compact,
                compact = compact,
                modifier = Modifier.weight(1f).then(if (compact) Modifier.fillMaxHeight() else Modifier),
            )
            NeoStatCard(
                "Avg gap", gapValue, "between bites",
                NeoColors.MagentaHead, NeoColors.MagentaBody,
                headerTextColor = NeoColors.Cream,
                expandBody = compact,
                compact = compact,
                modifier = Modifier.weight(1f).then(if (compact) Modifier.fillMaxHeight() else Modifier),
            )
        }
        StatRow(compact = compact, gap = gap) {
            NeoStatCard(
                "Elapsed", mmss(stats.durationSeconds), "since ${formatTimeHm(watching.startedAtEpochMs)}",
                NeoColors.CyanHead, NeoColors.CyanBody,
                expandBody = compact,
                compact = compact,
                modifier = Modifier.weight(1f).then(if (compact) Modifier.fillMaxHeight() else Modifier),
            )
            NeoStatCard(
                "Eating", mmss(stats.eatingSeconds.toLong()), "this session",
                NeoColors.LimeHead, NeoColors.LimeBody,
                expandBody = compact,
                compact = compact,
                modifier = Modifier.weight(1f).then(if (compact) Modifier.fillMaxHeight() else Modifier),
            )
        }
    }
}

@Composable
private fun ColumnScope.StatRow(
    compact: Boolean,
    gap: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.weight(1f) else Modifier),
        content = content,
    )
}

/** Small kept camera thumbnail plus a Front/Back neo toggle. */
@Composable
private fun CameraSection(
    active: Boolean,
    isEating: Boolean,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
    modifier: Modifier = Modifier,
    cameraHeight: Dp = 150.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DetectionCameraFrame(active = active, isEating = isEating, height = cameraHeight)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f),
        ) {
            Text("CAMERA", style = MaterialTheme.typography.labelMedium, color = NeoColors.Ink)
            CameraFacing.entries.forEach { entry ->
                val selected = facing == entry
                NeoBox(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = if (selected) NeoColors.CyanBody else NeoColors.Cream,
                    cornerRadius = 12.dp,
                    shadowOffset = 3.dp,
                    onClick = { if (!selected) onToggleFacing() },
                    contentPadding = PaddingValues(vertical = 7.dp, horizontal = 10.dp),
                ) {
                    Text(
                        if (entry == CameraFacing.FRONT) "Front" else "Back",
                        style = MaterialTheme.typography.titleSmall,
                        color = NeoColors.Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.Center),
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
