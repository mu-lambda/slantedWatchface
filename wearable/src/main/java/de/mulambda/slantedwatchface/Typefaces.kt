package de.mulambda.slantedwatchface

import android.content.res.AssetManager
import android.graphics.Typeface

class Typefaces(assets: AssetManager, val config: Config) {
    val timeTypeface: Typeface = Typeface.createFromAsset(assets, config.assetName)
    val dateTypeface: Typeface = Typeface.DEFAULT

    data class Config(
        val displayName: String, val assetName: String,
        val hourScaleX: Float, val minutesScaleX: Float, val secondsScaleX: Float
    )

    companion object {
        val LIMELIGHT =
            Config(
                displayName = "Limelight",
                assetName = "limelight.ttf",
                hourScaleX = 0.4f,
                minutesScaleX = 0.33f,
                secondsScaleX = 0.4f
            )
        val VACCINE =
            Config(
                displayName = "Vaccine",
                assetName = "Vaccine.ttf",
                hourScaleX = 0.7f,
                minutesScaleX = 0.4f,
                secondsScaleX = 0.4f
            )
        val REALLYFREE =
            Config(
                displayName = "Really Free",
                assetName = "reallyfree.ttf",
                hourScaleX = 1f,
                minutesScaleX = 0.7f,
                secondsScaleX = 0.7f
            )
        val SWEET_HIPSTER =
            Config(
                displayName = "Sweet Hipster",
                assetName = "sweethipster.ttf",
                hourScaleX = 1f,
                minutesScaleX = 0.7f,
                secondsScaleX = 0.7f
            )

        val DEFAULT = LIMELIGHT
    }
}