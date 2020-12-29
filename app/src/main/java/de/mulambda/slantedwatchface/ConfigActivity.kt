package de.mulambda.slantedwatchface

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.wearable.complications.*
import android.util.Log
import android.util.SparseArray
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
        const val REQUEST_TOP_COMPLICATION = 1001
        const val REQUEST_BOTTOM_COMPLICATION = 1002
        const val REQUEST_PICK_COLOR_THEME = 1003

        object MenuItems {
            const val PREVIEW = 0
            const val MORE = 1
            const val HANDEDNESS = 2
            const val COLOR_THEME = 3
            const val TOP_COMPLICATION = 4
            const val BOTTOM_COMPLICATION = 5
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

    fun requestCodeOf(complicationId: Int) = when (complicationId) {
        WatchFace.Complications.TOP -> REQUEST_TOP_COMPLICATION
        WatchFace.Complications.BOTTOM -> REQUEST_BOTTOM_COMPLICATION
        else -> throw UnsupportedOperationException("complicationId = ${complicationId}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG(), "onActivityResult: requestCode=${requestCode} resultCode =${resultCode}")
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_TOP_COMPLICATION ->
                mAdapter.updateComplication(WatchFace.Complications.TOP)
            REQUEST_BOTTOM_COMPLICATION ->
                mAdapter.updateComplication(WatchFace.Complications.BOTTOM)
            REQUEST_PICK_COLOR_THEME -> {
                if (data?.hasExtra(ColorSelectionActivity.RESULT) == true) {
                    mAdapter.updateColorTheme(data.getIntExtra(ColorSelectionActivity.RESULT, 0))
                }
            }

        }
    }

    inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private val TAG = Adapter::class.qualifiedName
        private val mProviderInfoRetriever: ProviderInfoRetriever =
            ProviderInfoRetriever(
                this@ConfigActivity,
                Executors.newCachedThreadPool()
            ).apply { init() }
        private val mComplicationViews =
            SparseArray<ComplicationViewHolder>(WatchFace.Complications.ALL.size)
        private lateinit var preview: WatchFacePreview

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            when (viewType) {
                MenuItems.PREVIEW ->
                    return PreviewViewHolder(parent).also {
                        preview = it.view.apply { onComplicationIdClick = ::selectComplication }
                    }
                MenuItems.MORE ->
                    return MoreViewHolder(parent)

                MenuItems.HANDEDNESS ->
                    return HandednessViewHolder(parent)

                MenuItems.COLOR_THEME ->
                    return ColorThemeViewHolder(parent)

                MenuItems.TOP_COMPLICATION ->
                    return complicationViewHolder(
                        parent, WatchFace.Complications.TOP,
                        R.drawable.ic_top_complication
                    )

                MenuItems.BOTTOM_COMPLICATION ->
                    return complicationViewHolder(
                        parent,
                        WatchFace.Complications.BOTTOM,
                        R.drawable.ic_bottom_complication
                    )

            }
            throw UnsupportedOperationException()
        }


        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder.itemViewType) {
                MenuItems.TOP_COMPLICATION, MenuItems.BOTTOM_COMPLICATION -> {
                    retrieveComplicationInfo(holder as ComplicationViewHolder)
                    return
                }
                MenuItems.MORE, MenuItems.COLOR_THEME -> return
                MenuItems.HANDEDNESS -> {
                    return
                }
                MenuItems.PREVIEW -> {
                    retrieveComplicationInfos((holder as PreviewViewHolder).view)
                    return
                }
            }
            throw UnsupportedOperationException()
        }


        private fun retrieveComplicationInfos(watchFacePreview: WatchFacePreview) {
            mProviderInfoRetriever.retrieveProviderInfo(
                object : ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    override fun onProviderInfoReceived(
                        complicationId: Int,
                        info: ComplicationProviderInfo?
                    ) {
                        watchFacePreview.setComplication(complicationId, info)
                    }
                },
                ComponentName(this@ConfigActivity, WatchFace::class.java),
                *WatchFace.Complications.ALL
            )
        }

        private fun retrieveComplicationInfo(complicationViewHolder: ComplicationViewHolder) {
            mProviderInfoRetriever.retrieveProviderInfo(
                object : ProviderInfoRetriever.OnProviderInfoReceivedCallback() {
                    override fun onProviderInfoReceived(id: Int, info: ComplicationProviderInfo?) {
                        complicationViewHolder.setComplication(info)
                    }
                },
                ComponentName(this@ConfigActivity, WatchFace::class.java),
                complicationViewHolder.complicationId
            )
        }

        fun updateComplication(complicationId: Int) {
            val complicationViewHolder = mComplicationViews[complicationId]
            if (complicationViewHolder != null) {
                retrieveComplicationInfo(complicationViewHolder)
            } else {
                Log.e(TAG, "No complicationViewHolder for complicationId=${complicationId}")
            }
            retrieveComplicationInfos(preview)
        }


        override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
            mProviderInfoRetriever.release()
            super.onDetachedFromRecyclerView(recyclerView)
        }

        override fun getItemCount(): Int {
            return 6
        }

        override fun getItemViewType(position: Int): Int = position

        private fun complicationViewHolder(
            parent: ViewGroup, complicationId: Int, iconId: Int
        ): ComplicationViewHolder {
            return ComplicationViewHolder(parent, complicationId, iconId).also {
                mComplicationViews.put(complicationId, it)
            }
        }

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

    class MoreViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.more_options, parent, false)
    )

    inner class PreviewViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.preview, parent, false)
    ) {
        val view: WatchFacePreview = itemView.findViewById(R.id.preview)
    }

    inner class ComplicationViewHolder(
        parent: ViewGroup,
        val complicationId: Int,
        iconId: Int
    ) : RecyclerView.ViewHolder(
        LayoutInflater
            .from(parent.context)
            .inflate(R.layout.complication, parent, false)
    ),
        View.OnClickListener {
        private val dataButton: Button = itemView.findViewById(R.id.data_button)

        init {
            itemView.setOnClickListener(this)
            dataButton.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0, 0)
        }

        fun setComplication(p1: ComplicationProviderInfo?) {
            if (p1 != null) {
                dataButton.text = p1.providerName
            } else {
                dataButton.setText(R.string.empty_provider)
            }
        }


        override fun onClick(v: View?) {
            selectComplication(complicationId)
        }
    }

    inner class HandednessViewHolder(
        parent: ViewGroup,
    ) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.handedness, parent, false)
    ), View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private val switch: Switch = itemView.findViewById(R.id.handedness)

        init {
            itemView.setOnClickListener(this)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)
            updateCurrentState()
        }

        fun updateCurrentState() {
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
                ComponentName(this, WatchFace::class.java),
                complicationId,
                ComplicationData.TYPE_SHORT_TEXT
            ),
            requestCodeOf(complicationId)
        )
    }
}
