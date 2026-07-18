package com.eatplease.app.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoftmaxTest {

    @Test
    fun probabilitiesSumToOne() {
        val probs = softmax(floatArrayOf(1f, 2f, 3f))
        assertEquals(1f, probs.sum(), absoluteTolerance = 1e-5f)
        assertTrue(probs[2] > probs[1] && probs[1] > probs[0])
    }

    @Test
    fun uniformLogitsGiveUniformProbabilities() {
        val probs = softmax(floatArrayOf(4f, 4f, 4f, 4f))
        for (p in probs) assertEquals(0.25f, p, absoluteTolerance = 1e-5f)
    }

    @Test
    fun largeLogitsDoNotOverflow() {
        val probs = softmax(floatArrayOf(1000f, 999f))
        assertEquals(1f, probs.sum(), absoluteTolerance = 1e-5f)
        assertTrue(probs[0] > probs[1])
    }

    @Test
    fun emptyInputIsReturnedAsIs() {
        assertEquals(0, softmax(floatArrayOf()).size)
    }
}
