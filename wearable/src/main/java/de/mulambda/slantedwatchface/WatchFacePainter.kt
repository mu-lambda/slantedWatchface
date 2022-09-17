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

import android.graphics.*
import android.text.TextPaint
import android.util.SparseArray
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class WatchFacePainter(
    calendar: Calendar,
    val veneer: Veneer,
    val bounds: RectF,
    private val complicationsPainter: ComplicationsPainter
) {
    private val sampleCalendar: Calendar = calendar.clone() as Calendar

    interface ComplicationsPainter {
        fun isComplicationEmpty(id: Int): Boolean
        fun draw(canvas: Canvas, currentTimeMillis: Long)
    }

    val centerX = bounds.width() / 2f
    val centerY = bounds.height() / 2f
    private val nonEmptyComplications =
        veneer.visibleComplicationIds.count { id -> !complicationsPainter.isComplicationEmpty(id) }
    val largerDate = veneer.largerDate
    private val hoursSize = centerY * 2 * veneer.typefaces.config.ySizeRatio
    private val hoursPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = hoursSize
        textScaleX = veneer.typefaces.config.hourScaleX
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }
    private val singleDigitHoursPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = hoursSize
        textScaleX = veneer.typefaces.config.hourScaleXSingleDigit
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }

    private val minutesRatio =
        if (nonEmptyComplications <= 2 && !largerDate) WatchFaceService.Constants.RATIO else 2f
    private val textScaleFactor =
        if (nonEmptyComplications <= 2 && !largerDate) 1f else 2f / WatchFaceService.Constants.RATIO
    private val minutesSize = hoursSize / minutesRatio
    private val minutesPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = minutesSize
        textScaleX = veneer.typefaces.config.minutesScaleX * textScaleFactor
        color = veneer.minutesColor
        isAntiAlias = !veneer.isAmbient
    }
    private val secondsSize = minutesSize / 2
    private val secondsPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = secondsSize
        textScaleX = veneer.typefaces.config.secondsScaleX * textScaleFactor
        color = veneer.secondsColor
        isAntiAlias = !veneer.isAmbient
    }

    private val dateHeightFactor = 0.7f
    private val dateSize =
        if (veneer.largerDate)
            ((hoursSize / 2f) / Complications.MAX_NUMBER_OF_SLOTS) * dateHeightFactor
        else secondsSize / 4
    private val datePaint = TextPaint().apply {
        typeface = veneer.typefaces.dateTypeface
        textSize = dateSize
        color = veneer.dateColor
        isAntiAlias = !veneer.isAmbient
        textScaleX =
            this.let { // poor man's scaling estimate
                val width =
                    if (veneer.largerDate)
                        bounds.width() / 2f
                    else
                        secondsPaint.measureText("00")
                val dateSize = it.measureText(Geometry.formatDate(sampleCalendar))
                val scale = width * 0.95f / dateSize
                if (scale < 1f) scale else 1f
            }
    }
    private val amPmSize = if (veneer.largerDate) minutesSize / 4 else dateSize
    private val amPmPaint = TextPaint().apply {
        typeface = veneer.typefaces.dateTypeface
        textSize = amPmSize
        color = veneer.amPmColor
        isAntiAlias = !veneer.isAmbient
        textScaleX = this.let { // poor man's scaling estimate
            val secondsSize = secondsPaint.measureText("00")
            val amSize = it.measureText("AM")
            val scale = secondsSize * 0.7f / amSize
            if (scale < 1f) scale else 1f
        }
    }


    private val geometry = Geometry(
        veneer.is24h,
        hoursPaint,
        singleDigitHoursPaint,
        minutesPaint,
        secondsPaint,
        datePaint,
    )

    fun shouldUpdate(newCalendar: Calendar): Boolean {
        val newNonEmptyComplications =
            veneer.visibleComplicationIds.count { id ->
                !complicationsPainter.isComplicationEmpty(id)
            }
        if (newNonEmptyComplications != nonEmptyComplications) return true
        return sampleCalendar.get(Calendar.HOUR_OF_DAY) != newCalendar.get(Calendar.HOUR_OF_DAY) ||
                sampleCalendar.get(Calendar.DAY_OF_WEEK) != newCalendar.get(Calendar.DAY_OF_WEEK) ||
                sampleCalendar.get(Calendar.DAY_OF_MONTH) != newCalendar.get(Calendar.DAY_OF_MONTH)
    }


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
        val smallInset = 3
        val maxHoursHeight = geometry.calculateMaxHoursHeight()
        val maxMinutesHeight = geometry.calculateMaxMinutesHeight()
        val hoursY = centerY + maxHoursHeight / 2
        val minutesX = centerX + largeInset
        val minutesY = hoursY - maxHoursHeight + maxMinutesHeight

        val complicationAreaLeft = minutesX
        val complicationAreaRight = centerX * 2
        val complicationAreaBottom = (hoursY + centerY * 2) / 2

        val complicationAreaTop =
            if (!veneer.largerDate)
                minutesY + largeInset
            else {
                val top = minutesY + largeInset
                val offset =
                    (complicationAreaBottom - top) / Complications.MAX_NUMBER_OF_SLOTS * dateHeightFactor
                top + offset + smallInset
            }

        val emptyRect = Rect()
        if (nonEmptyComplications == 0) {
            Complications.RANGE.forEach { complicationBounds.put(it, emptyRect) }
        } else {
            val inset = if (nonEmptyComplications > 1) smallInset else 0
            val delta = (complicationAreaBottom - complicationAreaTop) / nonEmptyComplications
            var indexOfNonEmpty = 0
            veneer.visibleComplicationIds.forEach {
                if (complicationsPainter.isComplicationEmpty(it)) {
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
            veneer.invisibleComplicationsIds.forEach {
                complicationBounds.put(it, emptyRect)
            }
        }
    }

    // Data is in Painter coordinates
    private data class PaintData(
        var hours: String = "",
        var hoursX: Float = 0f,
        var hoursY: Float = 0f,
        var minutes: String = "",
        var minutesX: Float = 0f,
        var minutesY: Float = 0f,
        var seconds: String = "",
        var secondsX: Float = 0f,
        var secondsY: Float = 0f,
        var amPm: String = "",
        var amPmX: Float = 0f,
        var amPmY: Float = 0f,
        var date: String = "",
        var dateX: Float = 0f,
        var dateY: Float = 0f
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
        val p = PaintData()
        calculatePaintData(calendar, p)
        return getDataRect(p.hoursX, p.hoursY, geometry.getHours(calendar))
    }

    fun minutesRect(calendar: Calendar): RectF {
        val p = PaintData()
        calculatePaintData(calendar, p)
        return getDataRect(p.minutesX, p.minutesY, geometry.getMinutes(calendar))
    }


    fun secondsRect(calendar: Calendar): RectF {
        val p = PaintData()
        calculatePaintData(calendar, p)
        return getDataRect(p.secondsX, p.secondsY, geometry.getSeconds(calendar))
    }

    fun dateRect(calendar: Calendar): RectF {
        val p = PaintData()
        calculatePaintData(calendar, p)
        return getDataRect(p.dateX, p.dateY, geometry.getDate(calendar))
    }

    private fun getDataRect(x: Float, y: Float, dataBounds: Geometry.Item): RectF {
        val secondsRect = RectF(x, y - dataBounds.height, x + dataBounds.width, y)
        secondsRect.offset(bounds.left, bounds.top)
        return secondsRect
    }

    fun rotate(x: Int, y: Int): Pair<Float, Float> {
        val dx = x - centerX - bounds.left
        val dy = y - centerY - bounds.top
        val a = -veneer.angle / 180f * Math.PI
        val dx1 = dx * cos(a) - dy * sin(a)
        val dy1 = dx * sin(a) + dy * cos(a)
        val x1 = bounds.left + centerX + dx1
        val y1 = bounds.top + centerY + dy1
        return Pair(x1.toFloat(), y1.toFloat())
    }

    private fun calculatePaintData(calendar: Calendar, outPaintData: PaintData) {
        val h = geometry.getHours(calendar)
        val m = geometry.getMinutes(calendar)
        val s = geometry.getSeconds(calendar)
        val d = geometry.getDate(calendar)

        val largeInset = 10f
        val smallInset = 2f
        with(outPaintData) {
            hours = h.text
            hoursX = centerX - h.width - (if (hours.length <= 1) largeInset else 0f)
            hoursY = centerY + h.height / 2

            minutes = m.text
            minutesX = centerX + largeInset
            minutesY = hoursY - h.height + m.height

            seconds = s.text
            secondsX = minutesX + m.width + largeInset


            date = d.text
            if (veneer.largerDate) {
                dateX = minutesX
                dateY = minutesY + d.height + largeInset
            } else {
                dateX = secondsX
                dateY = minutesY
            }
            if (veneer.largerDate)
                secondsY = minutesY
            else
                secondsY = dateY - d.height - 4 * smallInset

            amPm = if (calendar.get(Calendar.HOUR_OF_DAY) >= 12) "PM" else "AM"
            amPmX = secondsX
            amPmY = secondsY - s.height - 4 * smallInset
        }
    }

    private var paintData = PaintData()
    fun draw(calendar: Calendar, canvas: Canvas) {
        canvas.save()
        canvas.rotate(veneer.angle, bounds.left + centerX, bounds.top + centerY)
        calculatePaintData(calendar, paintData)
        highlightedPath?.let { canvas.drawPath(it, highlightPaint) }
        with(paintData) {
            canvas.drawText(
                hours, bounds.left + hoursX, bounds.top + hoursY,
                if (hours.length > 1) hoursPaint else singleDigitHoursPaint
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
            if (!veneer.is24h) {
                canvas.drawText(amPm, amPmX, amPmY, amPmPaint)
            }
        }
        complicationsPainter.draw(canvas, calendar.timeInMillis)
        canvas.restore()
    }
}