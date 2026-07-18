package com.eatplease.app.detection

/**
 * Turns per-frame class probabilities into a debounced eating signal and
 * per-second eating events.
 *
 * - The eating score of a frame is the summed probability of all eating classes.
 * - Scores are exponentially smoothed across frames to ride out single-frame noise.
 * - Enter/exit hysteresis prevents flapping at the threshold boundary.
 * - While eating, exactly one [EatingSecondSample] is emitted per wall-clock second.
 *
 * Thresholds are deliberately plain constants: confidences are persisted with
 * every event precisely so they can be re-calibrated from real meal logs.
 */
class EatingScoreAggregator(
    private val eatingClassIndices: IntArray = EatingClasses.indices,
    private val smoothingAlpha: Float = DEFAULT_SMOOTHING_ALPHA,
    private val enterThreshold: Float = DEFAULT_ENTER_THRESHOLD,
    private val exitThreshold: Float = DEFAULT_EXIT_THRESHOLD,
) {
    init {
        require(exitThreshold <= enterThreshold) { "exitThreshold must not exceed enterThreshold" }
    }

    data class EatingSecondSample(val atEpochSecond: Long, val confidence: Float)

    data class FrameResult(
        val smoothedScore: Float,
        val isEating: Boolean,
        val newSamples: List<EatingSecondSample>,
    )

    private var smoothed = 0f
    private var eating = false
    private var lastEmittedSecond = Long.MIN_VALUE
    private var hasSeenFrame = false

    fun onFrame(probabilities: FloatArray, atEpochMillis: Long): FrameResult {
        val raw = eatingClassIndices.fold(0f) { acc, i -> acc + probabilities[i] }
        smoothed = if (hasSeenFrame) smoothingAlpha * raw + (1 - smoothingAlpha) * smoothed else raw
        hasSeenFrame = true

        eating = when {
            !eating && smoothed >= enterThreshold -> true
            eating && smoothed <= exitThreshold -> false
            else -> eating
        }

        val samples = if (eating) {
            val second = atEpochMillis / 1000
            if (second > lastEmittedSecond) {
                lastEmittedSecond = second
                listOf(EatingSecondSample(second, smoothed))
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        return FrameResult(smoothedScore = smoothed, isEating = eating, newSamples = samples)
    }

    fun reset() {
        smoothed = 0f
        eating = false
        lastEmittedSecond = Long.MIN_VALUE
        hasSeenFrame = false
    }

    companion object {
        const val DEFAULT_SMOOTHING_ALPHA = 0.4f
        const val DEFAULT_ENTER_THRESHOLD = 0.35f
        const val DEFAULT_EXIT_THRESHOLD = 0.20f
    }
}
