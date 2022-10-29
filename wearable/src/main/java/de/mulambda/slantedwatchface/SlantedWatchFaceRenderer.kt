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

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import androidx.core.graphics.toRectF
import androidx.wear.watchface.*
import androidx.wear.watchface.client.InteractiveWatchFaceClient
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.time.ZonedDateTime

// Default for how long each frame is displayed at expected frame rate.
private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class SlantedWatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int
) : Renderer.CanvasRenderer2<SlantedWatchFaceRenderer.SharedAssets>(
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    FRAME_PERIOD_MS_DEFAULT,
    clearWithBackgroundTintBeforeRenderingHighlightLayer = false
) {
    class SharedAssets : Renderer.SharedAssets {
        override fun onDestroy() {
        }
    }

    private val complicationsPainter = object : WatchFacePainter.ComplicationsPainter {
        override fun isComplicationEmpty(id: Int): Boolean = true


        override fun draw(canvas: Canvas, zdt: ZonedDateTime) {
        }

    }

    private var lastZDT = ZonedDateTime.now()
    private lateinit var activeVeneer: Veneer
    private lateinit var ambientVeneer: Veneer
    private lateinit var painter: ActiveAmbient<WatchFacePainter>
    private var lastKnownBounds = Rect()

    override suspend fun createSharedAssets(): SharedAssets = SharedAssets()
    private fun loadSavedPreferences(): SharedPreferences {
        return context.getSharedPreferences(
            context.getString(R.string.preference_file_key),
            MODE_PRIVATE
        )!!
    }

    private val sharedPreferencesChangeListener =
        object : SharedPreferences.OnSharedPreferenceChangeListener {
            override fun onSharedPreferenceChanged(sp: SharedPreferences?, p1: String?) {
                if (sp != null) initializeFromSharedPreferences(sp)
            }
        }

    init {
        val sp = loadSavedPreferences()
        initializeFromSharedPreferences(sp)
        sp.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
    }

    override fun onDestroy() {
        loadSavedPreferences().unregisterOnSharedPreferenceChangeListener(
            sharedPreferencesChangeListener
        )
        super.onDestroy()
    }

    private fun initializeFromSharedPreferences(sp: SharedPreferences) {
        activeVeneer = Veneer.fromSharedPreferences(sp, context.assets, false)
        ambientVeneer = Veneer.fromSharedPreferences(sp, context.assets, true)
        updatePainter()
    }

    private fun updatePainter() {
        painter = ActiveAmbient(
            WatchFacePainter(
                lastZDT, activeVeneer, lastKnownBounds.toRectF(), complicationsPainter
            ),
            WatchFacePainter(
                lastZDT, ambientVeneer, lastKnownBounds.toRectF(), complicationsPainter
            )
        )
    }


    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        lastZDT = zonedDateTime
        if (lastKnownBounds != bounds
            || painter.get(renderParameters.drawMode).shouldUpdate(zonedDateTime)
        ) {
            lastKnownBounds = bounds
            updatePainter()
        }
        canvas.drawColor(Color.BLACK)
        painter.get(renderParameters.drawMode).draw(zonedDateTime, canvas)
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        //TODO("Not yet implemented")
    }

    private val handler = Handler(Looper.getMainLooper())
    private val unhighlightRunnable = Runnable {
        painter.get(DrawMode.AMBIENT).unhighlight()
        painter.get(DrawMode.INTERACTIVE).unhighlight()
        invalidate()
    }

    val tapListener = object : WatchFace.TapListener {
        override fun onTapEvent(
            tapType: Int,
            tapEvent: TapEvent,
            complicationSlot: ComplicationSlot?
        ) {
            when (tapType) {
                InteractiveWatchFaceClient.TAP_TYPE_DOWN -> {}
                InteractiveWatchFaceClient.TAP_TYPE_CANCEL -> {}
                InteractiveWatchFaceClient.TAP_TYPE_UP -> {
                    val currentPainter = painter.get(renderParameters.drawMode)
                    if (currentPainter.isDateTap(
                            lastZDT,
                            tapEvent.xPos,
                            tapEvent.yPos
                        ) || currentPainter.isSecondsTap(lastZDT, tapEvent.xPos, tapEvent.yPos)
                    ) {
                        val rect = currentPainter.dateRect(lastZDT)
                            .apply { union(currentPainter.secondsRect(lastZDT)) }
                        rect.inset(-4f, -4f)
                        currentPainter.highlightRect(rect.toIntRect())
                        invalidate()
                        handler.removeCallbacks(unhighlightRunnable)
                        handler.postDelayed(unhighlightRunnable, 100L)
                        launchAgenda()
                    }
                }
            }
        }

    }

    private fun launchAgenda() {
        context.startActivity(Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_APP_CALENDAR)
            flags =
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
}