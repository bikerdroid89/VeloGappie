package com.velogappie.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PhoneMessenger"
private const val PATH_WATCH_LEVELS = "/watch_levels"
private const val PATH_WATCH_EVENT = "/watch_event"

/**
 * Watch -> phone, over DataClient (not MessageClient): on real hardware,
 * MessageClient.sendMessage reported local success without ever being delivered to the
 * phone, while DataClient sync (used for phone -> watch telemetry) was reliable. DataClient
 * persists/retries through transient disconnects; MessageClient is fire-and-forget.
 *
 * Cadence/heart rate are level values (safe to redeliver, latest write wins). Toggle-lights
 * and request-state are one-shot events, so they carry a monotonic seq the phone uses to
 * tell a genuinely new event from the same DataItem being re-delivered.
 */
object PhoneMessenger {
    private val eventSeq = AtomicLong(0)

    fun sendCadence(context: Context, rpm: Int) = putLevels(context) { dataMap.putInt("cadenceRpm", rpm) }
    fun sendHeartRate(context: Context, bpm: Int) = putLevels(context) { dataMap.putInt("heartRateBpm", bpm) }
    fun sendToggleLights(context: Context) = putEvent(context, "toggle_lights")
    fun sendRequestState(context: Context) = putEvent(context, "request_state")

    private fun putLevels(context: Context, fill: PutDataMapRequest.() -> Unit) {
        val request = PutDataMapRequest.create(PATH_WATCH_LEVELS).apply(fill)
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
            .addOnFailureListener { e -> Log.e(TAG, "putLevels failed", e) }
    }

    private fun putEvent(context: Context, kind: String) {
        val request = PutDataMapRequest.create(PATH_WATCH_EVENT).apply {
            dataMap.putString("kind", kind)
            dataMap.putLong("seq", eventSeq.incrementAndGet())
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
            .addOnFailureListener { e -> Log.e(TAG, "putEvent($kind) failed", e) }
    }
}
