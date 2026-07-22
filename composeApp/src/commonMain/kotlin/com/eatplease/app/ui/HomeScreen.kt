package com.eatplease.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eatplease.app.detection.SessionStats
import com.eatplease.app.detection.WatchState
import com.eatplease.app.detection.roundedTo1
import com.eatplease.app.di.AppGraph
import com.eatplease.app.platform.currentEpochMillis
import com.eatplease.app.settings.AudioPokeDecision
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
    val isPlaying by graph.audioPokeSettings.isPlaying.collectAsState()
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

    // Plays the reminder when the live pace drops below the minimum (rules in
    // AudioPokeDecision). Kept outside the landscape/portrait split so it runs once.
    AudioPokeEffect(graph, watching, now)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            LandscapeHome(
                graph = graph,
                watchState = watchState,
                watching = watching,
                now = now,
                facing = facing,
                poking = isPlaying,
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
                poking = isPlaying,
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
    poking: Boolean,
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
        VerdictHero(watchState, now, poking = poking)
        CameraStatsSection(
            active = watching != null,
            isEating = watching?.isEatingNow == true,
            watching = watching,
            stats = watching?.let { liveStats(graph, it, now) },
            facing = facing,
            onToggleFacing = onToggleFacing,
        )
        WatchButton(watching != null, onToggleWatch)
    }
}

/**
 * Landscape is a three-column split under a compact title:
 *
 * - Col 1 (1/3): the verdict box (which also carries the "Please eat!" poke
 *   flash) above the 2×2 stats grid.
 * - Col 2 (1/3): the live video, sized as tall as it fits.
 * - Col 3 (1/3): the facing toggle and the Start/Stop button.
 */
@Composable
private fun LandscapeHome(
    graph: AppGraph,
    watchState: WatchState,
    watching: WatchState.Watching?,
    now: Long,
    facing: CameraFacing,
    poking: Boolean,
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
            // Col 1 (1/3) — verdict box above the stats.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VerdictHero(
                    watchState = watchState,
                    now = now,
                    compact = true,
                    fill = true,
                    poking = poking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (watching != null) 0.35f else 1f),
                )
                if (watching != null) {
                    StatGrid(
                        watching = watching,
                        stats = liveStats(graph, watching, now),
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.65f),
                    )
                }
            }

            // Col 2 — video, as tall as it can fit (height-driven 3:4, centered).
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                DetectionCameraFrame(
                    active = watching != null,
                    isEating = watching?.isEatingNow == true,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true),
                )
            }

            // Col 3 — facing toggle + Start/Stop.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                CameraFacingToggle(
                    facing = facing,
                    onToggle = onToggleFacing,
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

/**
 * Watches live pace while a session is active and plays the audio reminder when
 * the pace falls below the configured minimum. Renders nothing.
 *
 * Timing (see [AudioPokeDecision]): the first reminder waits a full minute of
 * watching, the gap re-arms after every detected bite, and no more than one
 * reminder plays per minute.
 */
