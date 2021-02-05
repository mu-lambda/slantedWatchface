/*
 * Copyright 2021-present The Slanted Watchface Authors. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.mulambda.slantedwatchface

import android.graphics.*
import android.text.TextPaint
import android.util.SparseArray
import java.util.*

class WatchFacePainter(
    calendar: Calendar,
    val veneer: Veneer,
    val bounds: RectF,
    private val complications: Complications
) {
    private val sampleCalendar: Calendar = calendar.clone() as Calendar

    interface Complications {
        val ids: IntRange
        fun isComplicationEmpty(id: Int): Boolean
        fun draw(canvas: Canvas, currentTimeMillis: Long)
    }

    val centerX = bounds.width() / 2f
    val centerY = bounds.height() / 2f
    private val nonEmptyComplications =
        complications.ids.count { id -> !complications.isComplicationEmpty(id) }
    private val hoursSize = centerY * 2 * veneer.typefaces.config.ySizeRatio
    private val hoursPaint = TextPaint().apply {
        typeface = veneer.typefaces.timeTypeface
        textSize = hoursSize
        textScaleX = veneer.typefaces.config.hourScaleX
        color = veneer.hoursColor
        isAntiAlias = !veneer.isAmbient
    }
    private val minutesRatio =
        if (nonEmptyComplications <= 2) WatchFaceService.Constants.RATIO else 2f
    private val textScaleFactor =
        if (nonEmptyComplications <= 2) 1f else 2f / WatchFaceService.Constants.RATIO
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
    private val dateSize = secondsSize / 4
    private val datePaint = TextPaint().apply {
        typeface = veneer.typefaces.dateTypeface
        textSize = dateSize
        color = veneer.dateColor
        isAntiAlias = !veneer.isAmbient
        textScaleX = this.let { // poor man's scaling estimate
            val secondsSize = secondsPaint.measureText("00")
            val dateSize = it.measureText(Geometry.formatDate(sampleCalendar))
            val scale = secondsSize * 0.95f / dateSize
            if (scale < 1f) scale else 1f
        }
    }

    private val geometry = Geometry(
        sampleCalendar,
        hoursPaint,
        minutesPaint,
        secondsPaint,
        datePaint
    )

    fun shouldUpdate(newCalendar: Calendar): Boolean {
        val newNonEmptyComplications =
            complications.ids.count { id -> !complications.isComplicationEmpty(id) }
        if (newNonEmptyComplications != nonEmptyComplications) return true
        return sampleCalendar.get(Calendar.DAY_OF_WEEK) != newCalendar.get(Calendar.DAY_OF_WEEK) ||
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
        val maxHoursHeight = geometry.calculateMaxHoursHeight()
        val maxMinutesHeight = geometry.calculateMaxMinutesHeight()
        val hoursY = centerY + maxHoursHeight / 2
        val minutesX = centerX + largeInset
        val minutesY = hoursY - maxHoursHeight + maxMinutesHeight

        val complicationAreaLeft = minutesX
        val complicationAreaTop = minutesY + largeInset
        val complicationAreaRight = centerX * 2
        val complicationAreaBottom = (hoursY + centerY * 2) / 2

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
        var hours: String = "",
        var hoursX: Float = 0f,
        var hoursY: Float = 0f,
        var minutes: String = "",
        var minutesX: Float = 0f,
        var minutesY: Float = 0f,
        var seconds: String = "",
        var secondsX: Float = 0f,
        var secondsY: Float = 0f,
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
        val dx1 = dx * Math.cos(a) - dy * Math.sin(a)
        val dy1 = dx * Math.sin(a) + dy * Math.cos(a)
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
            hoursX = centerX - h.width
            hoursY = centerY + h.height / 2

            minutes = m.text
            minutesX = centerX + largeInset
            minutesY = hoursY - h.height + m.height

            date = d.text
            dateX = minutesX + m.width + largeInset
            dateY = minutesY

            seconds = s.text
            secondsX = dateX
            secondsY = dateY - d.height - 4 * smallInset
        }
    }

    private var paintData = PaintData()
    fun draw(calendar: Calendar, canvas: Canvas) {
        canvas.save()
        canvas.rotate(veneer.angle, bounds.left + centerX, bounds.top + centerY)
        highlightedPath?.let { canvas.drawPath(it, highlightPaint) }
        calculatePaintData(calendar, paintData)
        with(paintData) {
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
        complications.draw(canvas, calendar.timeInMillis)
        canvas.restore()
    }
}