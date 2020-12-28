package de.mulambda.slantedwatchface

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.wearable.complications.*
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView
import java.util.concurrent.Executors

class ConfigActivity : Activity() {
    private val TAG = ConfigActivity::class.qualifiedName

    companion object {
        const val REQUEST_TOP_COMPLICATION = 1001
        const val REQUEST_BOTTOM_COMPLICATION = 1002
    }

    private lateinit var mConfigMenu: WearableRecyclerView
    private lateinit var mAdapter: Adapter
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.w(TAG, "onCreate")
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
        const val TOP_COMPLICATION = 1
        const val BOTTOM_COMPLICATION = 2
    }

    fun requestCodeOf(complicationId: Int) = when (complicationId) {
        WatchFace.Complications.TOP -> REQUEST_TOP_COMPLICATION
        WatchFace.Complications.BOTTOM -> REQUEST_BOTTOM_COMPLICATION
        else -> throw UnsupportedOperationException("complicationId = ${complicationId}")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(
            TAG,
            "onActivityResult: requestCode=${requestCode} resultCode=${resultCode} success = ${resultCode == RESULT_OK}"
        )
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            Log.i(TAG, "onCreateViewHolder:${viewType}")
            when (viewType) {
                MenuItems.PREVIEW ->
                    return PreviewViewHolder(parent).also {
                        preview = it.view.apply { onComplicationIdClick = ::selectComplication }
                    }
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
            Log.i(TAG, "onBindViewHolder:${position}")
            when (holder.itemViewType) {
                MenuItems.TOP_COMPLICATION, MenuItems.BOTTOM_COMPLICATION -> {
                    retrieveComplicationInfo(holder as ComplicationViewHolder)
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
            Log.i(TAG, "Detached from view")
            mProviderInfoRetriever.release()
            super.onDetachedFromRecyclerView(recyclerView)
        }

        override fun getItemCount(): Int {
            return 3
        }

        override fun getItemViewType(position: Int): Int {
            when (position) {
                0 -> return MenuItems.PREVIEW
                1 -> return MenuItems.TOP_COMPLICATION
                2 -> return MenuItems.BOTTOM_COMPLICATION
            }
            throw UnsupportedOperationException()
        }

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
        LayoutInflater.from(parent.context).inflate(R.layout.complication, parent, false)
    ),
        View.OnClickListener {
        private val TAG = this::class.qualifiedName
        private val mDataButton: Button = itemView.findViewById(R.id.data_button)

        init {
            itemView.setOnClickListener(this)
            mDataButton.setCompoundDrawablesWithIntrinsicBounds(iconId, 0, 0, 0)
        }

        fun setComplication(p1: ComplicationProviderInfo?) {
            if (p1 != null) {
                mDataButton.text = p1.providerName
            } else {
                mDataButton.setText(R.string.empty_provider)
            }
        }


        override fun onClick(v: View?) {
            Log.i(TAG, "OnClick")

            selectComplication(complicationId)
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
