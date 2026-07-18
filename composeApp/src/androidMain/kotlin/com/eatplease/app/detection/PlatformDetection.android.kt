package com.eatplease.app.detection

import android.content.Intent
import androidx.core.content.ContextCompat
import com.eatplease.app.EatPleaseApplication
import com.eatplease.app.settings.CameraSettings
import kotlinx.coroutines.CoroutineScope

private class AndroidWatchController(
    private val manager: WatchSessionManager,
) : WatchController {

    override suspend fun startWatching() {
        manager.start()
        val context = EatPleaseApplication.instance
        val intent = Intent(context, WatchForegroundService::class.java)
            .setAction(WatchForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    override suspend fun stopWatching() {
        val context = EatPleaseApplication.instance
        context.startService(
            Intent(context, WatchForegroundService::class.java)
                .setAction(WatchForegroundService.ACTION_STOP),
        )
        manager.stop()
    }
}

actual fun createPlatformWatchController(
    manager: WatchSessionManager,
    scope: CoroutineScope,
    cameraSettings: CameraSettings,
): WatchController = AndroidWatchController(manager)
