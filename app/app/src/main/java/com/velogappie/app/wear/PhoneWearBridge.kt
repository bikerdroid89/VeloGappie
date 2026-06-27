package com.velogappie.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.velogappie.app.BikeUiState
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PhoneWearBridge"
private const val PATH_BIKE_STATE = "/bike_state"
private const val PATH_OPEN_WATCH_APP = "/open_watch_app"
private const val PATH_CLOSE_WATCH_APP = "/close_watch_app"

/**
 * Phone -> watch, over the Wearable Data Layer's DataClient — talks directly to a paired
 * watch over Bluetooth/Wi-Fi Direct, no internet, no cloud, consistent with this app's
 * local-only design. No-ops harmlessly if no watch is paired/the companion isn't installed.
 * Uses DataClient (not MessageClient) throughout: on real hardware, MessageClient.sendMessage
 * reported local success without ever being delivered, while DataClient sync was reliable.
 */
object PhoneWearBridge {
    private val openSeq = AtomicLong(0)
    private val closeSeq = AtomicLong(0)

    /** Brings the Wear OS companion app to the foreground on any connected watch. No-ops
     *  harmlessly (including on devices with no Play Services at all) if that fails. */
    fun openWatchApp(context: Context) {
        try {
            val request = PutDataMapRequest.create(PATH_OPEN_WATCH_APP).apply {
                dataMap.putLong("seq", openSeq.incrementAndGet())
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
                .addOnFailureListener { e -> Log.e(TAG, "openWatchApp failed", e) }
        } catch (e: Exception) {
            // No watch support available on this device — fine, the watch is optional.
        }
    }

    fun closeWatchApp(context: Context) {
        try {
            val request = PutDataMapRequest.create(PATH_CLOSE_WATCH_APP).apply {
                dataMap.putLong("seq", closeSeq.incrementAndGet())
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
                .addOnFailureListener { e -> Log.e(TAG, "closeWatchApp failed", e) }
        } catch (e: Exception) {
            // No watch support available — fine.
        }
    }

    fun pushState(context: Context, state: BikeUiState) {
        try {
            val request = PutDataMapRequest.create(PATH_BIKE_STATE).apply {
                dataMap.putBoolean("connected", state.connectionState.name == "CONNECTED")
                dataMap.putString("model", state.connectedName ?: "")
                dataMap.putBoolean("mock", state.mockMode)
                state.batteryPercent?.let { dataMap.putInt("battery", it) }
                state.speedKmh?.let { dataMap.putDouble("speed", it) }
                dataMap.putInt("assist", state.motorAssistLevel)
                state.cadenceRpm?.let { dataMap.putInt("cadence", it) }
                dataMap.putBoolean("lights", state.lightsOn)
                state.odometer?.let { dataMap.putInt("odometer", it) }
            }
            // Deliberately not .setUrgent() here: that forces an immediate radio wake on
            // every call, defeating the Data Layer's normal power-friendly batching. This
            // is called from a throttled ticker (see BikeViewModel), so a little scheduling
            // slack costs nothing in practice but saves real battery over a multi-hour ride.
            // openWatchApp() above stays urgent — that's a one-shot wake where latency
            // actually matters. Also no "ts" field: it existed only to force every call to
            // look like a changed payload, defeating DataClient's own no-op-if-unchanged dedup.
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest())
                .addOnFailureListener { e -> Log.e(TAG, "pushState failed", e) }
        } catch (e: Exception) {
            // No watch support available on this device — fine, the watch is optional.
        }
    }
}
