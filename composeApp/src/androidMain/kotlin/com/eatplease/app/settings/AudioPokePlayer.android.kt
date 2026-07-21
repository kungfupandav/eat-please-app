package com.eatplease.app.settings

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

actual class AudioPokePlayer actual constructor() {

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var player: MediaPlayer? = null

    actual fun play(path: String?) {
        if (path == null) return
        if (!File(path).let { it.exists() && it.length() > 0 }) return

        stopInternal()

        val mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnCompletionListener { stopInternal() }
            setOnErrorListener { _, _, _ ->
                stopInternal()
                true
            }
        }

        try {
            mediaPlayer.setDataSource(path)
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            _isPlaying.value = true
        } catch (_: Exception) {
            runCatching { mediaPlayer.release() }
            player = null
            _isPlaying.value = false
        }
    }

    actual fun stop() {
        stopInternal()
    }

    private fun stopInternal() {
        val current = player ?: run {
            _isPlaying.value = false
            return
        }
        player = null
        runCatching { if (current.isPlaying) current.stop() }
        runCatching { current.release() }
        _isPlaying.value = false
    }
}
