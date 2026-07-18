package com.eatplease.app.detection

/** The Kinetics-600 classes we count as "eating". */
object EatingClasses {

    val labels: List<String> =
        KineticsLabels.all.filter { it.startsWith("eating") || it == "tasting food" }

    val indices: IntArray =
        KineticsLabels.all
            .withIndex()
            .filter { (_, label) -> label.startsWith("eating") || label == "tasting food" }
            .map { it.index }
            .toIntArray()
}
