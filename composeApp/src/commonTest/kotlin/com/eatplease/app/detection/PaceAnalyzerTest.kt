package com.eatplease.app.detection

import kotlin.test.Test
import kotlin.test.assertEquals

class PaceAnalyzerTest {

    private val analyzer = PaceAnalyzer()

    @Test
    fun emptySessionIsInsufficientData() {
        val stats = analyzer.analyze(emptyList(), sessionStartEpochSecond = 0, sessionEndEpochSecond = 600)

        assertEquals(PaceVerdict.INSUFFICIENT_DATA, stats.paceVerdict)
        assertEquals(0, stats.eatingSeconds)
        assertEquals(600, stats.longestPauseSeconds)
    }

    @Test
    fun regularBurstsAreConstantPace() {
        // A 2-second bite every 30 seconds for 10 minutes.
        val seconds = (0 until 20).flatMap { bite -> listOf(bite * 30L, bite * 30L + 1) }

        val stats = analyzer.analyze(seconds, 0, 600)

        assertEquals(PaceVerdict.CONSTANT, stats.paceVerdict)
        assertEquals(40, stats.eatingSeconds)
        assertEquals(2.0, stats.bitesPerMinute, absoluteTolerance = 0.01)
    }

    @Test
    fun erraticBurstsAreIrregular() {
        // Bites at wildly varying intervals: 5s, 120s, 8s, 200s, 15s ...
        val starts = listOf(0L, 5, 125, 133, 333, 348, 350, 500)
        val seconds = starts.map { it }

        val stats = analyzer.analyze(seconds, 0, 600)

        assertEquals(PaceVerdict.IRREGULAR, stats.paceVerdict)
    }

    @Test
    fun fewBurstsAreInsufficientData() {
        val stats = analyzer.analyze(listOf(10L, 11, 40, 41), 0, 300)

        assertEquals(PaceVerdict.INSUFFICIENT_DATA, stats.paceVerdict)
    }

    @Test
    fun adjacentSecondsGroupIntoOneBurst() {
        // 6 seconds with gaps <= 2 form a single burst -> insufficient data.
        val stats = analyzer.analyze(listOf(10L, 11, 13, 15, 16, 17), 0, 300)

        assertEquals(PaceVerdict.INSUFFICIENT_DATA, stats.paceVerdict)
        assertEquals(6, stats.eatingSeconds)
    }

    @Test
    fun longestPauseSpansSessionEdgesAndGaps() {
        // Session 0..600; eating at 100-101 and 200-201: pauses are 100, 99, 399.
        val stats = analyzer.analyze(listOf(100L, 101, 200, 201), 0, 600)

        assertEquals(399, stats.longestPauseSeconds)
    }

    @Test
    fun duplicateSecondsAreIgnored() {
        val stats = analyzer.analyze(listOf(10L, 10, 10, 11), 0, 300)

        assertEquals(2, stats.eatingSeconds)
    }
}
