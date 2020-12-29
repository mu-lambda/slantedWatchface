package de.mulambda.slantedwatchface

import android.content.SharedPreferences
import android.graphics.Color

// Typeface and color settings for a watchface
data class Veneer(
    val angle: Float,
    val typefaces: Typefaces,
    val hoursColor: Int,
    val minutesColor: Int,
    val secondsColor: Int,
    val dateColor: Int,
    val complicationIconColor: Int,
    val complicationTextColor: Int,
    val isAmbient: Boolean
) {
    companion object {
        val AMBIENT_COLOR = Color.WHITE

        fun fromSharedPreferences(
            p: SharedPreferences,
            typefaces: Typefaces,
            isAmbient: Boolean
        ) =
            Veneer(
                angle = Settings.ANGLE.get(p),
                typefaces = typefaces,
                hoursColor = if (!isAmbient) Settings.HOURS_COLOR.get(p) else AMBIENT_COLOR,
                minutesColor = if (!isAmbient) Settings.MINUTES_COLOR.get(p) else AMBIENT_COLOR,
                secondsColor = if (!isAmbient) Settings.SECONDS_COLOR.get(p) else AMBIENT_COLOR,
                dateColor = if (!isAmbient) Settings.DATE_COLOR.get(p) else AMBIENT_COLOR,
                complicationIconColor = if (!isAmbient) Settings.COMPLICATION_ICON_COLOR.get(p) else AMBIENT_COLOR,
                complicationTextColor = if (!isAmbient) Settings.COMPLICATION_TEXT_COLOR.get(p) else AMBIENT_COLOR,
                isAmbient = isAmbient,
            )

    }

    fun put(editor: SharedPreferences.Editor): SharedPreferences.Editor =
        if (isAmbient) editor // not preferences yet for ambient mode
        else {
            Settings.ANGLE.put(editor, angle)
            Settings.HOURS_COLOR.put(editor, hoursColor)
            Settings.MINUTES_COLOR.put(editor, minutesColor)
            Settings.SECONDS_COLOR.put(editor, secondsColor)
            Settings.DATE_COLOR.put(editor, dateColor)
            Settings.COMPLICATION_ICON_COLOR.put(editor, complicationIconColor)
            Settings.COMPLICATION_TEXT_COLOR.put(editor, complicationTextColor)
        }

    fun withColorScheme(baseColor: Int) =
        copy(
            hoursColor = baseColor,
            minutesColor = Color.WHITE,
            secondsColor = baseColor,
            complicationIconColor = Color.WHITE,
            complicationTextColor = baseColor
        )
}
