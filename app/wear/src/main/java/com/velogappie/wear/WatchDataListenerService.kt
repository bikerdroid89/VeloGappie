package com.velogappie.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService

private const val TAG = "WatchDataListener"
private const val PATH_OPEN_WATCH_APP = "/open_watch_app"
private const val PATH_CLOSE_WATCH_APP = "/close_watch_app"

/**
 * Background-wake fallback for when the app isn't running (a live DataClient listener,
 * registered from MainActivity while the app is in the foreground, is the primary path —
 * see BikeStateRepository.handleDataEvents).
 */
class WatchDataListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        try {
            BikeStateRepository.init(this)
            BikeStateRepository.handleDataEvents(dataEvents)
            for (event in dataEvents) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                when (event.dataItem.uri.path) {
                    PATH_OPEN_WATCH_APP -> {
                        val intent = Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(intent)
                    }
                    PATH_CLOSE_WATCH_APP -> BikeStateRepository.requestClose()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "onDataChanged crashed", e)
        }
    }
}
