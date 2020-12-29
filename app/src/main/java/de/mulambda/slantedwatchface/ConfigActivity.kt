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
    }

    private lateinit var mConfigMenu: WearableRecyclerView
    private lateinit var mAdapter: Adapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    object MenuItems {
        const val PREVIEW = 0
        const val MORE = 1
        const val HANDEDNESS = 2
        const val TOP_COMPLICATION = 3
        const val BOTTOM_COMPLICATION = 4
    }

    fun requestCodeOf(complicationId: Int) = when (complicationId) {
        WatchFace.Complications.TOP -> REQUEST_TOP_COMPLICATION
        WatchFace.Complications.BOTTOM -> REQUEST_BOTTOM_COMPLICATION
        else -> throw UnsupportedOperationException("complicationId = ${complicationId}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != RESULT_OK) return

        when (requestCode) {
            REQUEST_TOP_COMPLICATION ->
                mAdapter.updateComplication(WatchFace.Complications.TOP)
            REQUEST_BOTTOM_COMPLICATION ->
                mAdapter.updateComplication(WatchFace.Complications.BOTTOM)

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
        val sharedPreferences = applicationContext.getSharedPreferences(
            getString(R.string.preference_file_key),
            Context.MODE_PRIVATE
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            when (viewType) {
                MenuItems.PREVIEW ->
                    return PreviewViewHolder(parent).also {
                        preview = it.view.apply { onComplicationIdClick = ::selectComplication }
                    }
                MenuItems.MORE ->
                    return MoreViewHolder(parent)

                MenuItems.HANDEDNESS ->
                    return HandednessViewHolder(parent, sharedPreferences)

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
                MenuItems.MORE -> return
                MenuItems.HANDEDNESS -> {
                    val handednessViewHolder = holder as HandednessViewHolder
                    handednessViewHolder.updateCurrentState()
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
            return 5
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
        private val sharedPreferences: SharedPreferences
    ) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.handedness, parent, false)
    ), View.OnClickListener {
        private val switch: Switch = itemView.findViewById(R.id.handedness)

        init {
            itemView.setOnClickListener(this)
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
            Veneer.getAngle(sharedPreferences) < 0

        override fun onClick(v: View?) {
            Log.i(TAG(), "onClick")
            val angle = Veneer.getAngle(sharedPreferences)
            with(sharedPreferences.edit()) {
                Veneer.setAngle(this, -angle)
                apply()
            }
            updateCurrentState()
        }
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
