package com.eatplease.app.detection

import kotlin.math.exp

/** Numerically stable softmax; turns the model's logits into probabilities. */
fun softmax(logits: FloatArray): FloatArray {
    if (logits.isEmpty()) return logits
    val max = logits.max()
    val exps = FloatArray(logits.size)
    var sum = 0f
    for (i in logits.indices) {
        exps[i] = exp(logits[i] - max)
        sum += exps[i]
    }
    if (sum == 0f) return FloatArray(logits.size) { 1f / logits.size }
    for (i in exps.indices) exps[i] /= sum
    return exps
}
