package com.velogappie.app.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Background BLE scan registrations don't survive a full device reboot; re-arm if a bike
 *  was ever connected on this device and the feature is still enabled. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        BikeProximityScanner.startIfEnabled(context)
    }
}
