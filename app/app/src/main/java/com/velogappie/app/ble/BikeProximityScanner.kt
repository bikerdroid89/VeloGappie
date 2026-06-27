package com.velogappie.app.ble

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import com.velogappie.app.AppSettings

/**
 * Background-capable BLE scan (PendingIntent-based — kept alive by the Bluetooth system
 * service, survives this app's process being killed) for the last-known bike, so the app
 * can auto-launch the moment it comes back in range, e.g. walking up to it with the phone
 * in a pocket. Only ever watches for one specific, already-paired-once device address —
 * there's nothing to auto-launch for before a bike has been connected at least once.
 */
object BikeProximityScanner {
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BikeDetectedReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** No-ops if the feature is disabled, no bike has ever been connected, or Bluetooth
     *  scanning isn't available right now — auto-launch is a convenience, never required. */
    @SuppressLint("MissingPermission")
    fun startIfEnabled(context: Context) {
        if (!AppSettings.isAutoStartOnBikeDetectedEnabled(context)) return
        val address = LastKnownBike.address(context) ?: return
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return
            val scanner = adapter.bluetoothLeScanner ?: return
            val filters = listOf(ScanFilter.Builder().setDeviceAddress(address).build())
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build()
            scanner.startScan(filters, settings, pendingIntent(context))
        } catch (e: Exception) {
            // No background scan on this device/Bluetooth state — fine, the app still
            // works, just without auto-launch.
        }
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        try {
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return
            val scanner = adapter.bluetoothLeScanner ?: return
            scanner.stopScan(pendingIntent(context))
        } catch (e: Exception) {
            // Nothing to stop.
        }
    }
}
