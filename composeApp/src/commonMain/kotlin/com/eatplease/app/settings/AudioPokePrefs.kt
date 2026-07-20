package com.eatplease.app.settings

/** On-device persistence for audio-poke toggle and min-pace threshold. */
internal expect class AudioPokePrefs() {
    fun getEnabled(): Boolean
    fun setEnabled(value: Boolean)
    fun getMinPaceBitesPerMin(): Int
    fun setMinPaceBitesPerMin(value: Int)
}
