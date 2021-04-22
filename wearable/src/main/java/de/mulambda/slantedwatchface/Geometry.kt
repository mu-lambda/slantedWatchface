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

import android.graphics.Rect
import android.text.TextPaint
import java.util.*
import kotlin.collections.HashMap

class Geometry(
    private val calendar: Calendar,
    mHoursPaint: TextPaint,
    mMinutesPaint: TextPaint,
    mSecondsPaint: TextPaint,
    private val mDatePaint: TextPaint
) {
    // TODO(#5): Handle AM/PM
    private val hoursField = Calendar.HOUR_OF_DAY
    private val minutesField = Calendar.MINUTE
    private val secondsField = Calendar.SECOND

    data class Item(val text: String, val height: Int, val width: Int)


    private val mHoursDimensions =
        getDimensions(mHoursPaint, range(hoursField)) { i -> "$i" }
    private val mMinutesDimensions =
        getDimensions(mMinutesPaint, range(minutesField), ::padZero)
    private val mSecondsDimensions =
        getDimensions(mSecondsPaint, range(secondsField), ::padZero)
    private val mDateDimensions: HashMap<Int, HashMap<Int, Item>> =
        HashMap()

    private fun range(calendarField: Int): IntRange {
        return calendar.getMinimum(calendarField)..calendar.getMaximum(calendarField)
    }

    private fun getDimensions(
        textPaint: TextPaint,
        range: IntRange,
        getText: (Int) -> String
    ): HashMap<Int, Item> {
        val m = HashMap<Int, Item>()
        val valueBounds = Rect()
        for (value in range) {
            val text = getText(value)
            textPaint.getTextBounds(text, 0, text.length, valueBounds)
            m[value] = Item(text = text, width = valueBounds.width(), height = valueBounds.height())
        }
        return m
    }

    private fun padZero(i: Int) = if (i < 10) "0$i" else "$i"

    companion object {
        fun formatDate(c: Calendar) =
            "${
                c.getDisplayName(
                    Calendar.DAY_OF_WEEK,
                    Calendar.SHORT,
                    Locale.getDefault()
                )
            } " + "${c.get(Calendar.DAY_OF_MONTH)}"
    }


    fun getHours(c: Calendar): Item =
        mHoursDimensions[c.get(hoursField)]!!

    fun getMinutes(c: Calendar): Item =
        mMinutesDimensions[c.get(minutesField)]!!

    fun getSeconds(c: Calendar): Item =
        mSecondsDimensions[c.get(secondsField)]!!

    private fun calculateDateItem(c: Calendar): Item {
        val text = formatDate(c)
        val bounds = Rect()
        mDatePaint.getTextBounds(text, 0, text.length, bounds)
        return Item(text = text, width = bounds.width(), height = bounds.height())
    }

    fun getDate(c: Calendar): Item {
        val dayOfMonthCache = mDateDimensions[c.get(Calendar.DAY_OF_MONTH)] ?: HashMap<Int, Item> ().also {
            mDateDimensions[c.get(Calendar.DAY_OF_MONTH)] = it
        }
        return dayOfMonthCache[c.get(Calendar.DAY_OF_WEEK)] ?: calculateDateItem(c).also {
            dayOfMonthCache[c.get(Calendar.DAY_OF_WEEK)] = it
        }
    }

    private fun calculateMaxHeight(dimensions: HashMap<Int, Item>): Int {
        var max = 0
        for ((_, v) in dimensions) {
            if (v.height > max) max = v.height
        }
        return max
    }

    fun calculateMaxHoursHeight() = calculateMaxHeight(mHoursDimensions)
    fun calculateMaxMinutesHeight() = calculateMaxHeight(mMinutesDimensions)
}