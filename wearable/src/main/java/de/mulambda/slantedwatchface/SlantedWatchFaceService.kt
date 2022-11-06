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

import android.graphics.RectF
import android.view.SurfaceHolder
import androidx.wear.watchface.*
import androidx.wear.watchface.complications.ComplicationSlotBounds
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema

class SlantedWatchFaceService : WatchFaceService() {
    override fun createUserStyleSchema(): UserStyleSchema {
        return super.createUserStyleSchema()
    }

    override fun createComplicationSlotsManager(currentUserStyleRepository: CurrentUserStyleRepository): ComplicationSlotsManager {
        val defaultCanvasComplicationFactory =
            CanvasComplicationFactory { watchState, listener ->
                CanvasComplicationDrawable(
                    ComplicationDrawable(applicationContext),
                    watchState,
                    listener
                )
            }

        return ComplicationSlotsManager(buildList {
            for (id in Complications.RANGE) {
                add(
                    ComplicationSlot.createRoundRectComplicationSlotBuilder(
                        id = id,
                        canvasComplicationFactory = defaultCanvasComplicationFactory,
                        supportedTypes = listOf(
                            ComplicationType.SHORT_TEXT,
                            ComplicationType.SMALL_IMAGE
                        ),
                        defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
                            SystemDataSources.DATA_SOURCE_SUNRISE_SUNSET,
                            ComplicationType.SHORT_TEXT
                        ),
                        bounds = ComplicationSlotBounds(RectF(0.1f, 0.1f, 0.2f, 0.2f))
                    ).build()
                )
            }
        }, currentUserStyleRepository)
    }

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {

        val renderer = SlantedWatchFaceRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            complicationSlotsManager = complicationSlotsManager,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = CanvasType.HARDWARE
        )
        return WatchFace(watchFaceType = WatchFaceType.DIGITAL, renderer = renderer).setTapListener(
            renderer.tapListener
        )
    }
}