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

//import android.support.wearable.complications.ComplicationHelperActivity
//import android.support.wearable.complications.ProviderInfoRetriever
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView

class ConfigActivity : Activity() {
    companion object {
        val REQUESTS_COMPLICATIONS = Complications.RANGE
        private fun requestCodeOf(complicationId: Int) =
            if (complicationId in Complications.RANGE) complicationId
            else throw UnsupportedOperationException("complicationId = $complicationId")

        const val REQUEST_PICK_COLOR_THEME = Complications.BOTTOM + 1

        const val REQUEST_PICK_HOURS_COLOR = Complications.BOTTOM + 2
        const val REQUEST_PICK_MINUTES_COLOR = Complications.BOTTOM + 3
        const val REQUEST_PICK_SECONDS_COLOR = Complications.BOTTOM + 4
        const val REQUEST_PICK_DATE_COLOR = Complications.BOTTOM + 5

        const val REQUEST_PICK_TYPEFACE = Complications.BOTTOM + 6

        object MenuItems {
            const val PREVIEW = 0
            const val COLOR_THEME = 1
            const val HANDEDNESS = 2
            const val IS_24H = 3
            const val COLORFUL_AMBIENT = 4
            const val LARGER_DATE = 5
            const val TYPEFACE = 6
            const val RESET_SETTINGS = 7
        }

    }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var configMenu: WearableRecyclerView
    private lateinit var adapter: Adapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = applicationContext.getSharedPreferences(
            getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )
        setContentView(R.layout.config_activity)

        adapter = Adapter()

