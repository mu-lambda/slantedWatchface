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
import android.graphics.Color

object Complications {
    const val NUMBER_OF_SLOTS = 3
    const val TOP = 2021
    const val BOTTOM = TOP + NUMBER_OF_SLOTS - 1

    val RANGE = TOP..BOTTOM
}

object Settings {
    private val getInt = SharedPreferences::getInt
    private val putInt = SharedPreferences.Editor::putInt
    private val getFloat = SharedPreferences::getFloat
    private val putFloat = SharedPreferences.Editor::putFloat
    private val getString =
        { sp: SharedPreferences, key: String, defaultValue: String ->
            sp.getString(key, defaultValue)!!
        }
    private val putString = SharedPreferences.Editor::putString

    val ANGLE =
        Binding("angle", 30f, 0, getFloat, putFloat)
    val HOURS_COLOR =
        Binding("hours-color", Color.GREEN, R.string.hours_color, getInt, putInt)
    val MINUTES_COLOR =
        Binding("minutes-color", Color.WHITE, R.string.minutes_color, getInt, putInt)
    val SECONDS_COLOR =
        Binding("seconds-color", Color.GREEN, R.string.seconds_color, getInt, putInt)
    val COMPLICATION_ICON_COLOR =
        Binding("complication-icon-color", Color.WHITE, 0, getInt, putInt)
    val COMPLICATION_TEXT_COLOR =
        Binding("complication-text-color", Color.GREEN, 0, getInt, putInt)
    val DATE_COLOR =
        Binding("date-color", Color.YELLOW, R.string.date_color, getInt, putInt)
    val TYPEFACE =
        Binding("typeface", Typefaces.DEFAULT.displayName, 0, getString, putString)

    val BINDINGS =
        arrayOf(
            ANGLE,
            HOURS_COLOR,
            MINUTES_COLOR,
            SECONDS_COLOR,
            DATE_COLOR,
            COMPLICATION_ICON_COLOR,
            COMPLICATION_TEXT_COLOR,
            TYPEFACE,
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