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

    /**
     * Detaches [provider] only if it is still the active one. On rotation the
     * portrait/landscape layouts swap: the incoming preview can register its
     * surface before the outgoing one is disposed, so an unconditional clear
     * would blank the freshly-bound preview. Compare-and-set makes disposal
     * order-independent — a stale view can never null out a newer surface.
     */
    fun clearSurfaceProvider(provider: Preview.SurfaceProvider) {
        _surfaceProvider.compareAndSet(provider, null)
    }
}
