/*
 * Copyright 2019 The Slanted Watchface Authors. All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.mulambda.slantedwatchface

import android.content.res.AssetManager
import android.graphics.Typeface

class Typefaces(assets: AssetManager, val config: Config) {
    val timeTypeface: Typeface = Typeface.createFromAsset(assets, config.assetName)
    val dateTypeface: Typeface = Typeface.DEFAULT

    data class Config(
        val displayName: String, val assetName: String,
        val ySizeRatio: Float,
        val hourScaleX: Float, val minutesScaleX: Float, val secondsScaleX: Float
    )

    companion object {
        val LIMELIGHT =
            Config(
                displayName = "Limelight",
                assetName = "limelight.ttf",
                ySizeRatio = 1f,
                hourScaleX = 0.4f,
                minutesScaleX = 0.33f,
                secondsScaleX = 0.4f
            )
        val VACCINE =
            Config(
                displayName = "Vaccine",
                assetName = "Vaccine.ttf",
                ySizeRatio = 1f,
                hourScaleX = 0.6f,
                minutesScaleX = 0.4f,
                secondsScaleX = 0.4f
            )
        val SWEET_HIPSTER =
            Config(
                displayName = "Sweet Hipster",
                assetName = "sweethipster.ttf",
                ySizeRatio = 1.2f,
                hourScaleX = 0.8f,
                minutesScaleX = 0.7f,
                secondsScaleX = 0.7f
            )

        val DEFAULT = LIMELIGHT
        val ALL = arrayOf(LIMELIGHT, VACCINE, SWEET_HIPSTER)
        fun configByString(displayName: String) =
            ALL.find { c -> displayName.equals(c.displayName) } ?: DEFAULT
    }
}