@Composable
private fun AudioPokeEffect(graph: AppGraph, watching: WatchState.Watching?, now: Long) {
    if (watching == null) return
    val audioPoke = graph.audioPokeSettings
    val enabled by audioPoke.enabled.collectAsState()
    val minPace by audioPoke.minPaceBitesPerMin.collectAsState()
    val hasRecording by audioPoke.hasRecording.collectAsState()
    val stats = liveStats(graph, watching, now)

    LaunchedEffect(now) {
        val shouldPlay = AudioPokeDecision.shouldPlay(
            enabled = enabled,
            hasRecording = hasRecording,
            paceBitesPerMin = stats.bitesPerMinute,
            minPaceBitesPerMin = minPace,
            sessionStartEpochMs = watching.startedAtEpochMs,
            lastBiteAtEpochMs = watching.lastEatingAtEpochMs,
            // Held in AudioPokeSettings (not remember) so the cooldown persists
            // when the user leaves Home and returns mid-session.
            lastPlayedAtEpochMs = audioPoke.lastPokedAt(watching.sessionId),
            nowEpochMs = now,
        )
        if (shouldPlay) {
            audioPoke.markPoked(watching.sessionId, now)
            audioPoke.playRecording()
        }
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
    poking: Boolean = false,
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
    // While the reminder plays, the hero takes over: swap in an urgent label and
    // gently pulse the whole box so the nudge is felt as well as heard.
    val nudging = poking && watchState is WatchState.Watching
    val heroVerdict = if (nudging) "Please eat!" else verdict
    // Brighter fill so the nudge pops off the calmer verdict hues.
    val heroColor = if (nudging) NeoColors.Yellow else color
    val flash = remember { Animatable(1f) }
    LaunchedEffect(nudging) {
        if (nudging) {
            flash.animateTo(
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 380, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        } else {
            flash.snapTo(1f)
        }
    }
    val verticalPad = if (compact) 4.dp else 28.dp
    NeoBox(
        modifier = modifier.fillMaxWidth().alpha(flash.value),
        backgroundColor = heroColor,
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
                heroVerdict,
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

/** The four Home stats, in a fixed order shared by the 2×2 grid and the column. */
private data class StatSpec(
    val label: String,
    val value: String,
    val caption: String,
    val headerColor: Color,
    val bodyColor: Color,
    val headerTextColor: Color = NeoColors.Ink,
)

private fun statSpecs(watching: WatchState.Watching, stats: SessionStats): List<StatSpec> {
    val paceValue = if (stats.eatingSeconds == 0) "—" else stats.bitesPerMinute.roundedTo1().toString()
    val gapValue = if (stats.averageGapSeconds <= 0) "—" else "${stats.averageGapSeconds}s"
    return listOf(
        StatSpec("Pace", paceValue, "bites / min", NeoColors.OrangeHead, NeoColors.OrangeBody),
        StatSpec(
            "Avg gap", gapValue, "between bites",
            NeoColors.MagentaHead, NeoColors.MagentaBody, headerTextColor = NeoColors.Cream,
        ),
        StatSpec(
            "Elapsed", mmss(stats.durationSeconds), "since ${formatTimeHm(watching.startedAtEpochMs)}",
            NeoColors.CyanHead, NeoColors.CyanBody,
        ),
        StatSpec("Eating", mmss(stats.eatingSeconds.toLong()), "this session", NeoColors.LimeHead, NeoColors.LimeBody),
    )
}

@Composable
private fun SpecCard(
    spec: StatSpec,
    compact: Boolean,
    expandBody: Boolean,
    modifier: Modifier,
    bodyVerticalPadding: Dp = if (compact) 4.dp else 8.dp,
) {
    NeoStatCard(
        label = spec.label,
        value = spec.value,
        caption = spec.caption,
        headerColor = spec.headerColor,
        bodyColor = spec.bodyColor,
        headerTextColor = spec.headerTextColor,
        expandBody = expandBody,
        compact = compact,
        bodyVerticalPadding = bodyVerticalPadding,
        modifier = modifier,
    )
}

@Composable
private fun StatGrid(
    watching: WatchState.Watching,
    stats: SessionStats,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val specs = statSpecs(watching, stats)
    val gap = if (compact) 4.dp else 12.dp
    // In a RowScope: split evenly, and fill the row height when compact so the
    // landscape grid's cards stretch to their weighted rows.
    fun RowScope.cardModifier() =
        Modifier.weight(1f).then(if (compact) Modifier.fillMaxHeight() else Modifier)
    Column(
        verticalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.fillMaxWidth(),
    ) {
        StatRow(compact = compact, gap = gap) {
            SpecCard(specs[0], compact, expandBody = compact, modifier = cardModifier())
            SpecCard(specs[1], compact, expandBody = compact, modifier = cardModifier())
        }
        StatRow(compact = compact, gap = gap) {
            SpecCard(specs[2], compact, expandBody = compact, modifier = cardModifier())
            SpecCard(specs[3], compact, expandBody = compact, modifier = cardModifier())
        }
    }
}

/**
 * Portrait col 2: stats stacked full width, each at its natural height so all of
 * its text stays visible. Drops the "Eating" box (the last spec) — the three
 * shown are Pace, Avg gap, and Elapsed.
 */
@Composable
private fun StatColumn(
    watching: WatchState.Watching,
    stats: SessionStats,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        statSpecs(watching, stats).dropLast(1).forEach { spec ->
            SpecCard(
                spec = spec,
                compact = false,
                expandBody = false,
                // Slightly tighter than the default 8.dp so the three boxes come
                // closer to col 1's (preview + toggle) height.
                bodyVerticalPadding = 5.dp,
                modifier = Modifier.fillMaxWidth(),
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

/**
 * Portrait body: two columns under the verdict. Col 1 is the live preview (3:4)
 * with the facing toggle beneath it; col 2 is a stack of stat boxes, shown only
 * while watching. Idle there are no stats, so col 1 drops to half width and
 * centers across the row instead of hugging the left edge.
 *
 * Col 2 sizes to its own content (each box tall enough to show all its text) —
 * it is not matched to col 1's height.
 */
@Composable
private fun CameraStatsSection(
    active: Boolean,
    isEating: Boolean,
    watching: WatchState.Watching?,
    stats: SessionStats?,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        // Idle: col 2 is gone, so center the lone col 1 across the row.
        horizontalArrangement = if (watching != null) {
            Arrangement.spacedBy(12.dp)
        } else {
            Arrangement.Center
        },
    ) {
        // Col 1 — preview above the facing toggle, always half width so it
        // doesn't jump size when a session starts.
        Column(
            modifier = if (watching != null) Modifier.weight(1f) else Modifier.fillMaxWidth(0.5f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // aspectRatio derives the preview height from its (half-screen) width.
            DetectionCameraFrame(
                active = active,
                isEating = isEating,
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
            )
            CameraFacingToggle(
                facing = facing,
                onToggle = onToggleFacing,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Col 2 — stacked stats at their natural height, only while watching.
        if (watching != null && stats != null) {
            StatColumn(
                watching = watching,
                stats = stats,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Single neo toggle that flips the camera between front and back, replacing the
 * old pair of Front / Back buttons. Built on NeoButton so it matches the
 * Start/Stop button's height when the two are stacked in landscape.
 */
@Composable
private fun CameraFacingToggle(
    facing: CameraFacing,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeoButton(
        text = if (facing == CameraFacing.FRONT) "Front Camera  ⇄" else "Back Camera  ⇄",
        backgroundColor = NeoColors.CyanBody,
        onClick = onToggle,
        modifier = modifier,
    )
}

private fun mmss(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
