package com.eatplease.app.settings

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.eatplease.app.EatPleaseApplication
import com.eatplease.app.platform.AndroidPermissionRequester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val RECORDING_FILE_NAME = "audio_poke_reminder.m4a"

actual class AudioPokeRecording actual constructor() {

    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _hasRecording = MutableStateFlow(recordingFile().let { it.exists() && it.length() > 0 })
    actual val hasRecording: StateFlow<Boolean> = _hasRecording.asStateFlow()

    private var recorder: MediaRecorder? = null

    private fun recordingFile(): File =
        File(EatPleaseApplication.instance.filesDir, RECORDING_FILE_NAME)

    actual fun recordingPath(): String? =
        recordingFile().takeIf { it.exists() && it.length() > 0 }?.absolutePath

    actual suspend fun startRecording(): Boolean {
        if (_isRecording.value) return true
        if (!ensureMicPermission()) return false

        stopRecordingInternal()

        val file = recordingFile()
        if (file.exists()) file.delete()
        refreshHasRecording()

        val mediaRecorder = createRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            setMaxDuration(AUDIO_POKE_MAX_DURATION_MS.toInt())
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopRecording()
                }
            }
        }

        return try {
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            _isRecording.value = true
            true
        } catch (_: Exception) {
            runCatching { mediaRecorder.release() }
            recorder = null
            _isRecording.value = false
            file.delete()
            refreshHasRecording()
            false
        }
    }

    actual fun stopRecording() {
        stopRecordingInternal()
    }

    actual fun eraseRecording() {
        stopRecordingInternal()
        recordingFile().delete()
        refreshHasRecording()
    }

    private fun stopRecordingInternal() {
        val current = recorder ?: run {
            _isRecording.value = false
            refreshHasRecording()
            return
        }
        recorder = null
        try {
            current.stop()
        } catch (_: Exception) {
            // stop() can throw if start() never completed; still release.
        } finally {
            runCatching { current.release() }
            _isRecording.value = false
            refreshHasRecording()
        }
    }

    private fun refreshHasRecording() {
        _hasRecording.value = recordingFile().let { it.exists() && it.length() > 0 }
    }

    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(EatPleaseApplication.instance)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private suspend fun ensureMicPermission(): Boolean {
        val context = EatPleaseApplication.instance
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return AndroidPermissionRequester.request(Manifest.permission.RECORD_AUDIO)
    }
}
