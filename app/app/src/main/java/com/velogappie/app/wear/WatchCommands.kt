package com.velogappie.app.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.flow.MutableSharedFlow

private const val PATH_WATCH_LEVELS = "/watch_levels"
private const val PATH_WATCH_EVENT = "/watch_event"

object WatchCommands {
    val cadenceRequests = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val heartRateReports = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val toggleLightsRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val stateRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    @Volatile private var lastEventSeq = -1L

    /** Edge-triggered watch events (toggle/request-state) share one DataItem; this
     *  distinguishes a genuinely new event from the same item being re-delivered. */
    @Synchronized
    fun isNewEvent(seq: Long): Boolean {
        if (seq <= lastEventSeq) return false
        lastEventSeq = seq
        return true
    }

    /**
     * Shared by the manifest-declared WearableListenerService (background wake) and a live
     * DataClient listener registered while the app is in the foreground. Manifest-only
     * dispatch proved unreliable on real hardware: GMS never invoked the declared service at
     * all, confirmed via dumpsys showing this app absent from its listener-service queue
     * while data demonstrably still arrived at the transport layer.
     */
    fun handleDataEvents(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            when (event.dataItem.uri.path) {
                PATH_WATCH_LEVELS -> {
                    if (map.containsKey("cadenceRpm")) cadenceRequests.tryEmit(map.getInt("cadenceRpm"))
                    if (map.containsKey("heartRateBpm")) heartRateReports.tryEmit(map.getInt("heartRateBpm"))
                }
                PATH_WATCH_EVENT -> {
                    val seq = map.getLong("seq", -1)
                    if (isNewEvent(seq)) {
                        when (map.getString("kind")) {
                            "toggle_lights" -> toggleLightsRequests.tryEmit(Unit)
                            "request_state" -> stateRefreshRequests.tryEmit(Unit)
                        }
                    }
                }
            }
        }
    }
}
