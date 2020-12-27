package de.mulambda.slantedwatchface

import android.graphics.Canvas
import android.graphics.RectF
import android.text.TextPaint
import java.util.*

class WatchfacePainter(val veneer: Veneer, val bounds: RectF) {
    private val mCenterX = bounds.width() / 2f
    private val mCenterY = bounds.height() / 2f
    private val hoursSize = mCenterY * 2
    private val hoursPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = hoursSize
        textScaleX = 0.4f
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }
    private val minutesSize = hoursSize / SlantedWatchface.Constants.RATIO
    private val minutesPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = minutesSize
        textScaleX = 0.33f
        color = veneer.minutesColor
        isAntiAlias = !veneer.isAmbient
    }
    private val secondsSize = minutesSize / 2
    private val secondsPaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = secondsSize
        textScaleX = 0.4f
        color = veneer.secondsColor
        isAntiAlias = !veneer.isAmbient
    }
    private val dateSize = secondsSize / 3
    private val datePaint = TextPaint().apply {
        typeface = veneer.typeface
        textSize = dateSize
        textScaleX = 0.5f
        color = veneer.dateColor
        isAntiAlias = !veneer.isAmbient
    }

    internal val boundsProvider = BoundsProvider(
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
        val secondsRect = RectF(
            p.secondsX,
            p.secondsY - secondsBounds.second,
            p.secondsX + secondsBounds.first,
            p.secondsY
        )
        val (_, dateBounds) = boundsProvider.getDate(calendar)
        val dateRect = RectF(
            p.dateX,
            p.dateY - dateBounds.second,
            p.dateX + dateBounds.first,
            p.dateY
        )
        dateRect.union(secondsRect)
        dateRect.offset(bounds.left, bounds.top)
        return dateRect.contains(x.toFloat(), y.toFloat())
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
        canvas.save()
        canvas.rotate(veneer.angle, bounds.left + mCenterX, bounds.top + mCenterY)
        with(calculatePaintData(mCalendar)) {
            canvas.drawText(
                hours, bounds.left + hoursX, bounds.top + hoursY, hoursPaint
            )
            canvas.drawText(
                minutes, bounds.left + minutesX, bounds.top + minutesY, minutesPaint
            )
            canvas.drawText(
                date, bounds.left + dateX, bounds.top + dateY, datePaint
            )
            if (!veneer.isAmbient) {
                canvas.drawText(
                    seconds, bounds.left + secondsX, bounds.top + secondsY, secondsPaint
                )
            }
        }
        canvas.restore()
    }
}