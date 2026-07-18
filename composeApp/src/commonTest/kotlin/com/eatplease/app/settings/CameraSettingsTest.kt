package com.eatplease.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraSettingsTest {

    @Test
    fun defaultsToFrontAndTogglesBackAndForth() {
        val settings = CameraSettings()
        assertEquals(CameraFacing.FRONT, settings.facing.value)

        settings.toggle()
        assertEquals(CameraFacing.BACK, settings.facing.value)

        settings.toggle()
        assertEquals(CameraFacing.FRONT, settings.facing.value)
    }
}
