package de.mulambda.slantedwatchface

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        contentView.layoutManager = LinearLayoutManager(this)
        contentView.apply {
            isEdgeItemsCenteringEnabled = true
            setHasFixedSize(true)
            adapter = this@TypefaceSelectionActivity.adapter
        }
    }

    inner class FontHolder(parent: ViewGroup, val typefaceIndex: Int) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.typeface_button, parent, false)
    ), View.OnClickListener {
        val config = Typefaces.ALL[typefaceIndex]
        val button = itemView as RadioButton


        init {
            button.text = config.displayName
            button.typeface = Typefaces(assets, config).timeTypeface
            button.isChecked = config.displayName.equals(originalTypeface)
            button.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            Log.i(TAG(), "Clicked: ${config.displayName} index=$typefaceIndex")
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