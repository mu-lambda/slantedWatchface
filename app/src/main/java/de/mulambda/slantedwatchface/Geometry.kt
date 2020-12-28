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
    private val TAG = this::class.qualifiedName

    // TODO(#5): Handle AM/PM
    private val hoursField = Calendar.HOUR_OF_DAY
    private val minutesField = Calendar.MINUTE
    private val secondsField = Calendar.SECOND


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
    ): HashMap<T, Pair<Int, Int>> {
        val m = HashMap<T, Pair<Int, Int>>()
        val valueBounds = Rect()
        for (value in range) {
            val text = getText(value)
            textPaint.getTextBounds(text, 0, text.length, valueBounds)
            m.put(value, Pair(valueBounds.width(), valueBounds.height()))
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


    fun getHours(c: Calendar): Pair<String, Pair<Int, Int>> =
        Pair("${c.get(hoursField)}", mHoursDimensions[c.get(hoursField)]!!)

    fun getMinutes(c: Calendar): Pair<String, Pair<Int, Int>> =
        Pair(padZero(c.get(minutesField)), mMinutesDimensions[c.get(minutesField)]!!)

    fun getSeconds(c: Calendar): Pair<String, Pair<Int, Int>> =
        Pair(padZero(c.get(secondsField)), mSecondsDimensions[c.get(secondsField)]!!)

    fun getDate(c: Calendar): Pair<String, Pair<Int, Int>> =
        Pair(
            formatDate(c),
            mDateDimensions[Pair(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.DAY_OF_WEEK))]!!
        )

    private fun calculateMaxHeight(dimensions: HashMap<Int, Pair<Int, Int>>): Int {
        var max = 0
        for ((_, v) in dimensions) {
            if (v.second > max) max = v.second
        }
        return max
    }

    fun calculateMaxHoursHeight() = calculateMaxHeight(mHoursDimensions)
    fun calculateMaxMinutesHeight() = calculateMaxHeight(mMinutesDimensions)
}