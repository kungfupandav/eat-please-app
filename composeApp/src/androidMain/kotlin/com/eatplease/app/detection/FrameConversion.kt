package com.eatplease.app.detection

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import kotlin.math.min

/**
 * Converts an RGBA_8888 [ImageProxy] into the classifier's input: an upright,
 * center-cropped square scaled to 172x172, as packed RGB bytes.
 */
fun ImageProxy.toModelFrame(): ByteArray {
    val plane = planes[0]
    val pixelStride = plane.pixelStride
    val rowStride = plane.rowStride
    val paddedWidth = rowStride / pixelStride

    val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    plane.buffer.rewind()
    padded.copyPixelsFromBuffer(plane.buffer)

    val square = min(width, height)
    val cropX = (width - square) / 2
    val cropY = (height - square) / 2
    val rotation = imageInfo.rotationDegrees
    val matrix = Matrix().apply { if (rotation != 0) postRotate(rotation.toFloat()) }
    val cropped = Bitmap.createBitmap(padded, cropX, cropY, square, square, matrix, true)

    val scaled = Bitmap.createScaledBitmap(
        cropped,
        FrameClassifier.FRAME_SIZE,
        FrameClassifier.FRAME_SIZE,
        true,
    )

    val size = FrameClassifier.FRAME_SIZE
    val pixels = IntArray(size * size)
    scaled.getPixels(pixels, 0, size, 0, 0, size, size)

    val rgb = ByteArray(size * size * 3)
    var out = 0
    for (pixel in pixels) {
        rgb[out++] = ((pixel shr 16) and 0xFF).toByte()
        rgb[out++] = ((pixel shr 8) and 0xFF).toByte()
        rgb[out++] = (pixel and 0xFF).toByte()
    }
    return rgb
}
