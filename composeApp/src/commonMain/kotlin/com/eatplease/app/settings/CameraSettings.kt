package com.eatplease.app.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CameraFacing { FRONT, BACK }

/**
 * Which camera the watch session uses. Front by default (prop the phone facing
 * the kid); the platform camera pipelines observe this.
 */
class CameraSettings {

    private val _facing = MutableStateFlow(CameraFacing.FRONT)
    val facing: StateFlow<CameraFacing> = _facing.asStateFlow()

    fun toggle() {
        _facing.update { if (it == CameraFacing.FRONT) CameraFacing.BACK else CameraFacing.FRONT }
    }
}
