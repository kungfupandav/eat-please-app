package com.eatplease.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import com.eatplease.app.detection.IosCameraPreviewBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.CoreGraphics.CGRectZero
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformCameraPreview(modifier: Modifier) {
    val session by IosCameraPreviewBridge.session.collectAsState()
    val previewLayer = remember {
        AVCaptureVideoPreviewLayer().also {
            it.videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }
    previewLayer.session = session

    UIKitView(
        factory = {
            object : UIView(frame = CGRectZero.readValue()) {
                init {
                    layer.addSublayer(previewLayer)
                }

                override fun layoutSubviews() {
                    super.layoutSubviews()
                    CATransaction.begin()
                    CATransaction.setValue(true, kCATransactionDisableActions)
                    previewLayer.setFrame(bounds)
                    CATransaction.commit()
                }
            }
        },
        modifier = modifier,
        update = {
            previewLayer.session = session
        },
    )
}
