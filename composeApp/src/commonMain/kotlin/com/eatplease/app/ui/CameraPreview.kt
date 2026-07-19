package com.eatplease.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Live camera surface. Platform actuals attach to the active watch pipeline
 * (CameraX Preview on Android, AVCaptureVideoPreviewLayer on iOS). Only mount
 * while a watch session is running so preview starts/stops with Start/Stop.
 */
@Composable
expect fun PlatformCameraPreview(modifier: Modifier = Modifier)

/**
 * Middle-of-home camera frame: live preview while watching, plus a detection
 * rectangle that highlights when eating is currently scored.
 *
 * MoViNet exposes class scores only (no bounding boxes), so the rectangle is a
 * fixed detection-region visualization rather than a tracked object box.
 */
@Composable
fun DetectionCameraFrame(
    active: Boolean,
    isEating: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp)
            .aspectRatio(3f / 4f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            PlatformCameraPreview(modifier = Modifier.fillMaxSize())
            DetectionRectangle(isEating = isEating)
        } else {
            Text(
                "Preview starts when you press start",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.DetectionRectangle(isEating: Boolean) {
    val stroke = if (isEating) {
        Color(0xFF2E7D32)
    } else {
        Color.White.copy(alpha = 0.45f)
    }
    val fill = if (isEating) {
        Color(0xFF2E7D32).copy(alpha = 0.12f)
    } else {
        Color.Transparent
    }
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.72f)
            .fillMaxHeight(0.58f)
            .border(3.dp, stroke, RoundedCornerShape(12.dp))
            .background(fill, RoundedCornerShape(12.dp)),
    )
}
