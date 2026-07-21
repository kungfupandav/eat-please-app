package com.eatplease.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.eatplease.app.detection.IosCameraPreviewBridge
import com.eatplease.app.detection.isIosSimulator
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.UIKit.UIViewMeta
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * A UIView whose backing layer *is* the AVCaptureVideoPreviewLayer, so UIKit
 * resizes it automatically with the view — no sublayer, no manual layout work.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView : UIView(frame = CGRectZero.readValue()) {
    val previewLayer: AVCaptureVideoPreviewLayer
        get() = layer as AVCaptureVideoPreviewLayer

    companion object : UIViewMeta() {
        override fun layerClass() = AVCaptureVideoPreviewLayer
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformCameraPreview(modifier: Modifier) {
    // The Simulator has no camera; the watch pipeline runs on a scripted fake
    // feed, so show a matching placeholder instead of an empty capture layer.
    if (isIosSimulator) {
        SimulatorPreview(modifier)
        return
    }

    val session by IosCameraPreviewBridge.session.collectAsState()
    UIKitView(
        factory = {
            CameraPreviewView().apply {
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                attachSession(this, session)
            }
        },
        modifier = modifier,
        update = { view -> attachSession(view, session) },
    )
}

@Composable
private fun SimulatorPreview(modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "sim-feed")
    val alpha by pulse.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "sim-feed-alpha",
    )
    Box(
        modifier = modifier.fillMaxSize().background(Color(0xFF1D9E75).copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Simulator\nsample feed",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/**
 * Associate the capture session with the preview layer, deferred to the next
 * main run-loop turn.
 *
 * `AVCaptureVideoPreviewLayer.setSession` spins a *nested run loop* to build the
 * capture graph. The factory/update lambdas run inside Compose's composition
 * `applyChanges`, so assigning it there lets that nested loop pump a display-link
 * tick and re-enter Compose's render/apply while a slot writer is already open,
 * which crashes the runtime. Deferring runs the association as its own run-loop
 * callout, clear of the apply/render cycle. The guard avoids redundant sets,
 * each of which would rebuild the graph.
 */
@OptIn(ExperimentalForeignApi::class)
private fun attachSession(view: CameraPreviewView, session: AVCaptureSession?) {
    dispatch_async(dispatch_get_main_queue()) {
        if (view.previewLayer.session != session) {
            view.previewLayer.session = session
        }
    }
}
