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
/*
import android.annotation.SuppressLint
import android.content.*
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.support.wearable.complications.rendering.ComplicationDrawable
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.util.SparseArray
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.util.*

@SuppressLint("RestrictedApi")
class WatchFaceService : CanvasWatchFaceService() {
    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: Engine) : Handler(Looper.getMainLooper()) {
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


    inner class Engine : CanvasWatchFaceService.Engine(true) {
        private var mRegisteredTimeZoneReceiver = false
        private lateinit var calendar: Calendar
        private var centerX: Float = 0F
        private var centerY: Float = 0F

        private lateinit var veneer: ActiveAmbient<Veneer>

        private lateinit var painter: ActiveAmbient<WatchFacePainter>
        private val complications = ComplicationsHolder()

        private var isAmbient: Boolean = false
        private var lowBitProtection: Boolean = false
        private var burnInProtection: Boolean = false

        /* Handler to update the time once a second in interactive mode. */
        private val updateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@WatchFaceService)
                    .setAcceptsTapEvents(true)
                    .setHideNotificationIndicator(false)
                    .build()
            )

            initialize(loadSavedPreferences())
            setActiveComplications(*Complications.RANGE.toList().toIntArray())
        }

        private fun loadSavedPreferences(): SharedPreferences {
            return applicationContext.getSharedPreferences(
                getString(R.string.preference_file_key),
                MODE_PRIVATE
            )!!
        }

        private fun initialize(sharedPreferences: SharedPreferences) {
            calendar = Calendar.getInstance()

            veneer = ActiveAmbient(
                active = Veneer.fromSharedPreferences(sharedPreferences, assets, false),
                ambient = Veneer.fromSharedPreferences(sharedPreferences, assets, true)
            )
            complications.setColors(veneer.active)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            centerX = width / 2f
            centerY = height / 2f

            initializePainter()
        }

        private fun initializePainter() {
            val bounds = RectF(0f, 0f, centerX * 2, centerY * 2)
            painter = ActiveAmbient(
                active = WatchFacePainter(calendar, veneer.active, bounds, complications),
                ambient = WatchFacePainter(calendar, veneer.ambient, bounds, complications)
            )
            complications.updatePositionsFromPainter()
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
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
            complications.onComplicationDataUpdate(watchFaceComplicationId, data)
            if (painter.get(isAmbient).shouldUpdate(calendar)) {
                initializePainter()
            }

            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            isAmbient = inAmbientMode
            complications.setInAmbientMode(inAmbientMode)

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)

            lowBitProtection = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false)
            burnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false)

            complications.setProperties(lowBitProtection, burnInProtection)
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

        private val handler = Handler(Looper.getMainLooper())
        private val unhighlightRunnable = Runnable {
            painter.get(true).unhighlight()
            painter.get(false).unhighlight()
            invalidate()
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
                    val currentPainter = painter.get(isInAmbientMode)
                    if (currentPainter.isDateTap(calendar, x, y)
                        || currentPainter.isSecondsTap(calendar, x, y)
                    ) {
                        val rect = currentPainter.dateRect(calendar)
                            .apply { union(currentPainter.secondsRect(calendar)) }
                        rect.inset(-4f, -4f)
                        currentPainter.highlightRect(rect.toIntRect())
                        invalidate()
                        handler.removeCallbacks(unhighlightRunnable)
                        handler.postDelayed(unhighlightRunnable, 100L)
                        launchAgenda()
                    } else {
                        complications.performTap(x, y)
                    }
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            calendar.timeInMillis = System.currentTimeMillis()
            canvas.drawColor(Color.BLACK)
            if (painter.get(isInAmbientMode).shouldUpdate(calendar)) {
                Log.i(TAG(), "Recalculating painter")
                initializePainter()
            }
            painter.get(isInAmbientMode).draw(calendar, canvas)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                initialize(loadSavedPreferences())
                initializePainter()
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                calendar.timeZone = TimeZone.getDefault()
                if (painter.get(isInAmbientMode).shouldUpdate(calendar)) {
                    initializePainter()
                }
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
            this@WatchFaceService.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@WatchFaceService.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }

        private inner class ComplicationsHolder : WatchFacePainter.ComplicationsPainter {
            private val complicationData =
                SparseArray<ComplicationData>(Complications.MAX_NUMBER_OF_SLOTS)
            private val complicationBounds = SparseArray<Rect>(Complications.MAX_NUMBER_OF_SLOTS)
            private val complicationDrawables =
                SparseArray<ComplicationDrawable>(Complications.MAX_NUMBER_OF_SLOTS).apply {
                    for (id in Complications.RANGE)
                        put(id, ComplicationDrawable(applicationContext))
                }

            override fun isComplicationEmpty(id: Int): Boolean =
                isEmptyComplicationData(complicationData.get(id))

            private fun isEmptyComplicationData(it: ComplicationData?): Boolean =
                it == null ||
                        it.type == ComplicationData.TYPE_EMPTY ||
                        it.type == ComplicationData.TYPE_NOT_CONFIGURED

            override fun draw(canvas: Canvas, currentTimeMillis: Long) {
                for (id in veneer.active.visibleComplicationIds) {
                    complicationDrawables[id].draw(canvas, currentTimeMillis)
                }
            }

            fun onComplicationDataUpdate(watchFaceComplicationId: Int, data: ComplicationData?) {
                complicationData.put(watchFaceComplicationId, data)
                val complicationDrawable = complicationDrawables.get(watchFaceComplicationId)
                complicationDrawable.setComplicationData(data)
            }

            fun updatePositionsFromPainter() {
                painterForBounds().updateComplicationBounds(complicationBounds)
                for (id in Complications.RANGE) {
                    complicationDrawables[id].bounds = complicationBounds[id]
                }
            }

            fun setInAmbientMode(inAmbientMode: Boolean) {
                for (id in Complications.RANGE) {
                    complicationDrawables[id].setInAmbientMode(inAmbientMode)
                }
                // We assume complication positions are the same in normal and ambient mode.
            }

            fun setProperties(lowBitAmbient: Boolean, burnInProtection: Boolean) {
                for (id in Complications.RANGE) {
                    val complicationDrawable = complicationDrawables.get(id)

                    complicationDrawable.setLowBitAmbient(lowBitAmbient)
                    complicationDrawable.setBurnInProtection(burnInProtection)
                }

            }

            fun setColors(veneer: Veneer) {
                for (id in Complications.RANGE) {
                    complicationDrawables[id].setTextColorActive(veneer.complicationTextColor)
                    complicationDrawables[id].setTextColorAmbient(Veneer.AMBIENT_COLOR)
                    complicationDrawables[id].setIconColorActive(veneer.complicationIconColor)
                    complicationDrawables[id].setTextColorAmbient(Veneer.AMBIENT_COLOR)
                    complicationDrawables[id].setBackgroundColorActive(Color.BLACK)
                    complicationDrawables[id].setBackgroundColorAmbient(Color.BLACK)
                }
            }

            fun performTap(x: Int, y: Int) {
                val rotatedPoint = painterForBounds().rotate(x, y)
                for (id in Complications.RANGE) {
                    performComplicationTap(id, rotatedPoint)
                }
            }

            // To save cycles, assume complication positions are the same
            // in normal and ambient mode.
            // Not a value because painters change on resize etc.
            private fun painterForBounds() = painter.active

            private fun performComplicationTap(
                complicationId: Int,
                rotatedPoint: Pair<Float, Float>
            ) {
                if (complicationBounds[complicationId].contains(
                        rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                    )
                ) {
                    val complicationData = complicationData.get(complicationId)
                    if (complicationData != null &&
                        complicationData.type == ComplicationData.TYPE_NO_PERMISSION
                    ) {
                        requestComplicationsPermission()
                    } else {
                        complicationDrawables[complicationId].onTap(
                            rotatedPoint.first.toInt(), rotatedPoint.second.toInt()
                        )
                    }
                }
            }

            private fun requestComplicationsPermission() {
                applicationContext.startActivity(
                    ComplicationHelperActivity.createPermissionRequestHelperIntent(
                        applicationContext,
                        ComponentName(applicationContext, WatchFaceService::class.java)
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
            }
        }

    }
}

*/