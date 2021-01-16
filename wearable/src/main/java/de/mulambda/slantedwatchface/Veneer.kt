/*
 * Copyright 2021-present The Slanted Watchface Authors. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.mulambda.slantedwatchface

import android.content.SharedPreferences
import android.content.res.AssetManager
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
            assets: AssetManager,
            isAmbient: Boolean
        ) =
            Veneer(
                angle = Settings.ANGLE.get(p),
                typefaces = Typefaces(assets, Typefaces.configByString(Settings.TYPEFACE.get(p))),
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

    fun whiteOf(baseColor: Int) =
        Color.HSVToColor(Color.alpha(baseColor), floatArrayOf(0f, 0f, 0f).also {
            Color.colorToHSV(baseColor, it)
            it[0] = 0f // hue
            it[1] = 0f // saturation
        })

    fun withColorScheme(baseColor: Int) =
        copy(
            hoursColor = baseColor,
            minutesColor = whiteOf(baseColor),
            secondsColor = baseColor,
            complicationIconColor = whiteOf(baseColor),
            complicationTextColor = baseColor,
            dateColor = applyColorValue(dateColor, baseColor)
        )

    private fun applyColorValue(color: Int, valueSource: Int): Int {
        val colorHSV = floatArrayOf(0f, 0f, 0f)
        val sourceHSV = floatArrayOf(0f, 0f, 0f)
        Color.colorToHSV(color, colorHSV)
        Color.colorToHSV(valueSource, sourceHSV)
        return Color.HSVToColor(
            Color.alpha(color), floatArrayOf(colorHSV[0], colorHSV[1], sourceHSV[2])
        )
    }
}
