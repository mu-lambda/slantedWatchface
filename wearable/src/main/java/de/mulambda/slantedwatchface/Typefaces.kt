/*
 *    Copyright (c) 2021 - present The Slanted Watch Face Authors
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
        val SWEET_HIPSTER =
            Config(
                displayName = "Sweet Hipster",
                assetName = "sweethipster.ttf",
                ySizeRatio = 1.3f,
                hourScaleX = 0.9f,
                minutesScaleX = 0.7f,
                secondsScaleX = 0.6f
            )
       val GRAFIK_TEXT =
            Config(
                displayName = "Grafik Text",
                assetName = "GrafikText.ttf",
                ySizeRatio = 1f,
                hourScaleX = 0.5f,
                minutesScaleX = 0.5f,
                secondsScaleX = 0.5f
            )
        val FENWICK_WOODTYPE =
            Config(
                displayName = "Fenwick Woodtype",
                assetName = "FenwickWoodtype.ttf",
                ySizeRatio = 1.0f,
                hourScaleX = 0.7f,
                minutesScaleX = 0.6f,
                secondsScaleX = 0.5f
            )
        val ORDINARY =
            Config(
                displayName = "Ordinary",
                assetName = "Ordinary.ttf",
                ySizeRatio = 1.2f,
                hourScaleX = 0.6f,
                minutesScaleX = 0.5f,
                secondsScaleX = 0.5f
            )
        val BEBAS_KAI =
            Config(
                displayName = "Bebas Kai",
                assetName = "BebasKai.ttf",
                ySizeRatio = 1.0f,
                hourScaleX = 0.6f,
                minutesScaleX = 0.5f,
                secondsScaleX = 0.5f
            )

        val DEFAULT = LIMELIGHT
        val ALL = arrayOf(LIMELIGHT, SWEET_HIPSTER, GRAFIK_TEXT, FENWICK_WOODTYPE, ORDINARY, BEBAS_KAI)
        fun configByString(displayName: String) =
            ALL.find { c -> displayName == c.displayName } ?: DEFAULT
    }
}