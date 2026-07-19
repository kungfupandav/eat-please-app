package com.eatplease.app.detection

import androidx.camera.core.Preview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the Compose [PreviewView] surface to [WatchForegroundService] so the
 * same CameraX bind can drive both ImageAnalysis and on-screen preview.
 */
object CameraPreviewBridge {
    private val _surfaceProvider = MutableStateFlow<Preview.SurfaceProvider?>(null)
    val surfaceProvider: StateFlow<Preview.SurfaceProvider?> = _surfaceProvider.asStateFlow()

    fun setSurfaceProvider(provider: Preview.SurfaceProvider?) {
        _surfaceProvider.value = provider
    }
}
