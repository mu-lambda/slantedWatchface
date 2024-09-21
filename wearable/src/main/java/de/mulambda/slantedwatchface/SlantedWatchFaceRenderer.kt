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
import android.util.Log
import android.util.SparseArray
import android.view.SurfaceHolder
import androidx.core.graphics.toRectF
import androidx.wear.watchface.*
import androidx.wear.watchface.client.InteractiveWatchFaceClient
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyle
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.ZonedDateTime

// Default for how long each frame is displayed at expected frame rate.
private const val FRAME_PERIOD_MS_DEFAULT: Long = 16L

class SlantedWatchFaceRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val complicationSlotsManager: ComplicationSlotsManager,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
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
        override fun isComplicationEmpty(id: Int): Boolean {
            val complicationSlot = complicationSlotsManager.complicationSlots[id]
            if (complicationSlot != null) {
                return complicationSlot.complicationData.value.type == ComplicationType.EMPTY
            } else {
                return true
            }
        }


        override fun draw(canvas: Canvas, zdt: ZonedDateTime) {
            for ((_, complication) in complicationSlotsManager.complicationSlots) {
                if (complication.enabled) {
                    complication.render(canvas, zdt, RenderParameters.DEFAULT_INTERACTIVE)
                }
            }
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
        SharedPreferences.OnSharedPreferenceChangeListener { sp, _ ->
            if (sp != null) initializeFromSharedPreferences(sp)
        }
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        val sp = loadSavedPreferences()
        initializeFromSharedPreferences(sp)
        sp.registerOnSharedPreferenceChangeListener(sharedPreferencesChangeListener)
        complicationSlotsManager.listenForStyleChanges(scope)
    }

    override fun onDestroy() {
        loadSavedPreferences().unregisterOnSharedPreferenceChangeListener(
            sharedPreferencesChangeListener
        )
        super.onDestroy()
    }

    private fun updateComplicationBounds() {
        val bounds = SparseArray<Rect>()
        val currentPainter = painter.get(renderParameters.drawMode)
        currentPainter.updateComplicationBounds(bounds)
/*
        val l = buildList<UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotsOption> {
            for (id in Complications.RANGE) {
                val b = bounds[id]
                if (b != null) {
                    var newBounds = divide(b, currentPainter.bounds)

                    val complicationSlotOverlay: UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay =
                        UserStyleSetting.ComplicationSlotsUserStyleSetting.ComplicationSlotOverlay.Builder(
                            id
                        ).setEnabled(true)
                            .setComplicationSlotBounds(ComplicationSlotBounds(newBounds)).build()
                    add(complicationSlotOverlay)

                }
            }
        }
        UserStyleSetting.ComplicationSlotsUserStyleSetting(
            UserStyleSetting.Id("complication_style_setting"),
            context.resources,
            0,
            0,
            icon = null,
            complicationConfig = l,
            listOf(WatchFaceLayer.COMPLICATIONS)
            )

        // currentUserStyleRepository.updateUserStyle(UserStyleSetting.ComplicationSlotsUserStyleSetting.)

 */
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
        validatePainter(zonedDateTime, bounds)
        canvas.drawColor(Color.BLACK)
        //painter.get(renderParameters.drawMode).draw(zonedDateTime, canvas)
        painter.get(renderParameters.drawMode).drawComplications(zonedDateTime, canvas)
    }

    private fun validatePainter(zonedDateTime: ZonedDateTime, bounds: Rect) {
        lastZDT = zonedDateTime
        if (lastKnownBounds != bounds
            || painter.get(renderParameters.drawMode).shouldUpdate(zonedDateTime)
        ) {
            Log.i(TAG(), "painter updated")
            lastKnownBounds = bounds
            updatePainter()
        }
    }

    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        sharedAssets: SharedAssets
    ) {
        validatePainter(zonedDateTime, bounds)
        painter.get(renderParameters.drawMode).drawComplications(zonedDateTime, canvas)
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