package de.mulambda.slantedwatchface

// Typeface and color settings for a watchface
data class Veneer(
    val angle: Float,
    val typefaces: Typefaces,
    val hoursColor: Int,
    val minutesColor: Int,
    val secondsColor: Int,
    val dateColor: Int,
    val isAmbient: Boolean
)
