package de.mulambda.slantedwatchface

import android.graphics.Rect
import android.text.TextPaint
import java.util.*
import kotlin.collections.HashMap

class Geometry(
    val mCalendar: Calendar,
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
    private val mDateDimensions = getDimensions(
        mDatePaint,
        sequence {
            for (dayOfMonth in range(Calendar.DAY_OF_MONTH))
                for (dayOfWeek in range(Calendar.DAY_OF_WEEK))
                    yield(Pair(dayOfMonth, dayOfWeek))
        }.asIterable(),
        ::formatDate
    )

    private fun range(calendarField: Int): IntRange {
        return mCalendar.getMinimum(calendarField)..mCalendar.getMaximum(calendarField)
    }

    private fun <T> getDimensions(
        textPaint: TextPaint,
        range: Iterable<T>,
        getText: (T) -> String
    ): HashMap<T, Item> {
        val m = HashMap<T, Item>()
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

    private fun formatDate(p: Pair<Int, Int>): String {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, p.first)
        c.set(Calendar.DAY_OF_WEEK, p.second)
        return formatDate(c)
    }

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
        mDateDimensions[Pair(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.DAY_OF_WEEK))]!!

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