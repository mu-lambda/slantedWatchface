/*
 *    Copyright (c) 2021 - present The Slanted Watch Face Authors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package de.mulambda.slantedwatchface

import android.content.SharedPreferences
import android.graphics.Color

object Complications {
    const val MAX_NUMBER_OF_SLOTS = 3
    const val TOP = 2021
    const val BOTTOM = TOP + MAX_NUMBER_OF_SLOTS - 1

    val RANGE = TOP..BOTTOM
}

object Settings {
    private val getInt = SharedPreferences::getInt
    private val putInt = SharedPreferences.Editor::putInt
    private val getBoolean = SharedPreferences::getBoolean
    private val putBoolean = SharedPreferences.Editor::putBoolean

    private fun getString(sp: SharedPreferences, key: String, defaultValue: String): String =
        sp.getString(key, defaultValue)!!

    private val putString = SharedPreferences.Editor::putString

    val LEFT_HANDED =
        Binding("left-handed", true, 0, getBoolean, putBoolean)
    val COLORFUL_AMBIENT =
        Binding("colorful-ambient", true, 0, getBoolean, putBoolean)
    val HOURS_COLOR =
        Binding("hours-color", Color.GREEN, R.string.hours_color, getInt, putInt)
    val MINUTES_COLOR =
        Binding("minutes-color", Color.WHITE, R.string.minutes_color, getInt, putInt)
    val SECONDS_COLOR =
        Binding("seconds-color", Color.GREEN, R.string.seconds_color, getInt, putInt)
    val AM_PM_COLOR =
        Binding("am-pm-color", Color.GREEN, R.string.am_pm_color, getInt, putInt)
    val COMPLICATION_ICON_COLOR =
        Binding("complication-icon-color", Color.WHITE, 0, getInt, putInt)
    val COMPLICATION_TEXT_COLOR =
        Binding("complication-text-color", Color.GREEN, 0, getInt, putInt)
    val DATE_COLOR =
        Binding("date-color", Color.YELLOW, R.string.date_color, getInt, putInt)
    val LARGER_DATE =
        Binding("larger-date", false, 0, getBoolean, putBoolean)
    val TYPEFACE =
        Binding("typeface", Typefaces.DEFAULT.displayName, 0, ::getString, putString)
    val IS24H = Binding("is24h", true, 0, getBoolean, putBoolean)

    private val BINDINGS =
        arrayOf(
            LEFT_HANDED,
            COLORFUL_AMBIENT,
            HOURS_COLOR,
            MINUTES_COLOR,
            SECONDS_COLOR,
            AM_PM_COLOR,
            DATE_COLOR,
            LARGER_DATE,
            COMPLICATION_ICON_COLOR,
            COMPLICATION_TEXT_COLOR,
            TYPEFACE,
            IS24H,
        )


    data class Binding<T>(
        private val key: String,
        val default: T,
        val stringId: Int,
        private val getter: (SharedPreferences, String, T) -> T,
        private val putter: (SharedPreferences.Editor, String, T) -> SharedPreferences.Editor
    ) {
        fun put(editor: SharedPreferences.Editor, value: T) = putter(editor, key, value)
        fun get(sharedPreferences: SharedPreferences) = getter(sharedPreferences, key, default)
        fun putDefault(editor: SharedPreferences.Editor) = put(editor, default)
    }

    fun applyDefault(editor: SharedPreferences.Editor) {
        BINDINGS.forEach { b -> b.putDefault(editor) }
    }
}