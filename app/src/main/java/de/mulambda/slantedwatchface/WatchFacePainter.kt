package de.mulambda.slantedwatchface

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.text.TextPaint
import android.util.SparseArray
import java.util.*

class WatchFacePainter(val veneer: Veneer, val bounds: RectF, val complications: Complications) {
    private val TAG = this::class.qualifiedName

    interface Complications {
        val ids: IntArray
        fun isComplicationEmpty(id: Int): Boolean
        fun draw(canvas: Canvas, currentTimeMillis: Long)
    }

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
    private val minutesSize = hoursSize / WatchFace.Constants.RATIO
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

    private val geometry = Geometry(
        Calendar.getInstance(),
        hoursPaint, minutesPaint, secondsPaint, datePaint
    )

    fun updateComplicationBounds(complicationBounds: SparseArray<Rect>) {
        val largeInset = 10f
        val maxHoursHeight = geometry.calculateMaxHoursHeight()
        val maxMinutesHeight = geometry.calculateMaxMinutesHeight()
        val hoursY = mCenterY + maxHoursHeight / 2
        val minutesX = mCenterX + largeInset
        val minutesY = hoursY - maxHoursHeight + maxMinutesHeight

        val complicationAreaLeft = minutesX
        val complicationAreaTop = minutesY + largeInset
        val complicationAreaRight = mCenterX * 2
        val complicationAreaBottom = hoursY

        var nonEmptyComplications = 0
        complications.ids.forEach {
            if (!complications.isComplicationEmpty(it)) {
                nonEmptyComplications++
            }
        }
        val emptyRect = Rect()
        if (nonEmptyComplications == 0) {
            complications.ids.forEach { complicationBounds.put(it, emptyRect) }
        } else {
            val inset = if (nonEmptyComplications > 1) 1 else 0
            val delta = (complicationAreaBottom - complicationAreaTop) / nonEmptyComplications
            var indexOfNonEmpty = 0
            complications.ids.forEach {
                if (complications.isComplicationEmpty(it)) {
                    complicationBounds.put(it, emptyRect)
                    return@forEach
                }
                val top = complicationAreaTop + delta * indexOfNonEmpty
                complicationBounds.put(
                    it, Rect(
                        complicationAreaLeft.toInt(),
                        top.toInt(),
                        complicationAreaRight.toInt(),
                        (top + delta).toInt() - inset
                    )
                )
                indexOfNonEmpty++
            }
        }
    }

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
        val (x1, y1) = rotate(x, y)

        val p = calculatePaintData(calendar)
        val (_, secondsBounds) = geometry.getSeconds(calendar)
        val secondsRect = RectF(
            p.secondsX,
            p.secondsY - secondsBounds.second,
            p.secondsX + secondsBounds.first,
            p.secondsY
        )
        val (_, dateBounds) = geometry.getDate(calendar)
        val dateRect = RectF(
            p.dateX,
            p.dateY - dateBounds.second,
            p.dateX + dateBounds.first,
            p.dateY
        )
        dateRect.union(secondsRect)
        dateRect.offset(bounds.left, bounds.top)
        return dateRect.contains(x1, y1)
    }

    fun rotate(x: Int, y: Int): Pair<Float, Float> {
        val dx = x - mCenterX - bounds.left
        val dy = y - mCenterY - bounds.top
        val a = -veneer.angle / 180f * Math.PI
        val dx1 = dx * Math.cos(a) - dy * Math.sin(a)
        val dy1 = dx * Math.sin(a) + dy * Math.cos(a)
        val x1 = bounds.left + mCenterX + dx1
        val y1 = bounds.top + mCenterY + dy1
        return Pair(x1.toFloat(), y1.toFloat())
    }

    private fun calculatePaintData(calendar: Calendar): PaintData {
        val (hours, hoursDim) = geometry.getHours(calendar)
        val (minutes, minutesDim) = geometry.getMinutes(calendar)
        val (seconds, _) = geometry.getSeconds(calendar)
        val (date, dateDim) = geometry.getDate(calendar)

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
        complications.draw(canvas, mCalendar.timeInMillis)
        canvas.restore()
    }
}