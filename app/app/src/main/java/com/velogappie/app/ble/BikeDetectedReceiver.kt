package com.velogappie.app.ble

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.velogappie.app.MainActivity

const val EXTRA_AUTO_CONNECT = "auto_connect"

/**
 * Fired by the OS (via the PendingIntent registered in BikeProximityScanner) when the
 * user's bike comes into BLE range — even if this app's process isn't running. Brings the
 * app to the foreground and asks it to reconnect immediately, no tapping required.
 */
class BikeDetectedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_AUTO_CONNECT, true)
        context.startActivity(launch)
    }
}
