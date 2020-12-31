package de.mulambda.slantedwatchface

import android.content.res.AssetManager
import android.graphics.Typeface

class Typefaces(assets: AssetManager) {
    val timeTypeface: Typeface = Typeface.createFromAsset(assets, "limelight.ttf")
    val dateTypeface: Typeface = Typeface.DEFAULT
}