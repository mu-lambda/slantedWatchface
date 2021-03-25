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

package de.mulambda.slantedwatchface.installer

import TAG
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.wearable.intent.RemoteIntent
import de.mulambda.slantedwatchface.R

class MainActivity : AppCompatActivity(), CapabilityClient.OnCapabilityChangedListener {

    private val resultReciever: ResultReceiver =
        object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                Log.d(TAG(), "onRecieveResult: $resultCode")

                if (resultCode == RemoteIntent.RESULT_OK) {
                    Toast.makeText(
                        applicationContext,
                        "Play Store Request to Wear device successful.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (resultCode == RemoteIntent.RESULT_FAILED) {
                    Toast.makeText(
                        applicationContext,
                        "Play Store Request Failed. Wear device(s) may not support Play Store, "
                                + " that is, the Wear device may be version 1.0.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    throw IllegalStateException("Unexpected result $resultCode")
                }
            }
        }


    private lateinit var installButton: Button
    private lateinit var statusTextView: TextView
    private var wearNodesWithApp: Set<Node>? = null
    private var allConnectedNodes: List<Node>? = null


    companion object {
        private val CAPABILITY_WEAR_APP = "de.mulambda.slantedWatchface.wearable.verify"
        private val PLAY_STORE_URI =
            "market://details?id=de.mulambda.slantedWatchface"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        installButton = findViewById(R.id.install_button)
        statusTextView = findViewById(R.id.status_text)
        statusTextView.setText(R.string.text_checking)

        installButton.setOnClickListener { installWearOSApps(); }
    }

    override fun onResume() {
        super.onResume()

        Wearable.getCapabilityClient(this).addListener(this, CAPABILITY_WEAR_APP)
        findWearDevicesWithApp()
        findAllWearDevices()
    }

    private fun findAllWearDevices() {
        Wearable.getNodeClient(this).connectedNodes.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d(TAG(), "Connected node request successful.")
                allConnectedNodes = it.result
            } else {
                Log.d(TAG(), "Connected node request failed.")
            }
            updateUI()
        }
    }

    private fun updateUI() {
        Log.d(TAG(), "updateUI()")

        val wearNodesWithApp = this.wearNodesWithApp ?: emptySet()
        val allConnectedNodes = this.allConnectedNodes ?: emptyList()
        if (this.wearNodesWithApp == null || this.allConnectedNodes == null) {
            Log.d(TAG(), "Still waiting on results")
            return
        }

        if (allConnectedNodes.isEmpty()) {
            statusTextView.setText(R.string.text_no_devices)
            installButton.isEnabled = false
        } else {
            if (wearNodesWithApp.isEmpty()) {
                statusTextView.setText(R.string.text_on_no_devices)
            } else if (wearNodesWithApp.size < allConnectedNodes.size) {
                statusTextView.setText(R.string.text_on_some_devices)
            } else if (wearNodesWithApp.size == allConnectedNodes.size) {
                statusTextView.setText(R.string.text_on_all_devices)
            }

            installButton.isEnabled = true
        }
    }

    private fun findWearDevicesWithApp() {

        Wearable.getCapabilityClient(this).getCapability(
            CAPABILITY_WEAR_APP,
            CapabilityClient.FILTER_ALL
        ).addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d(TAG(), "Capability request successful.")
                    wearNodesWithApp = it.result?.nodes
                } else {
                    Log.d(TAG(), "Capability request failed.")
                }
                updateUI()
            }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getCapabilityClient(this).removeListener(this, CAPABILITY_WEAR_APP)
    }

    private fun installWearOSApps() {
        Log.d(TAG(), "installWearOSApps()")

        val wearNodesWithApp = this.wearNodesWithApp ?: emptySet()
        val allConnectedNodes = this.allConnectedNodes ?: emptyList()

        val nodesWithoutApps = allConnectedNodes.filter { !wearNodesWithApp.contains(it) }

        if (!nodesWithoutApps.isEmpty()) {
            Log.d(TAG(), "Installing on ${nodesWithoutApps.size} nodes")
            val intent = Intent(Intent.ACTION_VIEW).addCategory(Intent.CATEGORY_BROWSABLE).setData(
                Uri.parse(PLAY_STORE_URI)
            )
            for (node in nodesWithoutApps) {
                RemoteIntent.startRemoteActivity(
                    applicationContext,
                    intent,
                    resultReciever,
                    node.id
                )
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        wearNodesWithApp = capabilityInfo.nodes
        findAllWearDevices()

        updateUI()
    }
}