package de.mulambda.slantedwatchface

import android.graphics.Typeface

// Typeface and color settings for a watchface
data class Veneer(
    val angle: Float,
    val typeface: Typeface,
    val hoursColor: Int,
    val minutesColor: Int,
    val secondsColor: Int,
    val dateColor: Int,
    val isAmbient: Boolean
)
