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
import android.util.SparseArray
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.util.*

class WatchFace : CanvasWatchFaceService() {
    private val TAG = WatchFace::class.qualifiedName!!

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

        private lateinit var mVeneer: NormalAmbient<Veneer>

        private lateinit var mPainter: NormalAmbient<WatchFacePainter>
        private lateinit var mComplications: ComplicationsHolder

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
                WatchFaceStyle.Builder(this@WatchFace)
                    .setAcceptsTapEvents(true)
                    .setShowUnreadCountIndicator(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()
            mTypeface =
                Typeface.createFromAsset(this@WatchFace.assets, "limelight.ttf")
            mVeneer = NormalAmbient(
                normal = Veneer(
                    angle = Constants.ANGLE,
                    typeface = mTypeface,
                    hoursColor = Constants.HOURS_COLOR,
                    minutesColor = Constants.MINUTES_COLOR,
                    secondsColor = Constants.SECONDS_COLOR,
                    dateColor = Constants.DATE_COLOR,
                    isAmbient = false,
                ),
                ambient = Veneer(
                    angle = Constants.ANGLE,
                    typeface = mTypeface,
                    hoursColor = Color.WHITE,
                    minutesColor = Color.WHITE,
                    secondsColor = Color.WHITE,
                    dateColor = Color.WHITE,
                    isAmbient = true,
                )
            )

            mComplications = ComplicationsHolder()
            setActiveComplications(*mComplications.ids)
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

            val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            mPainter = NormalAmbient(
                normal = WatchFacePainter(mVeneer.normal, bounds, mComplications),
                ambient = WatchFacePainter(mVeneer.ambient, bounds, mComplications)
            )
            mComplications.updatePositions()
            mComplications.setColors()
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onComplicationDataUpdate(
            watchFaceComplicationId: Int, data: ComplicationData?
        ) {
            super.onComplicationDataUpdate(watchFaceComplicationId, data)
            mComplications.onComplicationDataUpdate(watchFaceComplicationId, data)

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode
            mComplications.setInAmbientMode(inAmbientMode)

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false)

            mComplications.setProperties(mLowBitAmbient, mBurnInProtection)
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
                    if (mPainter.get(isInAmbientMode).isDateAreaTap(mCalendar, x, y)) {
                        launchAgenda()
                    } else {
                        mComplications.performTap(x, y)
                    }
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            mCalendar.timeInMillis = System.currentTimeMillis()
            canvas.drawColor(Color.BLACK)
            mPainter.get(isInAmbientMode).draw(mCalendar, canvas)
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
            this@WatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WatchFace.unregisterReceiver(mTimeZoneReceiver)
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

        private inner class ComplicationsHolder : WatchFacePainter.Complications {
            private val TAG = ComplicationsHolder::class.qualifiedName
            private val mComplicationData = SparseArray<ComplicationData>(Complications.ALL.size)
            private val mComplicationBounds = SparseArray<Rect>(Complications.ALL.size)
            private val mComplicationDrawables =
                SparseArray<ComplicationDrawable>(Complications.ALL.size).apply {
                    for (id in Complications.ALL)
                        put(id, ComplicationDrawable(applicationContext))
                }

            override val ids: IntArray = Complications.ALL

            override fun isComplicationEmpty(id: Int): Boolean =
                isEmptyComplicationData(mComplicationData.get(id))

            private fun isEmptyComplicationData(it: ComplicationData?) =
                it == null || it.type == ComplicationData.TYPE_EMPTY

            override fun draw(canvas: Canvas, currentTimeMillis: Long) {
                for (id in ids) {
                    mComplicationDrawables[id].draw(canvas, currentTimeMillis)
                }
            }

            fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData?) {
                val shouldUpdatePositions =
                    isComplicationEmpty(watchFaceComplicationId) != isEmptyComplicationData(data)
                mComplicationData.put(watchFaceComplicationId, data)
                if (shouldUpdatePositions) {
                    updatePositions()
                }
                val complicationDrawable = mComplicationDrawables.get(watchFaceComplicationId)
                complicationDrawable.setComplicationData(data)
            }

            fun updatePositions() {
                painterForBounds().updateComplicationBounds(mComplicationBounds)
                for (id in Complications.ALL) {
                    mComplicationDrawables[id].bounds = mComplicationBounds[id]
                }
            }

            fun setInAmbientMode(inAmbientMode: Boolean) {
                for (id in Complications.ALL) {
                    mComplicationDrawables[id].setInAmbientMode(inAmbientMode)
                }
                // We assume complication positions are the same in normal and ambient mode.
            }

            fun setProperties(lowBitAmbient: Boolean, burnInProtection: Boolean) {
                for (id in Complications.ALL) {
                    val complicationDrawable = mComplicationDrawables.get(id)

                    complicationDrawable.setLowBitAmbient(lowBitAmbient)
                    complicationDrawable.setBurnInProtection(burnInProtection)
                }

            }

            fun setColors() {
                for (id in Complications.ALL) {
                    mComplicationDrawables[id].setTextColorActive(Color.GREEN)
                    mComplicationDrawables[id].setTextColorAmbient(Color.WHITE)
                    mComplicationDrawables[id].setBackgroundColorActive(Color.BLACK)
                    mComplicationDrawables[id].setBackgroundColorAmbient(Color.BLACK)
                }
            }

            fun performTap(x: Int, y: Int) {
                val rotatedPoint = painterForBounds().rotate(x, y)
                for (id in Complications.ALL) {
                    performComplicationTap(id, rotatedPoint)
                }
            }

            // To save cycles, assume complication positions are the same
            // in normal and ambient mode.
            // Not a value because painters change on resize etc.
            private fun painterForBounds() = mPainter.normal

            private fun performComplicationTap(
                complicationId: Int,
                rotatedPoint: Pair<Float, Float>
            ) {
                if (mComplicationBounds[complicationId].contains(
                        rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                    )
                ) {
                    val complicationData = mComplicationData.get(complicationId)
                    if (complicationData != null &&
                        complicationData.type == ComplicationData.TYPE_NO_PERMISSION
                    ) {
                        requestComplicationsPermission()
                    } else {
                        mComplicationDrawables[complicationId].onTap(
                            rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                        )
                    }
                }
            }

            private fun requestComplicationsPermission() {
                applicationContext.startActivity(
                    ComplicationHelperActivity.createPermissionRequestHelperIntent(
                        applicationContext,
                        ComponentName(applicationContext, WatchFace::class.java)
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
            }
        }

    }
}