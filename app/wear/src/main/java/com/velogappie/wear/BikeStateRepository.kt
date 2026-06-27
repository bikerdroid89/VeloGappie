package com.velogappie.wear

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val PATH_BIKE_STATE = "/bike_state"

data class WatchBikeState(
    val connected: Boolean = false,
    val model: String = "",
    val mock: Boolean = false,
    val batteryPercent: Int? = null,
    val speedKmh: Double? = null,
    val assistLevel: Int = 0,
    val cadenceRpm: Int? = null,
    val lightsOn: Boolean = false,
    val odometer: Int? = null,
)

private const val SPEED_HISTORY_SIZE = 20

/**
 * Holds the latest telemetry received from the phone. A plain singleton object since
 * WatchDataListenerService instances are transient (the system creates/destroys them
 * per delivery) and MainActivity needs a stable place to observe from.
 */
object BikeStateRepository {
    private val _state = MutableStateFlow(WatchBikeState())
    val state: StateFlow<WatchBikeState> = _state

    private val _speedHistory = MutableStateFlow<List<Double>>(emptyList())
    val speedHistory: StateFlow<List<Double>> = _speedHistory

    private val _closeRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closeRequested: SharedFlow<Unit> = _closeRequested.asSharedFlow()

    val lastPhoneUpdateMs = MutableStateFlow(0L)

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requestClose() {
        _closeRequested.tryEmit(Unit)
    }

    fun update(newState: WatchBikeState) {
        _state.value = newState
        lastPhoneUpdateMs.value = System.currentTimeMillis()
        newState.speedKmh?.let { speed ->
            _speedHistory.value = (_speedHistory.value + speed).takeLast(SPEED_HISTORY_SIZE)
        }
        appContext?.let { BikeTileService.requestUpdate(it) }
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
            if (event.dataItem.uri.path != PATH_BIKE_STATE) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            update(
                WatchBikeState(
                    connected = map.getBoolean("connected", false),
                    model = map.getString("model") ?: "",
                    mock = map.getBoolean("mock", false),
                    batteryPercent = if (map.containsKey("battery")) map.getInt("battery") else null,
                    speedKmh = if (map.containsKey("speed")) map.getDouble("speed") else null,
                    assistLevel = map.getInt("assist", 0),
                    cadenceRpm = if (map.containsKey("cadence")) map.getInt("cadence") else null,
                    lightsOn = map.getBoolean("lights", false),
                    odometer = if (map.containsKey("odometer")) map.getInt("odometer") else null,
                )
            )
        }
    }
}
