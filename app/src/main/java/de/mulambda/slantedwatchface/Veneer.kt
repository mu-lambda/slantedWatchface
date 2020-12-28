package de.mulambda.slantedwatchface

import android.content.SharedPreferences
import android.graphics.Color

// Typeface and color settings for a watchface
class Veneer(
    val angle: Float,
    val typefaces: Typefaces,
    val hoursColor: Int,
    val minutesColor: Int,
    val secondsColor: Int,
    val dateColor: Int,
    val isAmbient: Boolean
) {


    companion object {
        const val ANGLE_KEY = "angle"
        fun fromSharedPreferences(
            sharedPreferences: SharedPreferences,
            typefaces: Typefaces,
            isAmbient: Boolean
        ) =
            Veneer(
                angle = getAngle(sharedPreferences),
                typefaces = typefaces,
                hoursColor = if (!isAmbient) WatchFace.Constants.HOURS_COLOR else Color.WHITE,
                minutesColor = if (!isAmbient) WatchFace.Constants.MINUTES_COLOR else Color.WHITE,
                secondsColor = if (!isAmbient) WatchFace.Constants.SECONDS_COLOR else Color.WHITE,
                dateColor = if (!isAmbient) WatchFace.Constants.DATE_COLOR else Color.WHITE,
                isAmbient = isAmbient,
            )

        fun getAngle(sharedPreferences: SharedPreferences) =
            sharedPreferences.getFloat(ANGLE_KEY, WatchFace.Constants.ANGLE)

        fun applyDefault(editor: SharedPreferences.Editor) =
            editor.let {
                setAngle(it, WatchFace.Constants.ANGLE)
            }

        fun setAngle(editor: SharedPreferences.Editor, angle: Float) =
            editor.putFloat(ANGLE_KEY, angle)

    }

}
