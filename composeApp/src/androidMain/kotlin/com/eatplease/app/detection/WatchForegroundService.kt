package com.eatplease.app.detection

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.eatplease.app.di.Di
import com.eatplease.app.generated.resources.Res
import com.eatplease.app.settings.CameraFacing
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera foreground service: keeps capturing and classifying frames while the
 * app is in the background. Runs MoViNet at ~6 fps and feeds the shared
 * WatchSessionManager; a notification with a Stop action is always visible.
 */
class WatchForegroundService : LifecycleService() {

    private var classifier: MoViNetFrameClassifier? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val busy = AtomicBoolean(false)
    private var lastFrameAtMs = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                lifecycleScope.launch {
                    Di.graph.sessionManager.stop()
                    stopSelf()
                }
            }

            else -> startWatching()
        }
        return START_NOT_STICKY
    }

    private fun startWatching() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                Di.graph.sessionManager.stop()
                stopSelf()
            }
            return
        }

        startInForeground()
        lifecycleScope.launch {
            val model = loadModel()
            val loaded = MoViNetFrameClassifier(model)
            classifier = loaded
            bindCamera(loaded)
        }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun loadModel(): ByteArray = Res.readBytes(MODEL_RESOURCE_PATH)

    private suspend fun bindCamera(classifier: MoViNetFrameClassifier) {
        val provider = ProcessCameraProvider.getInstance(this).await()
        cameraProvider = provider
        Di.graph.cameraSettings.facing.collect { facing ->
            provider.unbindAll()
            classifier.reset()
            val selector = when (facing) {
                CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { image ->
                analyzeFrame(image, classifier)
            }
            provider.bindToLifecycle(this, selector, analysis)
        }
    }

    private fun analyzeFrame(image: ImageProxy, classifier: MoViNetFrameClassifier) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAtMs < FRAME_INTERVAL_MS || !busy.compareAndSet(false, true)) {
            image.close()
            return
        }
        lastFrameAtMs = now
        val rgb = image.use { it.toModelFrame() }
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                Di.graph.sessionManager.onFrameClassified(classifier.classify(rgb))
            } finally {
                busy.set(false)
            }
        }
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Watching", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, WatchForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Eat Please is watching")
            .setContentText("Eating detection is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(0, "Stop", stopIntent)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        classifier?.close()
        classifier = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.eatplease.app.action.START_WATCHING"
        const val ACTION_STOP = "com.eatplease.app.action.STOP_WATCHING"
        const val MODEL_RESOURCE_PATH = "files/movinet_a0_stream.tflite"
        private const val CHANNEL_ID = "watching"
        private const val NOTIFICATION_ID = 1
        private const val FRAME_INTERVAL_MS = 150L
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            Runnable::run,
        )
    }