        configMenu = findViewById(R.id.config_menu)
        configMenu.layoutManager = LinearLayoutManager(this)
        configMenu.apply {
            isEdgeItemsCenteringEnabled = true
            setHasFixedSize(true)
            adapter = this@ConfigActivity.adapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        adapter.destroy()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG(), "onActivityResult: requestCode=${requestCode} resultCode =${resultCode}")
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_PICK_COLOR_THEME -> {
                if (data?.hasExtra(ColorSelectionActivity.RESULT) == true) {
                    adapter.updateColorTheme(data.getIntExtra(ColorSelectionActivity.RESULT, 0))
                }
            }
            in REQUESTS_COMPLICATIONS -> adapter.updateComplications()
            REQUEST_PICK_HOURS_COLOR -> updateIndividualColor(Settings.HOURS_COLOR, data)
            REQUEST_PICK_MINUTES_COLOR -> updateIndividualColor(Settings.MINUTES_COLOR, data)
            REQUEST_PICK_SECONDS_COLOR -> updateIndividualColor(Settings.SECONDS_COLOR, data)
            REQUEST_PICK_DATE_COLOR -> updateIndividualColor(Settings.DATE_COLOR, data)
            REQUEST_PICK_TYPEFACE -> updateTypeface(Settings.TYPEFACE, data)
        }
    }

    private fun updateIndividualColor(binding: Settings.Binding<Int>, data: Intent?) {
        if (data?.hasExtra(ColorSelectionActivity.RESULT) == true) {
            val color = data.getIntExtra(ColorSelectionActivity.RESULT, 0)
            with(sharedPreferences.edit()) {
                binding.put(this, color)
                when (binding) {
                    Settings.HOURS_COLOR -> {
                        Settings.COMPLICATION_TEXT_COLOR.put(this, color)
                        Settings.AM_PM_COLOR.put(this, color)
                    }
                    Settings.MINUTES_COLOR ->
                        Settings.COMPLICATION_ICON_COLOR.put(this, color)
                }
                apply()
            }
        }
    }

    private fun updateTypeface(binding: Settings.Binding<String>, data: Intent?) {
        Log.i(TAG(), "updateTypeface")
        if (data?.hasExtra(TypefaceSelectionActivity.TYPEFACE) == true) {
            val typeface =
                data.getStringExtra(TypefaceSelectionActivity.TYPEFACE)
                    ?: Typefaces.DEFAULT.displayName
            Log.i(TAG(), "typeface=${typeface}")
            with(sharedPreferences.edit()) {
                binding.put(this, typeface)
                apply()
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        /*
        private val mProviderInfoRetriever: ProviderInfoRetriever =
            ProviderInfoRetriever(
                this@ConfigActivity,
                Executors.newCachedThreadPool()
            ).apply { init() }

         */
        private lateinit var preview: WatchFacePreview

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            when (viewType) {
                MenuItems.PREVIEW ->
                    return PreviewViewHolder(parent).also {
                        preview = it.view.apply {
                            onComplicationIdClick = ::selectComplication
                            onColorSettingClick = ::selectColor
                        }
                    }

                MenuItems.HANDEDNESS ->
                    return HandednessViewHolder(parent)

                MenuItems.IS_24H ->
                    return BooleanBindingViewHolder(
                        parent, Settings.IS24H,
                        R.id.is24h, R.layout.is24h
                    )

                MenuItems.COLORFUL_AMBIENT ->
                    return BooleanBindingViewHolder(
                        parent, Settings.COLORFUL_AMBIENT,
                        R.id.colorful_ambient, R.layout.colorful_ambient
                    )

                MenuItems.LARGER_DATE ->
                    return BooleanBindingViewHolder(
                        parent, Settings.LARGER_DATE,
                        R.id.larger_date, R.layout.larger_date
                    )

                MenuItems.COLOR_THEME ->
                    return ColorThemeViewHolder(parent)

                MenuItems.TYPEFACE ->
                    return SetTypefaceViewHolder(parent)

                MenuItems.RESET_SETTINGS ->
                    return ResetSettingsViewHolder(parent)

            }
            throw UnsupportedOperationException()
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                MenuItems.PREVIEW -> {
                    updateComplications()
                    return
                }
                MenuItems.COLOR_THEME, MenuItems.RESET_SETTINGS,
                MenuItems.HANDEDNESS, MenuItems.IS_24H, MenuItems.COLORFUL_AMBIENT,
                MenuItems.LARGER_DATE, MenuItems.TYPEFACE -> return
            }
            throw UnsupportedOperationException()
        }


        fun updateComplications() {
            /*
            mProviderInfoRetriever.retrieveProviderInfo(
                object : ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    override fun onProviderInfoReceived(
                        complicationId: Int,
                        info: ComplicationProviderInfo?
                    ) {
                        preview.setComplication(complicationId, info)
                    }
                },
                ComponentName(this@ConfigActivity, WatchFaceService::class.java),
                *Complications.RANGE.toList().toIntArray()
            )
            */
        }


        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            //mProviderInfoRetriever.release()
            super.onDetachedFromRecyclerView(recyclerView)
        }

        override fun getItemCount(): Int {
            return 8
        }

        override fun getItemViewType(position: Int): Int = position

        fun destroy() {
            //mProviderInfoRetriever.release()
        }

        fun updateColorTheme(color: Int?) {
            Log.i(TAG(), "updateColorTheme: color = $color")
            if (color == null) return
            val newVeneer = Veneer
                .fromSharedPreferences(sharedPreferences, assets, false)
                .withColorScheme(color)
            with(sharedPreferences.edit()) {
                newVeneer.put(this)
                apply()
            }
        }
    }

    private fun selectColor(binding: Settings.Binding<Int>) {
        val requestCode = when (binding) {
            Settings.HOURS_COLOR -> REQUEST_PICK_HOURS_COLOR
            Settings.MINUTES_COLOR -> REQUEST_PICK_MINUTES_COLOR
            Settings.SECONDS_COLOR -> REQUEST_PICK_SECONDS_COLOR
            Settings.DATE_COLOR -> REQUEST_PICK_DATE_COLOR
            else -> throw UnsupportedOperationException("$binding")
        }
        startActivityForResult(
            Intent(this@ConfigActivity, ColorSelectionActivity::class.java)
                .putExtra(ColorSelectionActivity.NAME, getString(binding.stringId))
                .putExtra(
                    ColorSelectionActivity.ORIGINAL_COLOR,
                    binding.get(sharedPreferences)
                ),
            requestCode
        )

    }

    inner class ColorThemeViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.color_theme, parent, false)
    ), View.OnClickListener {
        private val button: Button = itemView.findViewById(R.id.color_theme_button)

        init {
            button.setOnClickListener(this)
            // dataButton.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0, 0)
        }

        override fun onClick(v: View?) {
            startActivityForResult(
                Intent(this@ConfigActivity, ColorSelectionActivity::class.java)
                    .putExtra(ColorSelectionActivity.NAME, button.text)
                    .putExtra(
                        ColorSelectionActivity.ORIGINAL_COLOR,
                        Settings.HOURS_COLOR.get(sharedPreferences)
                    ),
                REQUEST_PICK_COLOR_THEME
            )
        }

    }

    inner class SetTypefaceViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.set_typeface, parent, false)
    ), View.OnClickListener {

        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            startActivityForResult(
                Intent(this@ConfigActivity, TypefaceSelectionActivity::class.java)
                    .putExtra(
                        TypefaceSelectionActivity.TYPEFACE,
                        Settings.TYPEFACE.get(sharedPreferences)
                    ),
                REQUEST_PICK_TYPEFACE
            )
        }

    }

    inner class PreviewViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.preview, parent, false)
    ) {
        val view: WatchFacePreview = itemView.findViewById(R.id.preview)
    }

    inner class ResetSettingsViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.reset_settings, parent, false)
    ), View.OnClickListener {
        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            with(sharedPreferences.edit()) {
                Settings.applyDefault(this)
                apply()
            }
        }
    }


    inner class HandednessViewHolder(
        parent: ViewGroup,
    ) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.handedness, parent, false)
    ), View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
        @SuppressLint("UseSwitchCompatOrMaterialCode") // TOD0(#14)
        private val switch: Switch = itemView.findViewById(R.id.handedness)

        init {
            itemView.setOnClickListener(this)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            updateCurrentState()
        }

        private fun updateCurrentState() {
            switch.setCompoundDrawablesWithIntrinsicBounds(
                when (isOnRightHand(sharedPreferences)) {
                    true -> R.drawable.ic_watch_right_hand
                    false -> R.drawable.ic_watch_left_hand
                },
                0, 0, 0
            )
            switch.isChecked = isOnRightHand(sharedPreferences)
        }

        private fun isOnRightHand(sharedPreferences: SharedPreferences): Boolean =
            !Settings.LEFT_HANDED.get(sharedPreferences)

        override fun onClick(v: View?) {
            Log.i(TAG(), "onClick")
            val leftHanded = Settings.LEFT_HANDED.get(sharedPreferences)
            with(sharedPreferences.edit()) {
                Settings.LEFT_HANDED.put(this, !leftHanded)
                apply()
            }
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) = updateCurrentState()
    }

    inner class BooleanBindingViewHolder(
        parent: ViewGroup,
        private val binding: Settings.Binding<Boolean>,
        viewId: Int,
        val layoutId: Int,
    ) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
    ), View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
        @SuppressLint("UseSwitchCompatOrMaterialCode") // TOD0(#14)
        private val switch: Switch = itemView.findViewById(viewId)

        init {
            itemView.setOnClickListener(this)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            updateCurrentState()
        }

        private fun updateCurrentState() {
            switch.isChecked = binding.get(sharedPreferences)
        }

        override fun onClick(v: View?) {
            Log.i(TAG(), "onClick")
            val value = binding.get(sharedPreferences)
            with(sharedPreferences.edit()) {
                binding.put(this, !value)
                apply()
            }
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) = updateCurrentState()
    }

    private fun selectComplication(complicationId: Int) {
        /*
        this@ConfigActivity.startActivityForResult(
            ComplicationHelperActivity.createProviderChooserHelperIntent(
                this,
                ComponentName(this, WatchFaceService::class.java),
                complicationId,
                ComplicationData.TYPE_SHORT_TEXT
            ),
            requestCodeOf(complicationId)
        )
         */
    }
}
