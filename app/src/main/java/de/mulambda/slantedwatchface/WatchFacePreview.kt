package de.mulambda.slantedwatchface

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
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
    private val complications = ComplicationsPreview()
    private lateinit var watchFaceClipPath: Path
    var onComplicationIdClick: (Int) -> Unit = { _ -> }
    val sharedPreferences = context.getSharedPreferences(
        context.getString(R.string.preference_file_key),
        Context.MODE_PRIVATE
    ).apply {
        registerOnSharedPreferenceChangeListener(this@WatchFacePreview)
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.DKGRAY
        strokeWidth = 4f
        isAntiAlias = true
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        Log.i(TAG(), "onSharedPreferenceChanged")
        initializePainter()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // We provide alternative way of changing complications
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                val rotatedPoint = painter.rotate(event.x.toInt(), event.y.toInt())
                val id = complications.complicationIdByPoint(
                    rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                )
                return id != null
            }
            MotionEvent.ACTION_UP -> {
                val rotatedPoint = painter.rotate(event.x.toInt(), event.y.toInt())
                val id = complications.complicationIdByPoint(
                    rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                )
                if (id != null) {
                    complications.onComplicationIdClick(id)
                    onComplicationIdClick(id)
                    return true
                }

            }
        }
        return super.onTouchEvent(event)
    }

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
        painter.draw(Calendar.getInstance(), canvas)
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
        private val complicationBounds = SparseArray<Rect>(WatchFaceService.Complications.ALL.size)
        private val complicationInfos =
            SparseArray<ComplicationProviderInfo?>(WatchFaceService.Complications.ALL.size)
        private val complicationIcons =
            SparseArray<Drawable?>(WatchFaceService.Complications.ALL.size)
        private val borderPaint = Paint().apply {
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
        private lateinit var borderPath: Path
        private val handler = Handler(Looper.getMainLooper())

        private val unhighlightRunnable: Runnable = object : Runnable {
            override fun run() {
                painter.unhighlight()
                invalidate()
            }
        }

        override val ids: IntArray
            get() = WatchFaceService.Complications.ALL

        override fun isComplicationEmpty(id: Int): Boolean = false

        override fun draw(canvas: Canvas, currentTimeMillis: Long) {
            for (id in ids) {
                complicationIcons.get(id, null)?.draw(canvas)
            }
            canvas.drawPath(borderPath, borderPaint)
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
            painter.highlightRect(complicationBounds[id])
            invalidate()
            handler.removeCallbacks(unhighlightRunnable)
            handler.postDelayed(unhighlightRunnable, 100L)
        }
    }

}