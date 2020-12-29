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
                        listOf(
                            Color.BLACK,
                            Color.DKGRAY,
                            Color.GRAY,
                            Color.LTGRAY,
                            Color.WHITE,
                            Color.RED,
                            Color.GREEN,
                            Color.BLUE,
                            Color.YELLOW,
                            Color.CYAN,
                            Color.MAGENTA,
                            0xFFBB00DD.toInt()
                        )
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