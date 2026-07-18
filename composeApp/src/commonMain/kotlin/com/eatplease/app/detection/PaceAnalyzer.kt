package com.eatplease.app.detection

import kotlin.math.roundToLong
import kotlin.math.sqrt

enum class PaceVerdict { CONSTANT, IRREGULAR, INSUFFICIENT_DATA }

data class SessionStats(
    val durationSeconds: Long,
    val eatingSeconds: Int,
    /** Eating bursts (grouped consecutive seconds) per minute of session. */
    val bitesPerMinute: Double,
    val longestPauseSeconds: Long,
    val paceVerdict: PaceVerdict,
)

/**
 * Derives pace statistics from the per-second eating log of one session.
 *
 * Detected seconds are grouped into "bursts" (runs of seconds at most
 * [burstJoinGapSeconds] apart — one burst ~ one bite/stretch of eating). The
 * pace is judged constant when there are enough bursts and the intervals
 * between their starts have a low coefficient of variation.
 */
class PaceAnalyzer(
    private val burstJoinGapSeconds: Long = 2,
    private val minBurstsForVerdict: Int = 5,
    private val constantPaceMaxCv: Double = 0.6,
) {

    fun analyze(
        eatingSeconds: List<Long>,
        sessionStartEpochSecond: Long,
        sessionEndEpochSecond: Long,
    ): SessionStats {
        val duration = (sessionEndEpochSecond - sessionStartEpochSecond).coerceAtLeast(0)
        val sorted = eatingSeconds.distinct().sorted()

        val bursts = groupIntoBursts(sorted)
        val bitesPerMinute =
            if (duration > 0) bursts.size / (duration / 60.0) else 0.0

        return SessionStats(
            durationSeconds = duration,
            eatingSeconds = sorted.size,
            bitesPerMinute = bitesPerMinute,
            longestPauseSeconds = longestPause(bursts, sessionStartEpochSecond, sessionEndEpochSecond),
            paceVerdict = verdict(bursts),
        )
    }

    /** Each burst is the range [first, last] of consecutive detected seconds. */
    private fun groupIntoBursts(sorted: List<Long>): List<LongRange> {
        if (sorted.isEmpty()) return emptyList()
        val bursts = mutableListOf<LongRange>()
        var start = sorted.first()
        var prev = sorted.first()
        for (second in sorted.drop(1)) {
            if (second - prev > burstJoinGapSeconds) {
                bursts += start..prev
                start = second
            }
            prev = second
        }
        bursts += start..prev
        return bursts
    }

    private fun longestPause(bursts: List<LongRange>, sessionStart: Long, sessionEnd: Long): Long {
        if (bursts.isEmpty()) return (sessionEnd - sessionStart).coerceAtLeast(0)
        val gaps = buildList {
            add(bursts.first().first - sessionStart)
            for (i in 1 until bursts.size) add(bursts[i].first - bursts[i - 1].last)
            add(sessionEnd - bursts.last().last)
        }
        return gaps.max().coerceAtLeast(0)
    }

    private fun verdict(bursts: List<LongRange>): PaceVerdict {
        if (bursts.size < minBurstsForVerdict) return PaceVerdict.INSUFFICIENT_DATA
        val intervals = (1 until bursts.size).map { (bursts[it].first - bursts[it - 1].first).toDouble() }
        val mean = intervals.average()
        if (mean <= 0) return PaceVerdict.IRREGULAR
        val variance = intervals.sumOf { (it - mean) * (it - mean) } / intervals.size
        val cv = sqrt(variance) / mean
        return if (cv <= constantPaceMaxCv) PaceVerdict.CONSTANT else PaceVerdict.IRREGULAR
    }
}

/** Convenience for formatting durations in the UI later. */
fun Double.roundedTo1(): Double = (this * 10).roundToLong() / 10.0
