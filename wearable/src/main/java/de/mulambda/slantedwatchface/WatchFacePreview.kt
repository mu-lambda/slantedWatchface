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

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Drawable
import android.support.wearable.complications.ComplicationProviderInfo
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime
import java.util.*
import kotlin.math.min

/**
 * TODO: document your custom view class.
 */
class WatchFacePreview(
    context: Context,
    attrs: AttributeSet?,
) : View(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var painter: WatchFacePainter
    private val zdt = ZonedDateTime.now()
    private lateinit var complications: ComplicationsPreview
    private lateinit var watchFaceClipPath: Path
    private val iconMore = ContextCompat.getDrawable(context, R.drawable.ic_more)!!
    var onComplicationIdClick: (Int) -> Unit = { _ -> }
    var onColorSettingClick: (Settings.Binding<Int>) -> Unit = { _ -> }
    private val sharedPreferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key),
        Context.MODE_PRIVATE
    )!!.apply {
        registerOnSharedPreferenceChangeListener(this@WatchFacePreview)
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.LTGRAY
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val touchableBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        pathEffect =
            ComposePathEffect(
                DashPathEffect(floatArrayOf(4f, 4f), 0f),
                CornerPathEffect(10f)
            )
    }
    private val tochableColorBorderPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
        pathEffect =
            ComposePathEffect(
                DashPathEffect(floatArrayOf(4f, 4f), 0f),
                CornerPathEffect(10f)
            )
    }

    private lateinit var touchableBorderPath: Path


    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.i(TAG(), "onSharedPreferenceChanged")
        initializePainter()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // TODO(#15): fix this
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                val id = touchedComplicationId(event)
                if (id != null) return true
                for (s in touchableAreas) {
                    if (s.checker(painter, zdt, event.x.toInt(), event.y.toInt())) return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val id = touchedComplicationId(event)
                if (id != null) {
                    complications.onComplicationIdClick(id)
                    onComplicationIdClick(id)
                    return true
                }
                for (s in touchableAreas) {
                    if (s.checker(painter, zdt, event.x.toInt(), event.y.toInt())) {
                        highlightRect(s.recter(painter, zdt).toIntRect())
                        onColorSettingClick(s.binding)
                        return true
                    }
                }

            }
        }
        return super.onTouchEvent(event)
    }

    private fun touchedComplicationId(event: MotionEvent): Int? {
        val rotatedPoint = painter.rotate(event.x.toInt(), event.y.toInt())
        return complications.complicationIdByPoint(
            rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
        )
    }

    private data class ColorSetting(
        val binding: Settings.Binding<Int>,
        val checker: (WatchFacePainter, ZonedDateTime, Int, Int) -> Boolean,
        val recter: (WatchFacePainter, ZonedDateTime) -> RectF
    )

    private val touchableAreas = arrayOf(
        ColorSetting(
            Settings.HOURS_COLOR,
            WatchFacePainter::isHoursTap,
            WatchFacePainter::hoursRect
        ),
        ColorSetting(
            Settings.MINUTES_COLOR,
            WatchFacePainter::isMinutesTap,
            WatchFacePainter::minutesRect
        ),
        ColorSetting(
            Settings.SECONDS_COLOR,
            WatchFacePainter::isSecondsTap,
            WatchFacePainter::secondsRect
        ),
        ColorSetting(Settings.DATE_COLOR, WatchFacePainter::isDateTap, WatchFacePainter::dateRect),
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        initializePainter()
    }

    private fun initializePainter() {
        val dim = watchfaceSize().toFloat()
        val veneer = Veneer.fromSharedPreferences(sharedPreferences, context.assets, false)
        complications = ComplicationsPreview(veneer)
        painter = WatchFacePainter(
            ZonedDateTime.now(),
            veneer,
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                paddingLeft + dim,
                paddingTop + dim
            ),
            complications
        )
        val centerX = paddingLeft + dim / 2
        val centerY = paddingTop + dim / 2
        watchFaceClipPath = Path().apply {
            addCircle(centerX, centerY, dim / 2, Path.Direction.CW)
        }
        touchableBorderPath = Path().apply {
            for (touchableArea in touchableAreas) {
                rect(touchableArea.recter(painter, zdt).toIntRect())
            }
        }
        iconMore.setBounds(
            (centerX - iconMore.minimumWidth / 2).toInt(),
            (paddingTop + dim - iconMore.minimumHeight).toInt(),
            (centerX + iconMore.minimumWidth / 2).toInt(),
            (paddingTop + dim).toInt()
        )
        complications.updateComplicationLocations()
    }


    private fun watchfaceSize() =
        min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, width)
        initializePainter()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.clipPath(watchFaceClipPath)
        canvas.drawColor(Color.BLACK)

        canvas.save()
        with(painter) {
            canvas.rotate(veneer.angle, bounds.left + centerX, bounds.top + centerY)
        }
        canvas.drawPath(touchableBorderPath, tochableColorBorderPaint)
        canvas.restore()

        painter.draw(zdt, canvas)
        canvas.restore()
        val dim = watchfaceSize().toFloat()
        canvas.drawCircle(
            paddingLeft + dim / 2, paddingTop + dim / 2, dim / 2 - 1, borderPaint
        )
        iconMore.draw(canvas)
    }

    fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
        complications.setComplication(complicationId, info)
    }

    inner class ComplicationsPreview(val veneer: Veneer) : WatchFacePainter.ComplicationsPainter {
        private val complicationBounds = SparseArray<Rect>(Complications.MAX_NUMBER_OF_SLOTS)
        private val complicationInfos =
            SparseArray<ComplicationProviderInfo?>(Complications.MAX_NUMBER_OF_SLOTS)
        private val complicationIcons =
            SparseArray<Drawable?>(Complications.MAX_NUMBER_OF_SLOTS)
        private lateinit var borderPath: Path

        override fun isComplicationEmpty(id: Int): Boolean = false

        override fun draw(canvas: Canvas, zonedDateTime: ZonedDateTime) {
            for (id in veneer.visibleComplicationIds) {
                complicationIcons.get(id, null)?.draw(canvas)
            }
            canvas.drawPath(borderPath, touchableBorderPaint)
        }

        private fun setIconBounds(id: Int) {
            val rect = complicationBounds[id]
            val drawable = complicationIcons.get(id, null)
            if (drawable != null) {
                val w = drawable.minimumWidth
                val h = drawable.minimumHeight
                drawable.setBounds(
                    rect.centerX() - w / 2,
                    rect.centerY() - h / 2,
                    rect.centerX() + w / 2,
                    rect.centerY() + h / 2
                )
            }
        }

        fun updateComplicationLocations() {
            painter.updateComplicationBounds(complicationBounds)
            borderPath = Path().apply {
                veneer.visibleComplicationIds.forEach { id ->
                    rect(
                        complicationBounds[id]
                    )
                }
            }
            veneer.visibleComplicationIds.forEach(::setIconBounds)
        }

        fun complicationIdByPoint(x: Int, y: Int): Int? =
            veneer.visibleComplicationIds.find { id -> complicationBounds[id].contains(x, y) }

        @SuppressLint("RestrictedApi")
        fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
            complicationInfos.put(complicationId, info)
            val providerIcon = info?.providerIcon
            if (providerIcon != null) {
                providerIcon.loadDrawableAsync(
                    context,
                    { drawable ->
                        complicationIcons.put(complicationId, drawable)
                        setIconBounds(complicationId)
                        invalidate()
                    },
                    handler
                )
            } else {
                complicationIcons.put(complicationId, null)
                invalidate()
            }
        }

        fun onComplicationIdClick(id: Int) {
            val rect = complicationBounds[id]
            highlightRect(rect)
        }
    }

    private val unhighlightRunnable = Runnable {
        painter.unhighlight()
        invalidate()
    }

    private fun highlightRect(rect: Rect) {
        painter.highlightRect(rect)
        invalidate()
        handler.removeCallbacks(unhighlightRunnable)
        handler.postDelayed(unhighlightRunnable, 100L)
    }

}