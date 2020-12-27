package de.mulambda.slantedwatchface

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import java.util.*

class WatchfacePainter(val veneer: Veneer, val bounds: RectF) {
    private val mCenterX = bounds.width() / 2
    private val mCenterY = bounds.height() / 2
    val hoursSize = mCenterY * 2
    val hoursPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = hoursSize
        textScaleX = 0.4f
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }
    val minutesSize = hoursSize / SlantedWatchface.Constants.RATIO
    val minutesPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = minutesSize
        textScaleX = 0.33f
        color = veneer.minutesColor
        isAntiAlias = !veneer.isAmbient
    }
    val secondsSize = minutesSize / 2
    val secondsPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = secondsSize
        textScaleX = 0.4f
        color = veneer.secondsColor
        isAntiAlias = !veneer.isAmbient
    }
    val dateSize = secondsSize / 3
    val datePaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = dateSize
        textScaleX = 0.5f
        color = veneer.dateColor
        isAntiAlias = !veneer.isAmbient
    }

    val boundsProvider = BoundsProvider(
        Calendar.getInstance(),
        hoursPaint, minutesPaint, secondsPaint, datePaint
    )

    // Data is in Painter coordinates
    private data class PaintData(
        var hours: String,
        var hoursX: Float, val hoursY: Float,
        var minutes: String,
        var minutesX: Float, val minutesY: Float,
        var seconds: String,
        var secondsX: Float, val secondsY: Float,
        var date: String,
        var dateX: Float,
        val dateY: Float
    )

    fun isDateAreaTap(calendar: Calendar, x: Int, y: Int): Boolean {
        val p = calculatePaintData(calendar)
        val (_, secondsBounds) = boundsProvider.getSeconds(calendar)
        val secondsRect = Rect(
            p.secondsX.toInt(),
            p.secondsY.toInt() - secondsBounds.second,
            p.secondsX.toInt() + secondsBounds.first,
            p.secondsY.toInt()
        )
        val (_, dateBounds) = boundsProvider.getDate(calendar)
        val dateRect = Rect(
            p.dateX.toInt(),
            p.dateY.toInt() - dateBounds.second,
            p.dateX.toInt() + dateBounds.first,
            p.dateY.toInt()
        )
        dateRect.union(secondsRect)
        dateRect.offset(bounds.left.toInt(), bounds.top.toInt())
        return dateRect.contains(x, y)
    }

    private fun calculatePaintData(calendar: Calendar): PaintData {
        val (hours, hoursDim) = boundsProvider.getHours(calendar)
        val (minutes, minutesDim) = boundsProvider.getMinutes(calendar)
        val (seconds, _) = boundsProvider.getSeconds(calendar)
        val (date, dateDim) = boundsProvider.getDate(calendar)

        val largeInset = 10f
        val smallInset = 2f
        val hoursX = mCenterX - hoursDim.first
        val hoursY = mCenterY + hoursDim.second / 2
        val minutesX = mCenterX + largeInset
        val minutesY = hoursY - hoursDim.second + minutesDim.second
        val dateX = minutesX + minutesDim.first + largeInset
        val dateY = minutesY
        val secondsX = dateX
        val secondsY = dateY - dateDim.second - smallInset

        return PaintData(
            hours = hours,
            hoursX = hoursX,
            hoursY = hoursY,
            minutes = minutes,
            minutesX = minutesX,
            minutesY = minutesY,
            seconds = seconds,
            secondsX = secondsX,
            secondsY = secondsY,
            date = date,
            dateX = dateX,
            dateY = dateY,
        )
    }

    fun draw(mCalendar: Calendar, canvas: Canvas) {
        with(calculatePaintData(mCalendar)) {
            canvas.drawText(hours, hoursX, hoursY, hoursPaint)
            canvas.drawText(minutes, minutesX, minutesY, minutesPaint)
            canvas.drawText(date, dateX, dateY, datePaint)
            if (!veneer.isAmbient) {
                canvas.drawText(seconds, secondsX, secondsY, secondsPaint)
            }
        }

    }
}