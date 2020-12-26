package de.mulambda.slantedwatchface

import android.content.*
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.TextPaint
import android.util.Log
import android.util.SparseArray
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.util.*

class SlantedWatchface : CanvasWatchFaceService() {
    private val TAG = SlantedWatchface::class.qualifiedName!!

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: Engine) : Handler() {
        private val mWeakReference: WeakReference<Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    data class PaintData(
        var hours: String,
        var hoursX: Float, val hoursY: Float,
        var minutes: String,
        var minutesX: Float, val minutesY: Float,
        var seconds: String,
        var secondsX: Float, val secondsY: Float,
        var date: String,
        var dateX: Float, val dateY: Float
    )

    companion object {
        /**
         * Updates rate in milliseconds for interactive mode. We update once a second to advance the
         * second hand.
         */
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000

        /**
         * Handler message id for updating the time periodically in interactive mode.
         */
        private const val MSG_UPDATE_TIME = 0
    }

    object Complications {
        const val TOP = 1001
        const val BOTTOM = 1002
        val ALL = intArrayOf(TOP, BOTTOM)
    }

    object Constants {
        const val GOLDEN = 1.61803398875f
        const val RATIO = GOLDEN
        const val ANGLE = 30f
        const val HOURS_COLOR = Color.GREEN
        const val MINUTES_COLOR = Color.WHITE
        const val SECONDS_COLOR = Color.GREEN
        const val DATE_COLOR = Color.YELLOW
    }


    inner class Engine : CanvasWatchFaceService.Engine() {

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private lateinit var mCalendar: Calendar
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private lateinit var mHoursPaint: NormalAmbient<TextPaint>
        private lateinit var mMinutesPaint: NormalAmbient<TextPaint>
        private lateinit var mSecondsPaint: NormalAmbient<TextPaint>
        private lateinit var mDatePaint: NormalAmbient<TextPaint>
        private lateinit var mBoundsProvider: NormalAmbient<BoundsProvider>


        private lateinit var mComplicationData: SparseArray<ComplicationData>
        private lateinit var mComplicationDrawables: SparseArray<ComplicationDrawable>
        private lateinit var mComplicationBounds: SparseArray<Rect>

        private lateinit var mTypeface: Typeface

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@SlantedWatchface)
                    .setAcceptsTapEvents(true)
                    .setShowUnreadCountIndicator(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()
            mTypeface =
                Typeface.createFromAsset(this@SlantedWatchface.assets, "limelight.ttf")

            initializeComplications()
            initializeWatchFace()
        }

        private fun initializeComplications() {
            mComplicationData = SparseArray(Complications.ALL.size)
            mComplicationBounds = SparseArray(Complications.ALL.size)
            mComplicationDrawables = SparseArray(Complications.ALL.size)
            for (id in Complications.ALL) {
                mComplicationDrawables.put(id, ComplicationDrawable(applicationContext))
            }
            setActiveComplications(*Complications.ALL)
        }

        override fun onComplicationDataUpdate(
            watchFaceComplicationId: Int, data: ComplicationData?
        ) {
            Log.i(TAG, "onComplicationDataUpdate: ${watchFaceComplicationId} data = ${data}")
            super.onComplicationDataUpdate(watchFaceComplicationId, data)

            val shouldUpdatePositions =
                isComplicationEmpty(watchFaceComplicationId) != isEmptyComplicationData(data)
            mComplicationData.put(watchFaceComplicationId, data)
            Log.i(TAG, "onComplicationDataUpdate: shouldUpdatePositions=${shouldUpdatePositions}")
            if (shouldUpdatePositions) {
                updateComplicationPositions()
            }
            val complicationDrawable = mComplicationDrawables.get(watchFaceComplicationId)
            complicationDrawable.setComplicationData(data)
            invalidate()
        }

        private fun initializeWatchFace() {
            val hoursSize = mCenterY * 2
            val hoursPaint = TextPaint().apply {
                typeface = mTypeface
                textSize = hoursSize
                textScaleX = 0.4f
            }
            val minutesSize = hoursSize / Constants.RATIO
            val minutesPaint = TextPaint().apply {
                typeface = mTypeface
                textSize = minutesSize
                textScaleX = 0.33f
            }
            val secondsSize = minutesSize / 2
            val secondsPaint = TextPaint().apply {
                typeface = mTypeface
                textSize = secondsSize
                textScaleX = 0.4f
            }
            val dateSize = secondsSize / 3
            val datePaint = TextPaint().apply {
                typeface = mTypeface
                textSize = dateSize
                textScaleX = 0.5f
            }

            mHoursPaint = NormalAmbient(
                normal = TextPaint(hoursPaint).apply {
                    color = Constants.HOURS_COLOR
                    isAntiAlias = true
                },
                ambient = TextPaint(hoursPaint).apply {
                    color = Color.WHITE
                    isAntiAlias = false
                }
            )
            mMinutesPaint = NormalAmbient(
                normal = TextPaint(minutesPaint).apply {
                    color = Constants.MINUTES_COLOR
                    isAntiAlias = true
                },
                ambient = TextPaint(minutesPaint).apply {
                    color = Color.WHITE
                    isAntiAlias = false
                }
            )
            mSecondsPaint = NormalAmbient(
                normal = TextPaint(secondsPaint).apply {
                    color = Constants.SECONDS_COLOR
                    isAntiAlias = true
                },
                ambient = TextPaint(secondsPaint).apply {
                    color = Color.WHITE
                    isAntiAlias = false
                }
            )
            mDatePaint = NormalAmbient(
                normal = TextPaint(datePaint).apply {
                    color = Constants.DATE_COLOR
                    isAntiAlias = true
                },
                ambient = TextPaint(datePaint).apply {
                    color = Color.WHITE
                    isAntiAlias = true
                }
            )
            mBoundsProvider = NormalAmbient(
                normal = BoundsProvider(
                    mCalendar,
                    mHoursPaint.normal,
                    mMinutesPaint.normal,
                    mSecondsPaint.normal,
                    mDatePaint.normal
                ),
                ambient = BoundsProvider(
                    mCalendar,
                    mHoursPaint.ambient,
                    mMinutesPaint.ambient,
                    mSecondsPaint.ambient,
                    mDatePaint.ambient
                )
            )
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode
            for (id in Complications.ALL) {
                mComplicationDrawables[id].setInAmbientMode(inAmbientMode)
            }

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false)

            for (id in Complications.ALL) {
                val complicationDrawable = mComplicationDrawables.get(id)

                complicationDrawable.setLowBitAmbient(mLowBitAmbient)
                complicationDrawable.setBurnInProtection(mBurnInProtection)
            }
        }


        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            initializeWatchFace()
            initializeComplicationAppearance()
        }

        private fun initializeComplicationAppearance() {
            updateComplicationPositions()

            for (id in Complications.ALL) {
                mComplicationDrawables[id].setTextColorActive(Color.GREEN)
                mComplicationDrawables[id].setTextColorAmbient(Color.WHITE)
                mComplicationDrawables[id].setBackgroundColorActive(Color.BLACK)
                mComplicationDrawables[id].setBackgroundColorAmbient(Color.BLACK)
            }
        }

        private fun updateComplicationPositions() {
            val boundsProvider = mBoundsProvider.get(true)

            val largeInset = 10f
            val maxHoursHeight = boundsProvider.calculateMaxHoursHeight()
            val maxMinutesHeight = boundsProvider.calculateMaxMinutesHeight()
            val hoursY = mCenterY + maxHoursHeight / 2
            val minutesX = mCenterX + largeInset
            val minutesY = hoursY - maxHoursHeight + maxMinutesHeight

            val complicationAreaLeft = minutesX
            val complicationAreaTop = minutesY + largeInset
            val complicationAreaRight = mCenterX * 2
            val complicationAreaBottom = hoursY

            var nonEmptyComplications = 0
            for (id in Complications.ALL) {
                if (!isComplicationEmpty(id)) {
                    nonEmptyComplications++
                }
            }
            val emptyRect = Rect()
            if (nonEmptyComplications == 0) {
                for (id in Complications.ALL) mComplicationBounds.put(id, emptyRect)
            } else {
                val inset = if (nonEmptyComplications > 1) 1 else 0
                val delta = (complicationAreaBottom - complicationAreaTop) / nonEmptyComplications
                var indexOfNonEmpty = 0
                for (id in Complications.ALL) {
                    if (isComplicationEmpty(id)) {
                        mComplicationBounds.put(id, emptyRect)
                        continue
                    }
                    val top = complicationAreaTop + delta * indexOfNonEmpty
                    mComplicationBounds.put(
                        id, Rect(
                            complicationAreaLeft.toInt(),
                            top.toInt(),
                            complicationAreaRight.toInt(),
                            (top + delta).toInt() - inset
                        )
                    )
                    indexOfNonEmpty++
                }
            }

            for (id in Complications.ALL) {
                mComplicationDrawables[id].bounds = mComplicationBounds[id]
            }
        }

        private fun isComplicationEmpty(id: Int): Boolean =
            mComplicationData.get(id).let {
                isEmptyComplicationData(it)
            }

        private fun isEmptyComplicationData(it: ComplicationData?) =
            it == null || it.type == ComplicationData.TYPE_EMPTY

        private fun isDateAreaTap(x: Int, y: Int): Boolean {
            val p = calculatePaintData()
            val (_, secondsBounds) = mBoundsProvider.get(isInAmbientMode).getSeconds(mCalendar)
            val secondsRect = Rect(
                p.secondsX.toInt(),
                p.secondsY.toInt() - secondsBounds.second,
                p.secondsX.toInt() + secondsBounds.first,
                p.secondsY.toInt()
            )
            val (_, dateBounds) = mBoundsProvider.get(isInAmbientMode).getDate(mCalendar)
            val dateRect = Rect(
                p.dateX.toInt(),
                p.dateY.toInt() - dateBounds.second,
                p.dateX.toInt() + dateBounds.first,
                p.dateY.toInt()
            )
            dateRect.union(secondsRect)
            return dateRect.contains(x, y)
        }

        private fun launchAgenda() {
            startActivity(Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_APP_CALENDAR)
                flags =
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // Rotate a point
                    val dx: Float = x - mCenterX
                    val dy: Float = y - mCenterY
                    val a = -Constants.ANGLE / 180f * Math.PI
                    val dx1 = dx * Math.cos(a) - dy * Math.sin(a)
                    val dy1 = dx * Math.sin(a) + dy * Math.cos(a)
                    val x1 = (mCenterX + dx1).toInt()
                    val y1 = (mCenterY + dy1).toInt()

                    if (isDateAreaTap(x1, y1)) {
                        launchAgenda()
                    } else {
                        for (id in Complications.ALL) {
                            performComplicationTap(id, x1, y1)
                        }
                    }
                }
            }
            invalidate()
        }

        private fun performComplicationTap(complicationId: Int, x: Int, y: Int) {
            if (mComplicationBounds[complicationId].contains(x, y)) {
                val complicationData = mComplicationData.get(complicationId)
                if (complicationData != null &&
                    complicationData.type == ComplicationData.TYPE_NO_PERMISSION
                ) {
                    startActivity(
                        ComplicationHelperActivity.createPermissionRequestHelperIntent(
                            applicationContext,
                            ComponentName(applicationContext, this@SlantedWatchface::class.java)
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        })
                } else {
                    mComplicationDrawables[complicationId].onTap(x, y)
                }
            }
        }

        private fun calculatePaintData(): PaintData {
            val (hours, hoursDim) = mBoundsProvider.get(isInAmbientMode).getHours(mCalendar)
            val (minutes, minutesDim) = mBoundsProvider.get(isInAmbientMode).getMinutes(mCalendar)
            val (seconds, _) = mBoundsProvider.get(isInAmbientMode).getSeconds(mCalendar)
            val (date, dateDim) = mBoundsProvider.get(isInAmbientMode).getDate(mCalendar)

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

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            canvas.drawColor(Color.BLACK)
            canvas.rotate(Constants.ANGLE, mCenterX, mCenterY)

            with(calculatePaintData()) {
                canvas.drawText(
                    hours,
                    hoursX, hoursY,
                    mHoursPaint.get(isInAmbientMode)
                )

                canvas.drawText(
                    minutes,
                    minutesX,
                    minutesY, mMinutesPaint.get(isInAmbientMode)
                )

                canvas.drawText(date, dateX, dateY, mDatePaint.get(isInAmbientMode))

                if (!isInAmbientMode) {
                    canvas.drawText(
                        seconds,
                        secondsX, secondsY,
                        mSecondsPaint.get(isInAmbientMode)
                    )
                }

                for (id in Complications.ALL) {
                    mComplicationDrawables[id].draw(canvas, now)
                }
            }
            canvas.save()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@SlantedWatchface.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@SlantedWatchface.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}