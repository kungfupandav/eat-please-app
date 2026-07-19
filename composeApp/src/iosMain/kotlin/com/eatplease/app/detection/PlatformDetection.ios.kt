package com.eatplease.app.detection

import com.eatplease.app.generated.resources.Res
import com.eatplease.app.settings.CameraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

/**
 * iOS watch pipeline: AVFoundation camera + MoViNet via TensorFlowLiteC.
 * Foreground-only by design (Apple stops background camera capture), so the
 * screen is kept awake for the duration of a session.
 */
private class IosWatchController(
    private val manager: WatchSessionManager,
    private val scope: CoroutineScope,
    private val cameraSettings: CameraSettings,
) : WatchController {

    private var classifier: MoViNetFrameClassifier? = null
    private var source: IosCameraSource? = null
    private var facingJob: Job? = null
    private val inference = Mutex()

    override suspend fun startWatching() {
        if (!ensureCameraPermission()) return

        manager.start()
        UIApplication.sharedApplication.idleTimerDisabled = true

        val classifier = this.classifier
            ?: MoViNetFrameClassifier(loadModel()).also { this.classifier = it }
        classifier.reset()

        val source = IosCameraSource { frame ->
            // Drop frames while an inference is in flight.
            if (inference.tryLock()) {
                scope.launch {
                    try {
                        manager.onFrameClassified(classifier.classify(frame))
                    } finally {
                        inference.unlock()
                    }
                }
            }
        }
        this.source = source
        source.start(cameraSettings.facing.value)
        facingJob = scope.launch {
            cameraSettings.facing.collect { this@IosWatchController.source?.setFacing(it) }
        }
    }

    override suspend fun stopWatching() {
        facingJob?.cancel()
        facingJob = null
        source?.stop()
        source = null
        UIApplication.sharedApplication.idleTimerDisabled = false
        manager.stop()
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadModel(): ByteArray = Res.readBytes(MODEL_RESOURCE_PATH)

    private suspend fun ensureCameraPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
                AVAuthorizationStatusAuthorized -> continuation.resume(true)
                AVAuthorizationStatusNotDetermined ->
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        continuation.resume(granted)
                    }

                else -> continuation.resume(false)
            }
        }

    private companion object {
        const val MODEL_RESOURCE_PATH = "files/movinet_a0_stream.tflite"
    }
}

actual fun createPlatformWatchController(
    manager: WatchSessionManager,
    scope: CoroutineScope,
    cameraSettings: CameraSettings,
): WatchController = IosWatchController(manager, scope, cameraSettings)
