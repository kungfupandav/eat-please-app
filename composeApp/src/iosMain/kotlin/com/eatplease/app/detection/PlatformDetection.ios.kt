package com.eatplease.app.detection

import com.eatplease.app.settings.CameraSettings
import kotlinx.coroutines.CoroutineScope

// The AVFoundation + TensorFlowLiteC pipeline lands in the next PR;
// until then iOS keeps the synthetic pipeline.
actual fun createPlatformWatchController(
    manager: WatchSessionManager,
    scope: CoroutineScope,
    cameraSettings: CameraSettings,
): WatchController = FakeWatchController(manager, FakeFrameClassifier(), scope)
