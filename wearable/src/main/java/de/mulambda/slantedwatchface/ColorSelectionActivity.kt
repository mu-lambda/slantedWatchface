/*
 *    Copyright (c) 2021 - present The Slanted Watchface Authors
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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.jraf.android.androidwearcolorpicker.ColorPickActivity

class ColorSelectionActivity : Activity() {
    companion object {
        const val REQUEST_PICK_COLOR = 1001
        const val ORIGINAL_COLOR = "original-color"
        const val NAME = "name"
        const val RESULT = "picked-color"

        fun getResult(intent: Intent): Int = intent.getIntExtra(RESULT, Color.GREEN)
    }

    private var pickedColor: Int = Color.GREEN

    private val list = listOf(
        Color.BLACK,
        Color.DKGRAY,
        Color.GRAY,
        Color.LTGRAY,
        Color.WHITE,
        Color.RED,
        0xFF_FF_A5_00.toInt(), // Orange
        Color.YELLOW,
        Color.GREEN,
        Color.CYAN,
        Color.BLUE,
        Color.MAGENTA,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.color_selection_activity)
        pickColor(intent?.getIntExtra(ORIGINAL_COLOR, Color.GREEN) ?: Color.GREEN)
        findViewById<TextView>(R.id.color_description).text = intent?.getStringExtra(NAME) ?: ""

        findViewById<Button>(R.id.apply_picked_color).setOnClickListener {
            Log.i(TAG(), "Color button clicked")
            setResult(RESULT_OK, Intent().putExtra(RESULT, pickedColor))
            finish()
        }


        // "Rainbow" mode
        findViewById<Button>(R.id.pick_color_shades).setOnClickListener {
            startActivityForResult(
                ColorPickActivity.IntentBuilder()
                    .oldColor(pickedColor)
                    .build(this),
                REQUEST_PICK_COLOR
            )
        }

        // Specific colors mode
        findViewById<Button>(R.id.pick_color_rainbow).setOnClickListener {
            startActivityForResult(
                ColorPickActivity.IntentBuilder()
                    .oldColor(pickedColor)
                    .colors(
                        list
                    )
                    .build(this),
                REQUEST_PICK_COLOR
            )
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_PICK_COLOR && resultCode == RESULT_OK) {
            pickColor(ColorPickActivity.getPickedColor(data!!))
        }
    }

    private fun pickColor(color: Int) {
        pickedColor = color
        findViewById<View>(R.id.picked_color_view).setBackgroundColor(pickedColor)
    }
}