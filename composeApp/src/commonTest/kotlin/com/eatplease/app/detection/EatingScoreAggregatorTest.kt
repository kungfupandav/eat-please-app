package com.eatplease.app.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EatingScoreAggregatorTest {

    private val eatingIndex = EatingClasses.indices.first()

    private fun probs(eating: Float): FloatArray =
        FloatArray(FrameClassifier.NUM_CLASSES).also {
            it[eatingIndex] = eating
            it[0] = (1f - eating).coerceAtLeast(0f)
        }

    private fun aggregator() = EatingScoreAggregator(
        smoothingAlpha = 1f, // no smoothing: raw scores drive the state machine directly
        enterThreshold = 0.35f,
        exitThreshold = 0.20f,
    )

    @Test
    fun highScoreEntersEatingAndEmitsTheCurrentSecond() {
        val result = aggregator().onFrame(probs(0.8f), atEpochMillis = 5_500)

        assertTrue(result.isEating)
        assertEquals(listOf(5L), result.newSamples.map { it.atEpochSecond })
    }

    @Test
    fun sameSecondEmitsOnlyOnce() {
        val agg = aggregator()

        val first = agg.onFrame(probs(0.8f), 5_100)
        val second = agg.onFrame(probs(0.8f), 5_900)
        val third = agg.onFrame(probs(0.8f), 6_050)

        assertEquals(1, first.newSamples.size)
        assertEquals(0, second.newSamples.size)
        assertEquals(listOf(6L), third.newSamples.map { it.atEpochSecond })
    }

    @Test
    fun hysteresisHoldsStateBetweenThresholds() {
        val agg = aggregator()
        agg.onFrame(probs(0.8f), 1_000) // enter eating

        val mid = agg.onFrame(probs(0.3f), 2_000) // between exit(0.2) and enter(0.35)
        assertTrue(mid.isEating, "score between thresholds must keep the eating state")

        val low = agg.onFrame(probs(0.1f), 3_000)
        assertFalse(low.isEating)
        assertTrue(low.newSamples.isEmpty())

        val midAgain = agg.onFrame(probs(0.3f), 4_000)
        assertFalse(midAgain.isEating, "score between thresholds must keep the not-eating state")
    }

    @Test
    fun smoothingDelaysEntry() {
        val agg = EatingScoreAggregator(smoothingAlpha = 0.4f, enterThreshold = 0.35f, exitThreshold = 0.20f)

        agg.onFrame(probs(0f), 1_000) // seed the EMA at 0
        val second = agg.onFrame(probs(0.5f), 2_000) // EMA = 0.4 * 0.5 = 0.2 < enter
        assertFalse(second.isEating)

        val third = agg.onFrame(probs(0.5f), 3_000) // EMA = 0.32 < enter
        assertFalse(third.isEating)

        val fourth = agg.onFrame(probs(0.5f), 4_000) // EMA = 0.392 >= enter
        assertTrue(fourth.isEating)
    }

    @Test
    fun multipleEatingClassesSumIntoTheScore() {
        val probabilities = FloatArray(FrameClassifier.NUM_CLASSES)
        for (i in EatingClasses.indices) probabilities[i] = 0.05f // sums to 0.5

        val result = aggregator().onFrame(probabilities, 1_000)

        assertTrue(result.isEating)
        assertEquals(0.5f, result.smoothedScore, absoluteTolerance = 1e-4f)
    }

    @Test
    fun resetClearsStateAndAllowsReEmission() {
        val agg = aggregator()
        agg.onFrame(probs(0.8f), 5_000)

        agg.reset()
        val result = agg.onFrame(probs(0.8f), 5_000)

        assertTrue(result.isEating)
        assertEquals(1, result.newSamples.size, "the same second emits again after reset")
    }
}
