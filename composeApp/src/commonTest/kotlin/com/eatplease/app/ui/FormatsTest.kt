package com.eatplease.app.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatsTest {

    private val utc = TimeZone.UTC

    @Test
    fun clockTimeFormatsWithSeconds() {
        // 2026-07-18T12:05:31Z
        assertEquals("12:05:31", formatClockTime(1_784_376_331_000, utc))
    }

    @Test
    fun hmTimeDropsSeconds() {
        assertEquals("12:05", formatTimeHm(1_784_376_331_000, utc))
    }

    @Test
    fun dayLabelUsesShortMonth() {
        assertEquals("Jul 18", formatDayLabel(1_784_376_331_000, utc))
    }

    @Test
    fun durationsPickTheRightUnit() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("28 min", formatDuration(28 * 60 + 12))
        assertEquals("1 h 05 min", formatDuration(3_900))
        assertEquals("0 s", formatDuration(-5))
    }
}
