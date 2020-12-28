package de.mulambda.slantedwatchface

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.support.wearable.complications.ComplicationProviderInfo
import android.util.AttributeSet
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
) : View(context, attrs) {
    private val TAG = this::class.qualifiedName
    private val veneer = Veneer(
        angle = WatchFace.Constants.ANGLE,
        typeface = Typeface.createFromAsset(context.assets, "limelight.ttf"),
        hoursColor = WatchFace.Constants.HOURS_COLOR,
        minutesColor = WatchFace.Constants.MINUTES_COLOR,
        secondsColor = WatchFace.Constants.SECONDS_COLOR,
        dateColor = WatchFace.Constants.DATE_COLOR,
        isAmbient = false
    )
    private lateinit var painter: WatchFacePainter
    private lateinit var complications: ComplicationsPreview
    var onComplicationIdClick: (Int) -> Unit = { _ -> }


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
        val dim = watchfaceSize()
        complications = ComplicationsPreview()
        painter = WatchFacePainter(
            veneer,
            RectF(
                paddingLeft.toFloat(), paddingTop.toFloat(),
                (paddingLeft + dim).toFloat(),
                (paddingTop + dim).toFloat()
            ),
            complications
        )
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
        val dim = watchfaceSize().toFloat()
        canvas.save()
        canvas.clipPath(Path().apply {
            addCircle(paddingLeft + dim / 2, paddingTop + dim / 2, dim / 2 + 2, Path.Direction.CW)
        })
        canvas.drawColor(Color.BLACK)
        painter.draw(Calendar.getInstance(), canvas)
        canvas.restore()

    }

    fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
        complications.setComplication(complicationId, info)
    }

    inner class ComplicationsPreview : WatchFacePainter.Complications {
        private val complicationBounds = SparseArray<Rect>(WatchFace.Complications.ALL.size)
        private val complicationInfos =
            SparseArray<ComplicationProviderInfo?>(WatchFace.Complications.ALL.size)
        private val complicationIcons = SparseArray<Drawable?>(WatchFace.Complications.ALL.size)
        private val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(4f, 2f), 0f)
        }
        private val highlightPaint = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.FILL
        }
        private val handler = Handler(Looper.getMainLooper())
        private var highlightedId: Int? = null
        private val unhighlightRunnable: Runnable = object : Runnable {
            override fun run() {
                highlightedId = null
                invalidate()
            }
        }


        override val ids: IntArray
            get() = WatchFace.Complications.ALL

        override fun isComplicationEmpty(id: Int): Boolean = false

        override fun draw(canvas: Canvas, currentTimeMillis: Long) {
            val path = Path()
            highlightedId?.let {
                canvas.drawPath(Path().rect(complicationBounds[it]), highlightPaint)
            }
            for (id in ids) {
                val rect = complicationBounds[id]
                path.rect(rect)
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
                    drawable.draw(canvas)
                }
            }
            canvas.drawPath(path, borderPaint)
        }

        private fun Path.rect(rect: Rect): Path {
            moveTo(rect.left.toFloat(), rect.top.toFloat())
            lineTo(rect.right.toFloat(), rect.top.toFloat())
            lineTo(rect.right.toFloat(), rect.bottom.toFloat())
            lineTo(rect.left.toFloat(), rect.bottom.toFloat())
            lineTo(rect.left.toFloat(), rect.top.toFloat())
            return this
        }

        fun updateComplicationLocations() {
            painter.updateComplicationBounds(complicationBounds)
        }

        fun complicationIdByPoint(x: Int, y: Int): Int? =
            ids.find { id -> complicationBounds[id].contains(x, y) }

        fun setComplication(complicationId: Int, info: ComplicationProviderInfo?) {
            complicationInfos.put(complicationId, info)
            if (info?.providerIcon != null) {
                info.providerIcon.loadDrawableAsync(
                    context,
                    { drawable -> complicationIcons.put(complicationId, drawable); invalidate() },
                    handler
                )
            } else {
                complicationIcons.put(complicationId, null)
                invalidate()
            }
        }

        fun onComplicationIdClick(id: Int) {
            highlightedId = id
            invalidate()
            handler.removeCallbacks(unhighlightRunnable)
            handler.postDelayed(unhighlightRunnable, 100L)
        }
    }

}