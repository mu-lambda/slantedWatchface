/*
 * Copyright 2021-present The Slanted Watchface Authors. All rights reserved.
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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView

class TypefaceSelectionActivity : Activity() {
    private lateinit var contentView: WearableRecyclerView
    private val adapter = Adapter()
    private lateinit var originalTypeface: String

    companion object {
        const val TYPEFACE = "typeface"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.font_selection_activity)

        originalTypeface = intent?.getStringExtra(TYPEFACE) ?: Typefaces.DEFAULT.displayName

        contentView = findViewById(R.id.font_selection_activity)
        contentView.apply {
            layoutManager = WearableLinearLayoutManager(this@TypefaceSelectionActivity)
            isEdgeItemsCenteringEnabled = true
            setHasFixedSize(true)
            adapter = this@TypefaceSelectionActivity.adapter
        }
        contentView.smoothScrollToPosition(
            Typefaces.ALL.indexOfFirst { c -> c.displayName == originalTypeface } + 1)
    }

   inner class FontHolder(parent: ViewGroup, typefaceIndex: Int) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.typeface_button, parent, false)
    ), View.OnClickListener {
        val config = Typefaces.ALL[typefaceIndex]

        init {
            (itemView as RadioButton).apply {
                typeface = Typefaces(assets, config).timeTypeface
                textSize *= config.ySizeRatio
                textScaleX = config.hourScaleX
                isChecked = config.displayName == originalTypeface
                setOnClickListener(this@FontHolder)
            }
        }

        override fun onClick(v: View?) {
            setResult(RESULT_OK, Intent().putExtra(TYPEFACE, config.displayName))
            finish()
        }
    }

    inner class Adapter : RecyclerView.Adapter<FontHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FontHolder {
            return FontHolder(parent, viewType)
        }

        override fun onBindViewHolder(holder: FontHolder, position: Int) {
            // Do nothing.
        }

        override fun getItemCount(): Int = Typefaces.ALL.size
        override fun getItemViewType(position: Int): Int = position
    }
}