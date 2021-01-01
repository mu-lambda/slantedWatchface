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
import java.util.*

/**
 * TODO: document your custom view class.
 */
class WatchFacePreview(
    context: Context,
    attrs: AttributeSet?,
) : View(context, attrs), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var painter: WatchFacePainter
    private val calendar = Calendar.getInstance()
    private val complications = ComplicationsPreview()
    private lateinit var watchFaceClipPath: Path
    var onComplicationIdClick: (Int) -> Unit = { _ -> }
    var onColorSettingClick: (Settings.Binding<Int>) -> Unit = { _ -> }
    val sharedPreferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key),
        Context.MODE_PRIVATE
    ).apply {
        registerOnSharedPreferenceChangeListener(this@WatchFacePreview)
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.LTGRAY
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val tochableBorderPaint = Paint().apply {
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
                    if (s.checker(painter, calendar, event.x.toInt(), event.y.toInt())) return true
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
                    if (s.checker(painter, calendar, event.x.toInt(), event.y.toInt())) {
                        highlightRect(s.recter(painter, calendar).toIntRect())
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
        val id = complications.complicationIdByPoint(
            rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
        )
        return id
    }

    private data class ColorSetting(
        val binding: Settings.Binding<Int>,
        val checker: (WatchFacePainter, Calendar, Int, Int) -> Boolean,
        val recter: (WatchFacePainter, Calendar) -> RectF
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
        painter = WatchFacePainter(
            Veneer.fromSharedPreferences(sharedPreferences, Typefaces(context.assets), false),
            RectF(
                paddingLeft.toFloat(),
                paddingTop.toFloat(),
                paddingLeft + dim,
                paddingTop + dim
            ),
            complications
        )
        watchFaceClipPath = Path().apply {
            addCircle(paddingLeft + dim / 2, paddingTop + dim / 2, dim / 2, Path.Direction.CW)
        }
        touchableBorderPath = Path().apply {
            for (touchableArea in touchableAreas) {
                rect(touchableArea.recter(painter, calendar).toIntRect())
            }
        }
        complications.updateComplicationLocations()
    }


    private fun watchfaceSize() =
        Math.min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom)

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

        painter.draw(calendar, canvas)
        canvas.restore()
        val dim = watchfaceSize().toFloat()
        canvas.drawCircle(
            paddingLeft + dim / 2, paddingTop + dim / 2, dim / 2 - 1, borderPaint
        )
    }

    fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
        complications.setComplication(complicationId, info)
    }

    inner class ComplicationsPreview : WatchFacePainter.Complications {
        private val complicationBounds = SparseArray<Rect>(Complications.ALL.size)
        private val complicationInfos =
            SparseArray<ComplicationProviderInfo?>(Complications.ALL.size)
        private val complicationIcons =
            SparseArray<Drawable?>(Complications.ALL.size)
        private lateinit var borderPath: Path

        override val ids: IntArray
            get() = Complications.ALL

        override fun isComplicationEmpty(id: Int): Boolean = false

        override fun draw(canvas: Canvas, currentTimeMillis: Long) {
            for (id in ids) {
                complicationIcons.get(id, null)?.draw(canvas)
            }
            canvas.drawPath(borderPath, tochableBorderPaint)
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
            borderPath = Path().apply { ids.forEach { id -> rect(complicationBounds[id]) } }
            ids.forEach(::setIconBounds)
        }

        fun complicationIdByPoint(x: Int, y: Int): Int? =
            ids.find { id -> complicationBounds[id].contains(x, y) }

        fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
            complicationInfos.put(complicationId, info)
            if (info?.providerIcon != null) {
                info.providerIcon.loadDrawableAsync(
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

    private val unhighlightRunnable: Runnable = object : Runnable {
        override fun run() {
            painter.unhighlight()
            invalidate()
        }
    }

    private fun highlightRect(rect: Rect) {
        painter.highlightRect(rect)
        invalidate()
        handler.removeCallbacks(unhighlightRunnable)
        handler.postDelayed(unhighlightRunnable, 100L)
    }

}