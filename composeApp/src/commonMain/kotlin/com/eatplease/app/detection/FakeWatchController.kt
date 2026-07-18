package com.eatplease.app.detection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Camera-free watch pipeline: pumps synthetic frames through the classifier at
 * ~6 fps so the whole start → detect → log → stop flow works end-to-end before
 * the platform camera integrations land.
 */
class FakeWatchController(
    private val manager: WatchSessionManager,
    private val classifier: FrameClassifier,
    private val scope: CoroutineScope,
    private val frameIntervalMs: Long = 166,
) : WatchController {

    private var pump: Job? = null

    override suspend fun startWatching() {
        manager.start()
        classifier.reset()
        pump?.cancel()
        pump = scope.launch {
            val emptyFrame = ByteArray(0)
            while (isActive) {
                manager.onFrameClassified(classifier.classify(emptyFrame))
                delay(frameIntervalMs)
            }
        }
    }

    override suspend fun stopWatching() {
        pump?.cancel()
        pump = null
        manager.stop()
    }
}
