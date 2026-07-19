package com.eatplease.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.eatplease.app.detection.CameraPreviewBridge

@Composable
actual fun PlatformCameraPreview(modifier: Modifier) {
    DisposableEffect(Unit) {
        onDispose { CameraPreviewBridge.setSurfaceProvider(null) }
    }
    AndroidView(
        factory = { context ->
            PreviewView(context).also { view ->
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
                view.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                CameraPreviewBridge.setSurfaceProvider(view.surfaceProvider)
            }
        },
        update = { view ->
            CameraPreviewBridge.setSurfaceProvider(view.surfaceProvider)
        },
        modifier = modifier,
    )
}
