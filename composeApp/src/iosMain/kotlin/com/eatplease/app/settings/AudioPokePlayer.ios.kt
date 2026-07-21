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
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
actual class AudioPokePlayer actual constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var player: AVAudioPlayer? = null
    private var stopJob: Job? = null

    actual fun play(path: String?) {
        if (path == null) return
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return

        stopInternal()

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)

        val url = NSURL.fileURLWithPath(path)
        val avPlayer = AVAudioPlayer(contentsOfURL = url, error = null)
        if (avPlayer.prepareToPlay() && avPlayer.play()) {
            player = avPlayer
            _isPlaying.value = true
            // AVAudioPlayer has no cheap Kotlin completion callback; schedule a
            // stop just past the clip's own duration to reset the playing flag.
            val durationMs = (avPlayer.duration * 1000).toLong().coerceAtLeast(0)
            stopJob = scope.launch {
                delay(durationMs)
                stopInternal()
            }
        } else {
            _isPlaying.value = false
        }
    }

    actual fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        stopJob?.cancel()
        stopJob = null
        val current = player
        player = null
        current?.stop()
        _isPlaying.value = false
    }
}
