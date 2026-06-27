package com.velogappie.app.wear

import android.util.Log
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "PhoneWearListener"

/**
 * Background-wake fallback for when the app isn't running (a live DataClient listener,
 * registered from BikeViewModel while the app is alive, is the primary path — see
 * WatchCommands.handleDataEvents).
 */
class PhoneWearListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            WatchCommands.handleDataEvents(dataEvents)
        } catch (e: Throwable) {
            Log.e(TAG, "onDataChanged crashed", e)
        }
    }
}
