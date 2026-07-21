package com.eatplease.app.ui

import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.eatplease.app.detection.CameraPreviewBridge

@Composable
actual fun PlatformCameraPreview(modifier: Modifier) {
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
        // Clear on release rather than an unconditional DisposableEffect: on
        // rotation the incoming layout's preview may bind its surface before this
        // outgoing one is released, and a plain clear would blank it (black frame
        // until a tab switch re-registered a surface). The compare-and-set only
        // detaches if this view is still the active surface.
        onRelease = { view ->
            CameraPreviewBridge.clearSurfaceProvider(view.surfaceProvider)
        },
        modifier = modifier,
    )
}
