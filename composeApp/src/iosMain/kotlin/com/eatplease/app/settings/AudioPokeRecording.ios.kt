package com.eatplease.app.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlin.coroutines.resume

private const val RECORDING_FILE_NAME = "audio_poke_reminder.m4a"

@OptIn(ExperimentalForeignApi::class)
actual class AudioPokeRecording actual constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _isRecording = MutableStateFlow(false)
    actual val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _hasRecording = MutableStateFlow(fileExists())
    actual val hasRecording: StateFlow<Boolean> = _hasRecording.asStateFlow()

    private var recorder: AVAudioRecorder? = null
    private var autoStopJob: Job? = null

    actual fun recordingPath(): String? {
        val path = recordingFileUrl()?.path ?: return null
        return path.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
    }

    actual suspend fun startRecording(): Boolean {
        if (_isRecording.value) return true
        if (!ensureMicPermission()) return false

        stopRecordingInternal()

        val url = recordingFileUrl() ?: return false
        url.path?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
        refreshHasRecording()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        session.setActive(true, error = null)

        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to 44_100.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderAudioQualityKey to AVAudioQualityHigh.toInt(),
        )

        val audioRecorder = AVAudioRecorder(url, settings, null)

        if (!audioRecorder.prepareToRecord()) {
            return false
        }
        if (!audioRecorder.record()) {
            return false
        }

        recorder = audioRecorder
        _isRecording.value = true
        autoStopJob = scope.launch {
            delay(AUDIO_POKE_MAX_DURATION_MS)
            stopRecording()
        }
        return true
    }

    actual fun stopRecording() {
        stopRecordingInternal()
    }

    actual fun eraseRecording() {
        stopRecordingInternal()
        recordingFileUrl()?.path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
        refreshHasRecording()
    }

    private fun stopRecordingInternal() {
        autoStopJob?.cancel()
        autoStopJob = null
        val current = recorder
        recorder = null
        current?.stop()
        _isRecording.value = false
        refreshHasRecording()
    }

    private fun fileExists(): Boolean {
        val path = recordingFileUrl()?.path ?: return false
        return NSFileManager.defaultManager.fileExistsAtPath(path)
    }

    private fun refreshHasRecording() {
        _hasRecording.value = fileExists()
    }

    private fun recordingFileUrl(): NSURL? {
        val documents = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: return null
        return documents.URLByAppendingPathComponent(RECORDING_FILE_NAME)
    }

    @Suppress("DEPRECATION")
    private suspend fun ensureMicPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val session = AVAudioSession.sharedInstance()
            when (session.recordPermission) {
                AVAudioSessionRecordPermissionGranted -> continuation.resume(true)
                AVAudioSessionRecordPermissionDenied -> continuation.resume(false)
                AVAudioSessionRecordPermissionUndetermined ->
                    session.requestRecordPermission { granted ->
                        continuation.resume(granted)
                    }
                else -> continuation.resume(false)
            }
        }
}
