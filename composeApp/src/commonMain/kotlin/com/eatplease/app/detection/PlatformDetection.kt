package com.eatplease.app.detection

import com.eatplease.app.settings.CameraSettings
import kotlinx.coroutines.CoroutineScope

/**
 * Platform watch pipeline factory. Android returns a controller backed by a
 * camera foreground service running MoViNet; iOS gets its camera pipeline in
 * the next PR and returns the fake until then.
 */
expect fun createPlatformWatchController(
    manager: WatchSessionManager,
    scope: CoroutineScope,
    cameraSettings: CameraSettings,
): WatchController
