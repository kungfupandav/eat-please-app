@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.eatplease.app.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun Int.pad2(): String = toString().padStart(2, '0')

/** "12:05:31" in the given (default: device) time zone. */
fun formatClockTime(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return "${t.hour.pad2()}:${t.minute.pad2()}:${t.second.pad2()}"
}

/** "12:05" in the given (default: device) time zone. */
fun formatTimeHm(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return "${t.hour.pad2()}:${t.minute.pad2()}"
}

/** "Jul 18" in the given (default: device) time zone. */
fun formatDayLabel(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val t = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    val month = t.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    return "$month ${t.dayOfMonth}"
}

/** "45 s" / "28 min" / "1 h 05 min". */
fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "$hours h ${minutes.toInt().pad2()} min"
        minutes > 0 -> "$minutes min"
        else -> "$seconds s"
    }
}
