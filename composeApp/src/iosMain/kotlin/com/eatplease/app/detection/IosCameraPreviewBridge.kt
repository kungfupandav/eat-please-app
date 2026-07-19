package com.eatplease.app.detection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVCaptureSession

/**
 * Publishes the active [AVCaptureSession] from [IosCameraSource] so the Home
 * preview layer can attach while a watch session is running.
 */
object IosCameraPreviewBridge {
    private val _session = MutableStateFlow<AVCaptureSession?>(null)
    val session: StateFlow<AVCaptureSession?> = _session.asStateFlow()

    fun setSession(session: AVCaptureSession?) {
        _session.value = session
    }
}
