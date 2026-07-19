package com.eatplease.app.detection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EatingClassesTest {

    @Test
    fun labelListHasExactly600Entries() {
        assertEquals(600, KineticsLabels.all.size)
    }

    @Test
    fun eatingClassesAreTheNineEatingLabelsPlusTastingFood() {
        assertEquals(10, EatingClasses.indices.size)
        assertTrue(EatingClasses.labels.contains("eating burger"))
        assertTrue(EatingClasses.labels.contains("tasting food"))
        assertTrue(EatingClasses.labels.none { it == "tasting beer" || it == "tasting wine" })
    }

    @Test
    fun indicesPointAtTheirLabels() {
        for ((offset, index) in EatingClasses.indices.withIndex()) {
            assertEquals(EatingClasses.labels[offset], KineticsLabels.all[index])
        }
    }
}
