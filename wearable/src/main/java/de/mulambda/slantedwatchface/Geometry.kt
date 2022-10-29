/*
 *    Copyright (c) 2022 - present The Slanted Watch Face Authors
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
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.util.*

class Geometry(
    private val is24h: Boolean,
    hoursPaint: TextPaint,
    singleDigitHoursPaint: TextPaint,
    minutesPaint: TextPaint,
    secondsPaint: TextPaint,
    private val mDatePaint: TextPaint
) {
    data class Item(val text: String, val height: Int, val width: Int)


    private val mHoursDimensions = getHoursDimensions(singleDigitHoursPaint, hoursPaint)
    private val mMinutesDimensions = getDimensions(minutesPaint)
    private val mSecondsDimensions = getDimensions(secondsPaint)
    private val mDateDimensions: HashMap<Int, HashMap<DayOfWeek, Item>> = HashMap()

    private fun getDimensions(
        textPaint: TextPaint,
    ): HashMap<Int, Item> {
        val m = HashMap<Int, Item>()
        val valueBounds = Rect()
        for (value in 0..59) {
            val text = padZero(value)
            textPaint.getTextBounds(text, 0, text.length, valueBounds)
            m[value] = Item(text = text, width = valueBounds.width(), height = valueBounds.height())
        }
        return m
    }

    private fun getHoursDimensions(oneDigitPaint: TextPaint, twoDigitsPaint: TextPaint): HashMap<Int, Item> {
        val m = HashMap<Int, Item>()
        val bounds = Rect()
        for (value in 0..23) {
            val text = "${getHoursValue(value)}"
            (if (value < 10) oneDigitPaint else twoDigitsPaint).getTextBounds(text, 0, text.length, bounds)
            m[value] = Item(text = text, width = bounds.width(), height = bounds.height())
        }
        return m
    }

    private fun padZero(i: Int) = if (i < 10) "0$i" else "$i"
    private fun getHoursValue(hoursOfDay: Int) =
        if (is24h) hoursOfDay else when(hoursOfDay) {
            0 -> 12
            in 1..12 -> hoursOfDay
            else -> hoursOfDay - 12
        }

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


    fun getHours(c: ZonedDateTime): Item =
        mHoursDimensions[getHoursValue(c.hour)]!!

    fun getMinutes(c: ZonedDateTime): Item =
        mMinutesDimensions[c.minute]!!

    fun getSeconds(c: ZonedDateTime): Item =
        mSecondsDimensions[c.second]!!

    private fun calculateDateItem(c: ZonedDateTime): Item {
        val text = formatDate(GregorianCalendar.from(c))
        val bounds = Rect()
        mDatePaint.getTextBounds(text, 0, text.length, bounds)
        return Item(text = text, width = bounds.width(), height = bounds.height())
    }

    fun getDate(c: ZonedDateTime): Item {
        val dayOfMonthCache: HashMap<DayOfWeek, Item> =
            mDateDimensions[c.dayOfMonth] ?: HashMap<DayOfWeek, Item>().also {
                mDateDimensions[c.dayOfMonth] = it
            }
        return dayOfMonthCache[c.dayOfWeek] ?: calculateDateItem(c).also {
            dayOfMonthCache[c.dayOfWeek] = it
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