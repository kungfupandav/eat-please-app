package com.eatplease.app.detection

import com.eatplease.app.generated.resources.Res
import com.eatplease.app.settings.CameraSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIApplication
import kotlin.coroutines.resume

/** True in the iOS Simulator, which has no camera hardware to capture from. */
internal val isIosSimulator: Boolean by lazy {
    NSProcessInfo.processInfo.environment["SIMULATOR_DEVICE_NAME"] != null
}

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

        // Reading the multi-MB model and building the TFLite interpreter — and
        // configuring the capture session — are heavy and would freeze the UI
        // for seconds if run on the main thread (this suspend runs on the caller's
        // Main dispatcher). Do them on a background dispatcher instead.
        val source = withContext(Dispatchers.Default) {
            val classifier = this@IosWatchController.classifier
                ?: MoViNetFrameClassifier(loadModel()).also { this@IosWatchController.classifier = it }
            classifier.reset()

            IosCameraSource { frame ->
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
            }.also { it.start(cameraSettings.facing.value) }
        }
        this.source = source
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
): WatchController =
    if (isIosSimulator) {
        // No camera on the Simulator: drive the whole start → detect → log → stop
        // flow with a scripted classifier so the app is fully testable there.
        FakeWatchController(manager, FakeFrameClassifier(), scope)
    } else {
        IosWatchController(manager, scope, cameraSettings)
    }
