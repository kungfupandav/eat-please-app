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
 * Landscape splits the width into two equal halves with no scrolling:
 *
 * - Left half: the hero over the 2×2 stats (weighted so the cards keep enough
 *   height for header + value + caption). Stats therefore stay within 50% of
 *   the screen width.
 * - Right half: the live video sized as tall as it fits above a small facing
 *   toggle and the Start/Stop button.
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
            // LEFT half — hero + stats, held to 50% of the width.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (watching != null) {
                    VerdictHero(
                        watchState = watchState,
                        now = now,
                        compact = true,
                        fill = true,
                        poking = poking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.32f),
                    )
                    StatGrid(
                        watching = watching,
                        stats = liveStats(graph, watching, now),
                        compact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.68f),
                    )
                } else {
                    VerdictHero(
                        watchState = watchState,
                        now = now,
                        compact = true,
                        fill = true,
                        poking = poking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            // RIGHT half — video as tall as it fits, then a small toggle + CTA.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // Height-driven here: fill the available height, width follows
                    // the 3:4 ratio, so the frame is as tall as it can be.
                    DetectionCameraFrame(
                        active = watching != null,
                        isEating = watching?.isEatingNow == true,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(3f / 4f, matchHeightConstraintsFirst = true),
                    )
                }
                CameraFacingToggle(
                    facing = facing,
                    onToggle = onToggleFacing,
                    compact = true,
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

/**
 * Portrait camera row: the live preview takes half the width (3:4 portrait
 * frame), with the CAMERA label and a single front/back toggle filling the
 * other half.
 */
@Composable
private fun CameraSection(
    active: Boolean,
    isEating: Boolean,
    facing: CameraFacing,
    onToggleFacing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // weight(1f) on both children splits the row 50/50, so the preview is
        // half the screen width; aspectRatio derives its height from that width.
        DetectionCameraFrame(
            active = active,
            isEating = isEating,
            modifier = Modifier.weight(1f).aspectRatio(3f / 4f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f),
        ) {
            Text("CAMERA", style = MaterialTheme.typography.labelMedium, color = NeoColors.Ink)
            CameraFacingToggle(facing = facing, onToggle = onToggleFacing)
        }
    }
}

/**
 * Single neo toggle that flips the camera between front and back, replacing the
 * old pair of Front / Back buttons. Shows the active facing with a swap glyph.
 */
@Composable
private fun CameraFacingToggle(
    facing: CameraFacing,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    NeoBox(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = NeoColors.CyanBody,
        cornerRadius = if (compact) 10.dp else 12.dp,
        shadowOffset = 3.dp,
        onClick = onToggle,
        contentPadding = PaddingValues(
            vertical = if (compact) 5.dp else 7.dp,
            horizontal = 10.dp,
        ),
    ) {
        Text(
            if (facing == CameraFacing.FRONT) "Front  ⇄" else "Back  ⇄",
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = NeoColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
        )
    }
}

private fun mmss(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
