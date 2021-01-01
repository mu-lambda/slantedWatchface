package de.mulambda.slantedwatchface

import android.graphics.*
import android.text.TextPaint
import android.util.SparseArray
import java.util.*

class WatchFacePainter(
    val veneer: Veneer,
    val bounds: RectF,
    private val complications: Complications
) {
    interface Complications {
        val ids: IntRange
        fun isComplicationEmpty(id: Int): Boolean
        fun draw(canvas: Canvas, currentTimeMillis: Long)
    }


    val centerX = bounds.width() / 2f
    val centerY = bounds.height() / 2f
    private val hoursSize = centerY * 2
    private val hoursPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = hoursSize
        textScaleX = 0.4f
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }
    private val minutesSize = hoursSize / WatchFaceService.Constants.RATIO
    private val minutesPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = minutesSize
        textScaleX = 0.33f
        color = veneer.minutesColor
        isAntiAlias = !veneer.isAmbient
    }
    private val secondsSize = minutesSize / 2
    private val secondsPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = secondsSize
        textScaleX = 0.4f
        color = veneer.secondsColor
        isAntiAlias = !veneer.isAmbient
    }
    private val dateSize = secondsSize / 4
    private val datePaint = TextPaint().apply {
        typeface = veneer.typefaces.dateTypeface
        textSize = dateSize
        color = veneer.dateColor
        isAntiAlias = !veneer.isAmbient
        textScaleX = this.let { // poor man's scaling estimate
            val secondsSize = secondsPaint.measureText("00")
            val dateSize = it.measureText("WED 00")
            val scale = secondsSize / dateSize
            if (scale < 1f) scale else 1f
        }
    }

    private val geometry = Geometry(
        Calendar.getInstance(),
        hoursPaint, minutesPaint, secondsPaint, datePaint
    )

    private val highlightPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        pathEffect = CornerPathEffect(10f)
    }
    private var highlightedPath: Path? = null

    fun highlightRect(rect: Rect) {
        highlightedPath = Path().apply { rect(rect) }
    }

    fun unhighlight() {
        highlightedPath = null
    }

    fun updateComplicationBounds(complicationBounds: SparseArray<Rect>) {
        val largeInset = 10f
        val maxHoursHeight = geometry.calculateMaxHoursHeight()
        val maxMinutesHeight = geometry.calculateMaxMinutesHeight()
        val hoursY = centerY + maxHoursHeight / 2
        val minutesX = centerX + largeInset
        val minutesY = hoursY - maxHoursHeight + maxMinutesHeight

        val complicationAreaLeft = minutesX
        val complicationAreaTop = minutesY + largeInset
        val complicationAreaRight = centerX * 2
        val complicationAreaBottom = (hoursY + centerY * 2) / 2

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
            val inset = if (nonEmptyComplications > 1) 3 else 0
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

    fun isHoursTap(calendar: Calendar, x: Int, y: Int) = isTap(calendar, x, y, ::hoursRect)
    fun isMinutesTap(calendar: Calendar, x: Int, y: Int) = isTap(calendar, x, y, ::minutesRect)
    fun isSecondsTap(calendar: Calendar, x: Int, y: Int) = isTap(calendar, x, y, ::secondsRect)
    fun isDateTap(calendar: Calendar, x: Int, y: Int) = isTap(calendar, x, y, ::dateRect)

    private fun isTap(calendar: Calendar, x: Int, y: Int, f: (Calendar) -> RectF): Boolean {
        val (x1, y1) = rotate(x, y)
        return f(calendar).contains(x1, y1)
    }


    fun hoursRect(calendar: Calendar): RectF {
        val p = calculatePaintData(calendar)
        return getDataRect(p.hoursX, p.hoursY, geometry.getHours(calendar).second)
    }

    fun minutesRect(calendar: Calendar): RectF {
        val p = calculatePaintData(calendar)
        return getDataRect(p.minutesX, p.minutesY, geometry.getMinutes(calendar).second)
    }


    fun secondsRect(calendar: Calendar): RectF {
        val p = calculatePaintData(calendar)
        return getDataRect(p.secondsX, p.secondsY, geometry.getSeconds(calendar).second)
    }

    fun dateRect(calendar: Calendar): RectF {
        val p = calculatePaintData(calendar)
        return getDataRect(p.dateX, p.dateY, geometry.getDate(calendar).second)
    }


    private fun getDataRect(x: Float, y: Float, dataBounds: Pair<Int, Int>): RectF {
        val secondsRect = RectF(x, y - dataBounds.second, x + dataBounds.first, y)
        secondsRect.offset(bounds.left, bounds.top)
        return secondsRect
    }

    fun rotate(x: Int, y: Int): Pair<Float, Float> {
        val dx = x - centerX - bounds.left
        val dy = y - centerY - bounds.top
        val a = -veneer.angle / 180f * Math.PI
        val dx1 = dx * Math.cos(a) - dy * Math.sin(a)
        val dy1 = dx * Math.sin(a) + dy * Math.cos(a)
        val x1 = bounds.left + centerX + dx1
        val y1 = bounds.top + centerY + dy1
        return Pair(x1.toFloat(), y1.toFloat())
    }

    private fun calculatePaintData(calendar: Calendar): PaintData {
        val (hours, hoursDim) = geometry.getHours(calendar)
        val (minutes, minutesDim) = geometry.getMinutes(calendar)
        val (seconds, _) = geometry.getSeconds(calendar)
        val (date, dateDim) = geometry.getDate(calendar)

        val largeInset = 10f
        val smallInset = 2f
        val hoursX = centerX - hoursDim.first
        val hoursY = centerY + hoursDim.second / 2
        val minutesX = centerX + largeInset
        val minutesY = hoursY - hoursDim.second + minutesDim.second
        val dateX = minutesX + minutesDim.first + largeInset
        val dateY = minutesY
        val secondsX = dateX
        val secondsY = dateY - dateDim.second - 4 * smallInset

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
        canvas.rotate(veneer.angle, bounds.left + centerX, bounds.top + centerY)
        highlightedPath?.let { canvas.drawPath(it, highlightPaint) }
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