package com.eatplease.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eatplease.app.data.EatingEvent
import com.eatplease.app.data.WatchSession
import com.eatplease.app.detection.PaceVerdict
import com.eatplease.app.detection.SessionStats
import com.eatplease.app.detection.roundedTo1
import com.eatplease.app.di.AppGraph
import com.eatplease.app.platform.currentEpochMillis
import com.eatplease.app.ui.theme.NeoBox
import com.eatplease.app.ui.theme.NeoColors
import com.eatplease.app.ui.theme.NeoStatCard
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Body colors cycled across session rows for visual rhythm — purely decorative.
private val sessionRowColors = listOf(NeoColors.LimeBody, NeoColors.CyanBody, NeoColors.OrangeBody)

@Composable
fun LogScreen(graph: AppGraph, onOpenSession: (Long) -> Unit) {
    val sessions by graph.repository.sessions.collectAsState(initial = emptyList())
    val sections = remember(sessions) { sessionsByDate(sessions) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top-level Log tab: big Fredoka title, no back arrow (bottom bar owns nav).
        Text("Log", style = MaterialTheme.typography.headlineMedium, color = NeoColors.Ink)
        Spacer(Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            NeoBox(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = NeoColors.CreamDeep,
                contentPadding = PaddingValues(20.dp),
            ) {
                Text(
                    "No watch sessions yet — hit Home and start watching a meal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeoColors.Ink,
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sections.forEach { section ->
                    item(key = "date-${section.date}") {
                        Text(
                            section.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = NeoColors.Ink,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                    itemsIndexed(
                        section.sessions,
                        key = { _, session -> session.id },
                    ) { index, session ->
                        SessionRow(
                            session = session,
                            bodyColor = sessionRowColors[(section.startIndex + index) % sessionRowColors.size],
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SessionDetailScreen(graph: AppGraph, sessionId: Long, onBack: () -> Unit) {
    val sessions by graph.repository.sessions.collectAsState(initial = emptyList())
    val events by graph.repository.eventsForSession(sessionId).collectAsState(initial = emptyList())
    val session = sessions.firstOrNull { it.id == sessionId }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Header(title = session?.let { sessionTitle(it) } ?: "Session", onBack = onBack)

        if (session != null) {
            val stats = remember(session, events) { computeStats(graph, session, events) }
            StatsCard(stats)
        }

        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(events, key = { _, it -> it.atEpochSecond }) { index, event ->
                EventRow(event, index)
            }
        }
    }
}

private fun computeStats(graph: AppGraph, session: WatchSession, events: List<EatingEvent>): SessionStats =
    graph.paceAnalyzer.analyze(
        eatingSeconds = events.map { it.atEpochSecond },
        sessionStartEpochSecond = session.startedAtEpochMs / 1000,
        sessionEndEpochSecond = (session.endedAtEpochMs ?: currentEpochMillis()) / 1000,
    )

@Composable
internal fun Header(title: String, onBack: (() -> Unit)? = null) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    ) {
        // Pushed screens get a pressable neo back button; top-level titles omit it.
        if (onBack != null) {
            NeoBox(
                backgroundColor = NeoColors.Yellow,
                cornerRadius = 12.dp,
                shadowOffset = 3.dp,
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("←", style = MaterialTheme.typography.headlineSmall, color = NeoColors.Ink)
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = NeoColors.Ink)
    }
}

@Composable
private fun SessionRow(session: WatchSession, bodyColor: Color, onClick: () -> Unit) {
    NeoBox(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = bodyColor,
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sessionTitle(session), style = MaterialTheme.typography.titleMedium, color = NeoColors.Ink)
            // Small summary line: total duration, or a live marker if still running.
            val subtitle = session.endedAtEpochMs?.let {
                "${formatDuration((it - session.startedAtEpochMs) / 1000)} watched"
            } ?: "in progress…"
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NeoColors.Ink)
        }
    }
}

@Composable
private fun StatsCard(stats: SessionStats) {
    // Formatted values mirror the Home StatGrid conventions.
    val paceValue = if (stats.eatingSeconds == 0) "—" else stats.bitesPerMinute.roundedTo1().toString()
    val gapValue = if (stats.averageGapSeconds <= 0) "—" else "${stats.averageGapSeconds}s"

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        // 2x2 grid of color-block stat cards.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeoStatCard(
                "Pace", paceValue, "bites / min",
                NeoColors.OrangeHead, NeoColors.OrangeBody,
                modifier = Modifier.weight(1f),
            )
            NeoStatCard(
                "Avg gap", gapValue, "between bites",
                NeoColors.MagentaHead, NeoColors.MagentaBody,
                headerTextColor = NeoColors.Cream,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            NeoStatCard(
                "Duration", formatDuration(stats.durationSeconds), "start to end",
                NeoColors.CyanHead, NeoColors.CyanBody,
                modifier = Modifier.weight(1f),
            )
            NeoStatCard(
                "Eating", "${stats.eatingSeconds} s", "of active bites",
                NeoColors.LimeHead, NeoColors.LimeBody,
                modifier = Modifier.weight(1f),
            )
        }

        // Pace verdict as a colored full-width strip.
        val (verdictColor, verdictText) = when (stats.paceVerdict) {
            PaceVerdict.CONSTANT -> NeoColors.Green to "Pace: ~constant ✓"
            PaceVerdict.IRREGULAR -> NeoColors.Coral to "Pace: irregular"
            PaceVerdict.INSUFFICIENT_DATA -> NeoColors.Yellow to "Pace: not enough data yet"
        }
        NeoBox(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = verdictColor,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(verdictText, style = MaterialTheme.typography.titleSmall, color = NeoColors.Ink)
        }
    }
}

@Composable
private fun EventRow(event: EatingEvent, index: Int) {
    // Light striped, bordered rows — readable without a heavy per-row shadow.
    val stripe = if (index % 2 == 0) NeoColors.Cream else NeoColors.CreamDeep
    val shape = RoundedCornerShape(10.dp)
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(stripe, shape)
            .border(2.dp, NeoColors.Ink, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(formatClockTime(event.atEpochSecond * 1000), style = MaterialTheme.typography.bodyMedium, color = NeoColors.Ink)
        Text("eating", style = MaterialTheme.typography.bodyMedium, color = NeoColors.Ink)
        Text(
            ((event.confidence * 100).toInt() / 100.0).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = NeoColors.Ink,
            textAlign = TextAlign.End,
        )
    }
}

private fun sessionTitle(session: WatchSession): String {
    val start = "${formatDayLabel(session.startedAtEpochMs)}, ${formatTimeHm(session.startedAtEpochMs)}"
    val end = session.endedAtEpochMs?.let { " – ${formatTimeHm(it)}" } ?: ""
    return start + end
}

/** Groups newest-first sessions into date sections, preserving list order. */
@OptIn(kotlin.time.ExperimentalTime::class)
private fun sessionsByDate(
    sessions: List<WatchSession>,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<DateSection> {
    if (sessions.isEmpty()) return emptyList()
    val grouped = sessions.groupBy { session ->
        Instant.fromEpochMilliseconds(session.startedAtEpochMs).toLocalDateTime(timeZone).date
    }
    var startIndex = 0
    // groupBy keeps key order of first appearance; sessions are already newest-first.
    return grouped.map { (date, daySessions) ->
        DateSection(
            date = date,
            title = formatDayLabel(daySessions.first().startedAtEpochMs, timeZone),
            sessions = daySessions,
            startIndex = startIndex,
        ).also { startIndex += daySessions.size }
    }
}

private data class DateSection(
    val date: LocalDate,
    val title: String,
    val sessions: List<WatchSession>,
    val startIndex: Int,
)
