package de.mulambda.slantedwatchface

import android.content.SharedPreferences
import android.graphics.Color

object Settings {
    private val getInt = SharedPreferences::getInt
    private val putInt = SharedPreferences.Editor::putInt
    private val getFloat = SharedPreferences::getFloat
    private val putFloat = SharedPreferences.Editor::putFloat

    val ANGLE =
        Binding("angle", 30f, getFloat, putFloat)
    val HOURS_COLOR =
        Binding("hours-color", Color.GREEN, getInt, putInt)
    val MINUTES_COLOR =
        Binding("minutes-color", Color.WHITE, getInt, putInt)
    val SECONDS_COLOR =
        Binding("seconds-color", Color.GREEN, getInt, putInt)
    val COMPLICATION_ICON_COLOR =
        Binding("compliation-icon-color", Color.WHITE, getInt, putInt)
    val COMPLICATION_TEXT_COLOR =
        Binding("compliation-text-color", Color.GREEN, getInt, putInt)
    val DATE_COLOR =
        Binding("date-color", Color.YELLOW, getInt, putInt)

    val BINDINGS =
        arrayOf(ANGLE, HOURS_COLOR, MINUTES_COLOR, SECONDS_COLOR, DATE_COLOR)


    class Binding<T>(
        private val key: String,
        val default: T,
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