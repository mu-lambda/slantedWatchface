package de.mulambda.slantedwatchface

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.wearable.complications.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView
import java.util.concurrent.Executors

class ConfigActivity : Activity() {
    companion object {
        val REQUESTS_COMPLICATIONS = Complications.RANGE
        private fun requestCodeOf(complicationId: Int) =
            if (complicationId in Complications.RANGE) complicationId
            else throw UnsupportedOperationException("complicationId = ${complicationId}")

        const val REQUEST_PICK_COLOR_THEME = Complications.BOTTOM + 1

        const val REQUEST_PICK_HOURS_COLOR = Complications.BOTTOM + 2
        const val REQUEST_PICK_MINUTES_COLOR = Complications.BOTTOM + 3
        const val REQUEST_PICK_SECONDS_COLOR = Complications.BOTTOM + 4
        const val REQUEST_PICK_DATE_COLOR = Complications.BOTTOM + 5

        object MenuItems {
            const val PREVIEW = 0
            const val COLOR_THEME = 1
            const val HANDEDNESS = 2
            const val RESET_SETTINGS = 3
        }

    }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var mConfigMenu: WearableRecyclerView
    private lateinit var mAdapter: Adapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPreferences = applicationContext.getSharedPreferences(
            getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )
        setContentView(R.layout.config_activity)

        mAdapter = Adapter()

        mConfigMenu = findViewById(R.id.config_menu)
        mConfigMenu.layoutManager = LinearLayoutManager(this)
        mConfigMenu.apply {
            isEdgeItemsCenteringEnabled = true
            setHasFixedSize(true)
            adapter = mAdapter
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mAdapter.destroy()
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG(), "onActivityResult: requestCode=${requestCode} resultCode =${resultCode}")
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_PICK_COLOR_THEME -> {
                if (data?.hasExtra(ColorSelectionActivity.RESULT) == true) {
                    mAdapter.updateColorTheme(data.getIntExtra(ColorSelectionActivity.RESULT, 0))
                }
            }
            in REQUESTS_COMPLICATIONS -> mAdapter.updateComplications()
            REQUEST_PICK_HOURS_COLOR -> updateIndividualColor(Settings.HOURS_COLOR, data)
            REQUEST_PICK_MINUTES_COLOR -> updateIndividualColor(Settings.MINUTES_COLOR, data)
            REQUEST_PICK_SECONDS_COLOR -> updateIndividualColor(Settings.SECONDS_COLOR, data)
            REQUEST_PICK_DATE_COLOR -> updateIndividualColor(Settings.DATE_COLOR, data)
        }
    }

    private fun updateIndividualColor(binding: Settings.Binding<Int>, data: Intent?) {
        if (data?.hasExtra(ColorSelectionActivity.RESULT) == true) {
            val color = data.getIntExtra(ColorSelectionActivity.RESULT, 0)
            with(sharedPreferences.edit()) {
                binding.put(this, color)
                when (binding) {
                    Settings.HOURS_COLOR ->
                        Settings.COMPLICATION_TEXT_COLOR.put(this, color)
                    Settings.MINUTES_COLOR ->
                        Settings.COMPLICATION_ICON_COLOR.put(this, color)
                }
                apply()
            }
        }
    }

    inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val mProviderInfoRetriever: ProviderInfoRetriever =
            ProviderInfoRetriever(
                this@ConfigActivity,
                Executors.newCachedThreadPool()
            ).apply { init() }
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

                MenuItems.COLOR_THEME ->
                    return ColorThemeViewHolder(parent)

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
                MenuItems.COLOR_THEME, MenuItems.RESET_SETTINGS, MenuItems.HANDEDNESS -> return
            }
            throw UnsupportedOperationException()
        }


        fun updateComplications() {
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
        }


        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            mProviderInfoRetriever.release()
            super.onDetachedFromRecyclerView(recyclerView)
        }

        override fun getItemCount(): Int {
            return 4
        }

        override fun getItemViewType(position: Int): Int = position

        fun destroy() {
            mProviderInfoRetriever.release()
        }

        private val typefaces = Typefaces(assets)

        fun updateColorTheme(color: Int?) {
            Log.i(TAG(), "updateColorTheme: color = ${color}")
            if (color == null) return
            val newVeneer = Veneer
                .fromSharedPreferences(sharedPreferences, typefaces, false)
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
            itemView.setOnClickListener(this)
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
            Settings.ANGLE.get(sharedPreferences) < 0

        override fun onClick(v: View?) {
            Log.i(TAG(), "onClick")
            val angle = Settings.ANGLE.get(sharedPreferences)
            with(sharedPreferences.edit()) {
                Settings.ANGLE.put(this, -angle)
                apply()
            }
        }

        override fun onSharedPreferenceChanged(
            sharedPreferences: SharedPreferences?,
            key: String?
        ) = updateCurrentState()
    }

    private fun selectComplication(complicationId: Int) {
        this@ConfigActivity.startActivityForResult(
            ComplicationHelperActivity.createProviderChooserHelperIntent(
                this,
                ComponentName(this, WatchFaceService::class.java),
                complicationId,
                ComplicationData.TYPE_SHORT_TEXT
            ),
            requestCodeOf(complicationId)
        )
    }
}
