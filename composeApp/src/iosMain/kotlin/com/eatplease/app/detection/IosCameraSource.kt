package com.eatplease.app.detection

import com.eatplease.app.settings.CameraFacing
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.plus
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession.Companion.discoverySessionWithDeviceTypes
import platform.AVFoundation.AVCaptureDeviceInput.Companion.deviceInputWithDevice
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.connectionWithMediaType
import platform.CoreFoundation.CFBridgingRelease
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelBufferPixelFormatTypeKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.QuartzCore.CACurrentMediaTime
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

/**
 * AVFoundation frame source: BGRA video frames at ~6 fps, center-cropped and
 * scaled to the classifier's 172x172 RGB input. Runs entirely in Kotlin/Native.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCameraSource(
    private val onFrame: (ByteArray) -> Unit,
) {

    private val session = AVCaptureSession()
    private val queue = dispatch_queue_create("com.eatplease.camera", null)
    private var lastFrameAt = 0.0

    private val delegate = object : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            val now = CACurrentMediaTime()
            if (now - lastFrameAt < FRAME_INTERVAL_SECONDS) return
            lastFrameAt = now
            val pixelBuffer = CMSampleBufferGetImageBuffer(didOutputSampleBuffer) ?: return
            val frame = pixelBuffer.toModelFrame() ?: return
            onFrame(frame)
        }
    }

    fun start(facing: CameraFacing) {
        configureInput(facing)
        configureOutputOnce()
        dispatch_async(queue) { session.startRunning() }
    }

    fun setFacing(facing: CameraFacing) {
        configureInput(facing)
    }

    fun stop() {
        dispatch_async(queue) { session.stopRunning() }
    }

    private fun configureInput(facing: CameraFacing) {
        val position = when (facing) {
            CameraFacing.FRONT -> AVCaptureDevicePositionFront
            CameraFacing.BACK -> AVCaptureDevicePositionBack
        }
        val device = discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        ).devices.firstOrNull() as? AVCaptureDevice ?: return

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPreset640x480
        session.inputs.forEach { session.removeInput(it as AVCaptureInput) }
        deviceInputWithDevice(device = device, error = null)?.let { input ->
            if (session.canAddInput(input)) session.addInput(input)
        }
        session.commitConfiguration()
    }

    private fun configureOutputOnce() {
        if (session.outputs.isNotEmpty()) return
        val output = AVCaptureVideoDataOutput()
        output.videoSettings = mapOf(
            CFBridgingRelease(kCVPixelBufferPixelFormatTypeKey) to kCVPixelFormatType_32BGRA,
        )
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(delegate, queue)
        session.beginConfiguration()
        if (session.canAddOutput(output)) session.addOutput(output)
        session.commitConfiguration()
        output.connectionWithMediaType(AVMediaTypeVideo)
            ?.videoOrientation = AVCaptureVideoOrientationPortrait
    }

    private companion object {
        const val FRAME_INTERVAL_SECONDS = 0.15
    }
}

/** BGRA pixel buffer -> center-cropped, nearest-neighbor-scaled 172x172 RGB. */
@OptIn(ExperimentalForeignApi::class)
internal fun CVPixelBufferRef.toModelFrame(): ByteArray? {
    CVPixelBufferLockBaseAddress(this, kCVPixelBufferLock_ReadOnly)
    try {
        val base = CVPixelBufferGetBaseAddress(this)?.reinterpret<UByteVar>() ?: return null
        val width = CVPixelBufferGetWidth(this).toInt()
        val height = CVPixelBufferGetHeight(this).toInt()
        val rowBytes = CVPixelBufferGetBytesPerRow(this).toInt()

        val square = minOf(width, height)
        val xOffset = (width - square) / 2
        val yOffset = (height - square) / 2
        val size = FrameClassifier.FRAME_SIZE

        val rgb = ByteArray(size * size * 3)
        var out = 0
        for (y in 0 until size) {
            val srcY = yOffset + y * square / size
            val row = base + srcY * rowBytes
            for (x in 0 until size) {
                val srcX = (xOffset + x * square / size) * 4
                // BGRA -> RGB
                rgb[out++] = row!![srcX + 2].toByte()
                rgb[out++] = row[srcX + 1].toByte()
                rgb[out++] = row[srcX].toByte()
            }
        }
        return rgb
    } finally {
        CVPixelBufferUnlockBaseAddress(this, kCVPixelBufferLock_ReadOnly)
    }
}
