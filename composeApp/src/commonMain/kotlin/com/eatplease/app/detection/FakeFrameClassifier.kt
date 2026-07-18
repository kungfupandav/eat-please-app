package com.eatplease.app.detection

/**
 * Deterministic stand-in for the MoViNet model: alternates between "eating"
 * and "not eating" phases every [phaseLengthFrames] calls. Used by tests and
 * by the UI while the real platform classifiers land.
 */
class FakeFrameClassifier(
    private val phaseLengthFrames: Int = 30,
    private val eatingConfidence: Float = 0.8f,
) : FrameClassifier {

    private var frameCount = 0

    override suspend fun classify(rgbFrame: ByteArray): FloatArray {
        val probabilities = FloatArray(FrameClassifier.NUM_CLASSES)
        val inEatingPhase = (frameCount / phaseLengthFrames) % 2 == 0
        frameCount++
        if (inEatingPhase) {
            probabilities[EatingClasses.indices.first()] = eatingConfidence
            probabilities[0] = 1f - eatingConfidence
        } else {
            probabilities[0] = 0.9f
        }
        return probabilities
    }

    override fun reset() {
        frameCount = 0
    }
}
