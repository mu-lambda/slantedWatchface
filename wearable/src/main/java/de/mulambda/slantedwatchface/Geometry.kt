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
    mDatePaint: TextPaint
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
        HashMap<Int, HashMap<Int, Item>>().also {
            val bounds = Rect()
            val c = Calendar.getInstance()
            for (dayOfMonth in range(Calendar.DAY_OF_MONTH)) {
                val m = HashMap<Int, Item>()
                it.put(dayOfMonth, m)
                for (dayOfWeek in range(Calendar.DAY_OF_WEEK)) {
                    c.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    c.set(Calendar.DAY_OF_WEEK, dayOfWeek)

                    val text = formatDate(c)
                    mDatePaint.getTextBounds(text, 0, text.length, bounds)
                    m.put(
                        dayOfWeek,
                        Item(text = text, width = bounds.width(), height = bounds.height())
                    )

                }
            }
        }

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
            m.put(
                value,
                Item(text = text, width = valueBounds.width(), height = valueBounds.height())
            )
        }
        return m
    }

    private fun padZero(i: Int) = if (i < 10) "0$i" else "$i"

    private fun formatDate(c: Calendar) =
        "${
            c.getDisplayName(
                Calendar.DAY_OF_WEEK,
                Calendar.SHORT,
                Locale.getDefault()
            )
        } " + "${c.get(Calendar.DAY_OF_MONTH)}"


    fun getHours(c: Calendar): Item =
        mHoursDimensions[c.get(hoursField)]!!

    fun getMinutes(c: Calendar): Item =
        mMinutesDimensions[c.get(minutesField)]!!

    fun getSeconds(c: Calendar): Item =
        mSecondsDimensions[c.get(secondsField)]!!

    fun getDate(c: Calendar): Item =
        mDateDimensions[c.get(Calendar.DAY_OF_MONTH)]!![c.get(Calendar.DAY_OF_WEEK)]!!

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