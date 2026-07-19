package com.eatplease.app.detection

/**
 * Per-frame video action classifier. Implementations wrap the MoViNet-A0-Stream
 * TFLite model on each platform and carry its recurrent state between calls;
 * tests and the UI-only build use a fake.
 */
interface FrameClassifier {

    /**
     * Classifies one RGB frame ([frameSize] x [frameSize], 3 bytes per pixel,
     * row-major) and returns 600 Kinetics-600 class probabilities.
     */
    suspend fun classify(rgbFrame: ByteArray): FloatArray

    /** Clears the streaming state; call between watch sessions. */
    fun reset()

    companion object {
        /** MoViNet-A0 input resolution. */
        const val FRAME_SIZE = 172
        const val NUM_CLASSES = 600
    }
}
