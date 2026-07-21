package com.eatplease.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eatplease.app.data.EatingEvent
import com.eatplease.app.data.WatchSession
import com.eatplease.app.detection.PaceVerdict
import com.eatplease.app.detection.SessionStats
import com.eatplease.app.di.AppGraph
import com.eatplease.app.platform.currentEpochMillis

@Composable
fun LogScreen(graph: AppGraph, onOpenSession: (Long) -> Unit) {
    val sessions by graph.repository.sessions.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Header(title = "Log")

        if (sessions.isEmpty()) {
            Text(
                "No watch sessions yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, onClick = { onOpenSession(session.id) })
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

        LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
            items(events, key = { it.atEpochSecond }) { event ->
                EventRow(event)
                HorizontalDivider()
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) { Text("←") }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SessionRow(session: WatchSession, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sessionTitle(session), style = MaterialTheme.typography.titleSmall)
            val subtitle = session.endedAtEpochMs?.let {
                formatDuration((it - session.startedAtEpochMs) / 1000)
            } ?: "in progress"
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatsCard(stats: SessionStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${formatDuration(stats.durationSeconds)} · eating ${stats.eatingSeconds} s",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "${((stats.bitesPerMinute * 10).toInt() / 10.0)} bites/min avg",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "longest pause ${formatDuration(stats.longestPauseSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                when (stats.paceVerdict) {
                    PaceVerdict.CONSTANT -> "pace: ~constant ✓"
                    PaceVerdict.IRREGULAR -> "pace: irregular"
                    PaceVerdict.INSUFFICIENT_DATA -> "pace: not enough data yet"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EventRow(event: EatingEvent) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Text(formatClockTime(event.atEpochSecond * 1000), style = MaterialTheme.typography.bodyMedium)
        Text("eating", style = MaterialTheme.typography.bodyMedium)
        Text(
            ((event.confidence * 100).toInt() / 100.0).toString(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun sessionTitle(session: WatchSession): String {
    val start = "${formatDayLabel(session.startedAtEpochMs)}, ${formatTimeHm(session.startedAtEpochMs)}"
    val end = session.endedAtEpochMs?.let { " – ${formatTimeHm(it)}" } ?: ""
    return start + end
}
