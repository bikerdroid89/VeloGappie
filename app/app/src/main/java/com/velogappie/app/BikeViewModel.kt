package com.velogappie.app

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velogappie.app.ble.BikeEvent
import com.velogappie.app.ble.BikeProximityScanner
import com.velogappie.app.ble.ConnectionState
import com.velogappie.app.ble.DiscoveredBike
import com.velogappie.app.ble.EnvioloBleManager
import com.velogappie.app.ble.EnvioloMode
import com.velogappie.app.ble.KnownBikes
import com.velogappie.app.ble.LastKnownBike
import com.velogappie.app.ble.BikeBleManager
import com.velogappie.app.ble.BikeRead
import com.velogappie.app.ble.BikeWrite
import com.velogappie.app.ble.ConnectionService
import com.velogappie.app.drive.DriveSync
import com.velogappie.app.update.AppUpdateChecker
import com.velogappie.app.update.UpdateState
import com.velogappie.app.health.HealthConnectBridge
import com.velogappie.app.weather.WeatherFetcher
import com.velogappie.app.nav.NavBridgeState
import com.velogappie.app.nav.NavDirection
import com.velogappie.app.nav.NavInstruction
import com.velogappie.app.ride.LocationPointEntity
import com.velogappie.app.ride.RideDatabase
import com.velogappie.app.ride.RideEntity
import com.velogappie.app.ride.RideGroupEntity
import com.velogappie.app.sun.SunCalculator
import com.velogappie.app.ui.theme.AppAccent
import com.velogappie.app.ui.theme.AppTheme
import com.velogappie.app.wear.PhoneWearBridge
import com.velogappie.app.wear.WatchCommands
import com.velogappie.app.widget.BikeWidgetProvider
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random
import java.util.Calendar
import java.util.TimeZone

data class BikeUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val discovered: List<DiscoveredBike> = emptyList(),
    val connectedName: String? = null,
    val batteryPercent: Int? = null,
    val batteryHealthPercent: Int? = null,
    val charging: Boolean? = null,
    val speedKmh: Double? = null,
    val maxSpeedKmh: Double = 0.0,
    val odometer: Int? = null,
    val tripDistanceKm: Double = 0.0,
    val cadenceRpm: Int? = null,
    val displaySerial: String? = null,
    val displayHardwareVersion: String? = null,
    val displayFirmwareVersion: String? = null,
    val experienceModuleFirmwareVersion: String? = null,
    val lightsOn: Boolean = false,
    val motorAssistLevel: Int = 0,
    val autoLightsEnabled: Boolean = false,
    val hasEmergencyContact: Boolean = false,
    val mockMode: Boolean = false,
    val heartRateBpm: Int? = null,
    val displayMode: DisplayMode? = null,
    val maxMotorSpeedKmh: Int? = null,
    /** Enviolo Harmony/Automatiq hub's start-gear-ratio multiplier, 0.00-2.55. Null until
     *  read at connect — also doubles as "no Enviolo hub found", gating its Dashboard UI. */
    val startMultiplier: Double? = null,
    val envioloSerialNumber: String? = null,
    val envioloArticleNumber: String? = null,
    val envioloOperationMode: String? = null,
    val envioloMinCadence: Int? = null,
    val envioloMaxCadence: Int? = null,
    val envioloOdometer: Int? = null,
    val envioloMode: EnvioloMode? = null,
    val navInstruction: NavInstruction? = null,
    val currentTrack: String? = null,
    val weatherDisplayEnabled: Boolean = false,
)

enum class DisplayMode { CLOCK, CLOCK_SPLIT, HEART_RATE, CLOCK_HR }

/** Minimum ride duration before it's worth persisting — filters out brief test/mock connects. */
private const val MIN_RIDE_DURATION_MS = 60_000L

/** Grace period: ride stays alive this long after speed drops to zero (traffic lights, etc). */
private const val RIDE_IDLE_GRACE_MS = 5 * 60_000L

private const val RECONNECT_ATTEMPTS = 6
private const val RECONNECT_INTERVAL_MS = 5_000L

/** Accumulates samples for a ride in progress; converted to a RideEntity on finalize. */
private class ActiveRide(val startTime: Long, val startBatterySoc: Int?) {
    var distanceKm: Double = 0.0
    var speedSampleSum: Double = 0.0
    var speedSampleCount: Int = 0
    var maxSpeedKmh: Double = 0.0
    val heartRateSamples = mutableListOf<Pair<Long, Int>>()
    var cadenceSampleSum: Int = 0
    var cadenceSampleCount: Int = 0
    var maxCadenceRpm: Int = 0
    val locationPoints = mutableListOf<LocationPointEntity>()
}

class BikeViewModel(app: Application) : AndroidViewModel(app) {

    private val ble = BikeBleManager(app)
    private val enviolo = EnvioloBleManager(app)
    private val rideDao = RideDatabase.getInstance(app).rideDao()

    private val _state = MutableStateFlow(BikeUiState())
    val state: StateFlow<BikeUiState> = _state.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _finishApp = MutableSharedFlow<Unit>()
    val finishApp: SharedFlow<Unit> = _finishApp.asSharedFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    val rides: StateFlow<List<RideEntity>> =
        rideDao.getAllRides().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rideGroups: StateFlow<List<RideGroupEntity>> =
        rideDao.getAllGroups().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var autoLightsJob: Job? = null
    private var mockJob: Job? = null
    private var cycleDisplayJob: Job? = null
    private var weatherDisplayJob: Job? = null
    private var reconnectJob: Job? = null
    private var isMock = false
    private var userInitiatedDisconnect = false
    private var connectedSerial: String? = null
    private var activeRide: ActiveRide? = null
    private val locationManager = app.getSystemService(LocationManager::class.java)
    private var rideLocationListener: LocationListener? = null

    // Primary path for receiving watch signals: the manifest-declared WearableListenerService
    // is kept only as a background-wake fallback, since on real hardware GMS never invoked it
    // at all (confirmed via dumpsys) despite data demonstrably arriving at the transport layer.
    private val dataClientListener = DataClient.OnDataChangedListener { dataEvents ->
        WatchCommands.handleDataEvents(dataEvents)
    }

    init {
        // The watch is entirely optional: a missing/broken Play Services Wearable API
        // must never prevent the rest of the app (bike control) from working.
        try {
            Wearable.getDataClient(app).addListener(dataClientListener)
        } catch (e: Exception) {
            // No watch support available on this device — fine, everything else still works.
        }
        // Defensive re-arm: a background BLE scan registration is supposed to survive this
        // process being killed, but re-asserting it on every app start is cheap and covers
        // any edge case where it didn't.
        BikeProximityScanner.startIfEnabled(app)
        NavBridgeState.init(app)
        ConnectionService.onDisconnectRequested = { disconnect() }
        ConnectionService.onStopScanRequested = { stopScan() }
        checkForUpdate()
        if (AppSettings.isWeatherDisplayEnabled(app)) setWeatherDisplay(true)
        recoverActiveRide()
        viewModelScope.launch {
            var wasConnected = false
            ble.connectionState.collect { cs ->
                _state.update { it.copy(connectionState = cs) }
                when (cs) {
                    ConnectionState.SCANNING -> ConnectionService.start(
                        app, app.getString(R.string.app_name),
                        app.getString(R.string.notif_scanning),
                        ConnectionService.MODE_SCANNING,
                    )
                    ConnectionState.CONNECTING -> ConnectionService.start(
                        app, _state.value.connectedName ?: "Bike",
                        app.getString(R.string.notif_connecting),
                    )
                    ConnectionState.CONNECTED -> {
                        wasConnected = true
                        reconnectJob?.cancel()
                        reconnectJob = null
                        userInitiatedDisconnect = false
                        onConnected()
                        ConnectionService.start(
                            app, _state.value.connectedName ?: "Bike",
                            app.getString(R.string.notif_connected),
                        )
                    }
                    ConnectionState.DISCONNECTED -> {
                        if (wasConnected && !userInitiatedDisconnect && !isMock) {
                            startReconnect()
                        } else {
                            ConnectionService.stop(app)
                        }
                        wasConnected = false
                    }
                }
            }
        }
        viewModelScope.launch {
            ble.discovered.collect { list -> _state.update { it.copy(discovered = list) } }
        }
        viewModelScope.launch {
            ble.events.collect { event -> applyEvent(event) }
        }
        // Wear OS bridge: crown rotation on the watch arrives here as cadence requests.
        viewModelScope.launch {
            WatchCommands.cadenceRequests.collect { rpm -> setCadence(rpm) }
        }
        // Wear OS bridge: heart rate measured on the watch.
        viewModelScope.launch {
            WatchCommands.heartRateReports.collect { bpm ->
                _state.update { it.copy(heartRateBpm = bpm) }
                AppSettings.markHeartRateSeen(getApplication())
            }
        }
        // Push to the watch on a throttled ticker rather than on every state emission —
        // ride tracking alone updates state once a second, and pushing that continuously
        // forces a Data Layer sync every time, draining the watch's battery for no real
        // benefit (a few seconds of staleness on a wrist display is unnoticeable).
        viewModelScope.launch {
            while (true) {
                PhoneWearBridge.pushState(getApplication(), _state.value)
                delay(3_000)
            }
        }
        // Ride history: start tracking once pedaling begins, finalize when the bike disconnects.
        // Runs on a fixed 1s ticker (not a `state` collector) so it can write tripDistanceKm
        // back into `_state` without feeding back into its own trigger.
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                trackRideTick()
                updateConnectionNotification()
            }
        }
        // Watch double-tap: toggle bike lights.
        viewModelScope.launch {
            WatchCommands.toggleLightsRequests.collect { setLights(!_state.value.lightsOn) }
        }
        // Watch startup: re-push current state so the watch always has fresh data.
        viewModelScope.launch {
            WatchCommands.stateRefreshRequests.collect { PhoneWearBridge.pushState(getApplication(), _state.value) }
        }
        // Nav bridge: forward turn-by-turn instructions from Komoot/Google Maps/Waze to the bike display.
        viewModelScope.launch {
            NavBridgeState.instruction.collect { instruction ->
                _state.update { it.copy(navInstruction = instruction) }
                if (instruction != null && _state.value.connectionState == ConnectionState.CONNECTED && !isMock) {
                    val dir = when (instruction.direction) {
                        NavDirection.FORWARD -> BikeWrite.Direction.FORWARD
                        NavDirection.LEFT -> BikeWrite.Direction.LEFT
                        NavDirection.RIGHT -> BikeWrite.Direction.RIGHT
                        NavDirection.U_TURN -> BikeWrite.Direction.BACKWARD
                        NavDirection.ARRIVE -> BikeWrite.Direction.FORWARD
                    }
                    ble.send(BikeWrite.directionDistance(dir, instruction.distanceMeters))
                }
            }
        }
        viewModelScope.launch {
            NavBridgeState.currentTrack.collect { track ->
                _state.update { it.copy(currentTrack = track) }
                if (track != null && _state.value.connectionState == ConnectionState.CONNECTED) {
                    if (_state.value.navInstruction == null && _state.value.displayMode == null) {
                        sendLocationText(track.take(10))
                    }
                }
            }
        }
    }

    private fun onConnected() {
        sendInitialReads()
        setSafetyTrackingAvailable(true)
        viewModelScope.launch {
            delay(5_000)
            if (_state.value.connectionState == ConnectionState.CONNECTED && _state.value.batteryPercent == null) {
                android.util.Log.w("BikeViewModel", "Initial reads incomplete after 5s — retrying")
                sendInitialReads()
            }
        }
        viewModelScope.launch {
            if (enviolo.scanAndConnect()) {
                val multiplier = enviolo.readStartMultiplier()
                val serial = enviolo.readSerialNumber()
                val mode = enviolo.readMode()
                val article = enviolo.readArticleNumber()
                val opMode = enviolo.readOperationMode()
                val minCad = enviolo.readMinCadence()
                val maxCad = enviolo.readMaxCadence()
                val odo = enviolo.readOdometer()
                _state.update {
                    it.copy(
                        startMultiplier = multiplier,
                        envioloSerialNumber = serial,
                        envioloArticleNumber = article,
                        envioloOperationMode = opMode,
                        envioloMinCadence = minCad,
                        envioloMaxCadence = maxCad,
                        envioloOdometer = odo,
                        envioloMode = mode,
                    )
                }
            }
        }
    }

    private fun sendInitialReads() {
        ble.send(BikeRead.displaySerialNumber)
        ble.send(BikeRead.displayHardwareVersion)
        ble.send(BikeRead.displayFirmwareVersion)
        ble.send(BikeRead.experienceModuleFirmwareVersion)
        ble.send(BikeRead.odometer)
        ble.send(BikeRead.cadence)
        ble.send(BikeRead.lights)
    }

    private fun applyEvent(event: BikeEvent) {
        _state.update { s ->
            when (event) {
                is BikeEvent.BatterySoc -> s.copy(batteryPercent = event.percent)
                is BikeEvent.BatterySoh -> s.copy(batteryHealthPercent = event.percent)
                is BikeEvent.BatteryCharging -> s.copy(charging = event.charging)
                is BikeEvent.Speed -> s.copy(
                    speedKmh = event.kmh,
                    maxSpeedKmh = maxOf(s.maxSpeedKmh, event.kmh)
                )
                is BikeEvent.Odometer -> s.copy(odometer = event.raw)
                is BikeEvent.Cadence -> s.copy(cadenceRpm = event.rpm)
                is BikeEvent.DisplaySerial -> s.copy(displaySerial = event.serial)
                is BikeEvent.DisplayHardwareVersion -> s.copy(displayHardwareVersion = event.version)
                is BikeEvent.DisplayFirmwareVersion -> s.copy(displayFirmwareVersion = event.version)
                is BikeEvent.ExperienceModuleFirmwareVersion -> s.copy(experienceModuleFirmwareVersion = event.version)
                is BikeEvent.LightsStatus -> s.copy(lightsOn = event.on)
                is BikeEvent.SafetyTrackingResponse -> s.copy(hasEmergencyContact = event.hasEmergencyContact)
                is BikeEvent.MotorConfig -> s.copy(maxMotorSpeedKmh = event.maxSpeedKmh)
                else -> s
            }
        }
        if (event is BikeEvent.Speed) checkSpeedAlert(event.kmh)
        if (event is BikeEvent.BatterySoc) checkBatteryLow(event.percent)
        if (event is BikeEvent.BatterySoc || event is BikeEvent.Speed || event is BikeEvent.Odometer) {
            val s = _state.value
            BikeWidgetProvider.pushState(getApplication(), s.connectedName, s.batteryPercent, s.speedKmh, s.odometer)
        }
        // The bike's handlebar long-press (normally the SOS/emergency-contact button)
        // sends this same unauthenticated notification — repurposed here to trigger launch control.
        if (event is BikeEvent.SafetyTrackingResponse) launchControl()
    }

    // --- speed alert ---

    private var speedAlertFired = false

    private fun checkSpeedAlert(kmh: Double) {
        val app = getApplication<Application>()
        if (!AppSettings.isSpeedAlertEnabled(app)) return
        val threshold = AppSettings.speedAlertThresholdKmh(app)
        if (kmh >= threshold && !speedAlertFired) {
            speedAlertFired = true
            vibrate()
        } else if (kmh < threshold - 1) {
            speedAlertFired = false
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = app.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val v = app.getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    // --- battery low notification ---

    private var batteryLowNotified20 = false
    private var batteryLowNotified10 = false

    private fun checkBatteryLow(percent: Int) {
        val app = getApplication<Application>()
        if (!AppSettings.isBatteryLowAlertEnabled(app)) return
        val alertThreshold = AppSettings.batteryLowAlertThreshold(app)
        val halfThreshold = alertThreshold / 2
        if (percent > alertThreshold) {
            batteryLowNotified20 = false
            batteryLowNotified10 = false
            return
        }
        val nm = app.getSystemService(android.app.NotificationManager::class.java)
        if (nm.getNotificationChannel("battery_low") == null) {
            nm.createNotificationChannel(
                android.app.NotificationChannel("battery_low", "Battery Low", android.app.NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val level = if (percent <= halfThreshold && !batteryLowNotified10) {
            batteryLowNotified10 = true; percent
        } else if (percent <= alertThreshold && !batteryLowNotified20) {
            batteryLowNotified20 = true; percent
        } else return

        val openApp = android.app.PendingIntent.getActivity(
            app, 0,
            android.content.Intent(app, com.velogappie.app.MainActivity::class.java).setPackage(app.packageName),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(9011, androidx.core.app.NotificationCompat.Builder(app, "battery_low")
            .setSmallIcon(R.drawable.ic_notif_bike)
            .setContentTitle(app.getString(R.string.notif_battery_low_title))
            .setContentText(app.getString(R.string.notif_battery_low_text, level))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        )
    }

    // --- ride history ---
    //
    // Distance is integrated from speed*time rather than read from the bike's odometer
    // field, since that field's unit is unconfirmed. Sampled
    // on the fixed 1s ticker above, started as soon as the bike is connected and
    // moving/pedaling, finalized (and persisted) as soon as that's no longer true.

    private var rideIdleSince: Long = 0L

    private fun trackRideTick() {
        val s = _state.value
        val moving = s.connectionState == ConnectionState.CONNECTED &&
            ((s.speedKmh ?: 0.0) > 0.5 || (s.cadenceRpm ?: 0) > 0)

        val ride = activeRide
        if (ride == null) {
            if (moving) {
                activeRide = ActiveRide(startTime = System.currentTimeMillis(), startBatterySoc = s.batteryPercent)
                rideIdleSince = 0L
                _state.update { it.copy(tripDistanceKm = 0.0) }
                startRideLocationTracking()
                if (AppSettings.isAutoStartWatchOnPedalEnabled(getApplication())) {
                    PhoneWearBridge.openWatchApp(getApplication())
                }
            }
            return
        }

        if (!moving) {
            if (rideIdleSince == 0L) rideIdleSince = System.currentTimeMillis()
            val reconnecting = reconnectJob?.isActive == true
            if (!reconnecting &&
                (System.currentTimeMillis() - rideIdleSince >= RIDE_IDLE_GRACE_MS
                    || s.connectionState != ConnectionState.CONNECTED)) {
                stopRideLocationTracking()
                finalizeRide(ride)
                clearPersistedActiveRide()
                activeRide = null
                rideIdleSince = 0L
            }
            return
        }
        rideIdleSince = 0L

        val speed = s.speedKmh ?: 0.0
        ride.distanceKm += speed / 3_600.0
        ride.speedSampleSum += speed
        ride.speedSampleCount++
        ride.maxSpeedKmh = maxOf(ride.maxSpeedKmh, speed)
        s.heartRateBpm?.let { ride.heartRateSamples += System.currentTimeMillis() to it }
        s.cadenceRpm?.let { rpm ->
            if (rpm > 0) {
                ride.cadenceSampleSum += rpm
                ride.cadenceSampleCount++
                ride.maxCadenceRpm = maxOf(ride.maxCadenceRpm, rpm)
            }
        }
        _state.update { it.copy(tripDistanceKm = ride.distanceKm) }
        if (ride.speedSampleCount % 30 == 0) persistActiveRide(ride)
    }

    private fun updateConnectionNotification() {
        val s = _state.value
        if (s.connectionState != ConnectionState.CONNECTED) return
        val app = getApplication<Application>()
        val name = s.connectedName ?: "Bike"
        val ride = activeRide
        val text = if (ride != null) {
            app.getString(R.string.notif_riding, "%.1f".format(ride.distanceKm))
        } else {
            app.getString(R.string.notif_connected)
        }
        ConnectionService.update(app, name, text)
    }

    private fun finalizeRide(ride: ActiveRide) {
        val endTime = System.currentTimeMillis()
        if (endTime - ride.startTime < MIN_RIDE_DURATION_MS) return
        val avgSpeedKmh = if (ride.speedSampleCount > 0) ride.speedSampleSum / ride.speedSampleCount else 0.0
        val avgHeartRateBpm = ride.heartRateSamples.map { it.second }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val maxHeartRateBpm = ride.heartRateSamples.map { it.second }.maxOrNull()
        val avgCadenceRpm = if (ride.cadenceSampleCount > 0) ride.cadenceSampleSum / ride.cadenceSampleCount else null
        val maxCadenceRpm = if (ride.cadenceSampleCount > 0) ride.maxCadenceRpm else null
        val locationPoints = ride.locationPoints.toList()
        val elevationGain = computeElevationGain(locationPoints)

        clearPersistedActiveRide()
        viewModelScope.launch {
            val rideId = rideDao.insert(
                RideEntity(
                    startTime = ride.startTime,
                    endTime = endTime,
                    distanceKm = ride.distanceKm,
                    avgSpeedKmh = avgSpeedKmh,
                    maxSpeedKmh = ride.maxSpeedKmh,
                    avgHeartRateBpm = avgHeartRateBpm,
                    maxHeartRateBpm = maxHeartRateBpm,
                    avgCadenceRpm = avgCadenceRpm,
                    maxCadenceRpm = maxCadenceRpm,
                    startBatterySoc = ride.startBatterySoc,
                    endBatterySoc = _state.value.batteryPercent,
                    elevationGainM = elevationGain,
                    bikeSerial = connectedSerial,
                )
            )
            if (locationPoints.isNotEmpty()) {
                rideDao.insertLocationPoints(locationPoints.map { it.copy(rideId = rideId) })
            }
            HealthConnectBridge.writeRide(
                context = getApplication(),
                startTimeMs = ride.startTime,
                endTimeMs = endTime,
                distanceKm = ride.distanceKm,
                heartRateSamples = ride.heartRateSamples,
            )
            if (DriveSync.isEnabled(getApplication())) syncToDriveNow()
        }
    }

    private fun computeElevationGain(points: List<LocationPointEntity>): Double? {
        val altitudes = points.mapNotNull { it.altitude }
        if (altitudes.size < 2) return null
        var gain = 0.0
        for (i in 1 until altitudes.size) {
            val diff = altitudes[i] - altitudes[i - 1]
            if (diff > 0) gain += diff
        }
        return gain
    }

    private fun persistActiveRide(ride: ActiveRide) {
        val prefs = getApplication<Application>().getSharedPreferences("active_ride", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("startTime", ride.startTime)
            .putInt("startBatterySoc", ride.startBatterySoc ?: -1)
            .putFloat("distanceKm", ride.distanceKm.toFloat())
            .putFloat("speedSampleSum", ride.speedSampleSum.toFloat())
            .putInt("speedSampleCount", ride.speedSampleCount)
            .putFloat("maxSpeedKmh", ride.maxSpeedKmh.toFloat())
            .putInt("cadenceSampleSum", ride.cadenceSampleSum)
            .putInt("cadenceSampleCount", ride.cadenceSampleCount)
            .putInt("maxCadenceRpm", ride.maxCadenceRpm)
            .apply()
    }

    private fun clearPersistedActiveRide() {
        getApplication<Application>().getSharedPreferences("active_ride", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun recoverActiveRide() {
        val prefs = getApplication<Application>().getSharedPreferences("active_ride", android.content.Context.MODE_PRIVATE)
        val startTime = prefs.getLong("startTime", 0L)
        if (startTime == 0L) return
        val soc = prefs.getInt("startBatterySoc", -1).takeIf { it >= 0 }
        val recovered = ActiveRide(startTime, soc).apply {
            distanceKm = prefs.getFloat("distanceKm", 0f).toDouble()
            speedSampleSum = prefs.getFloat("speedSampleSum", 0f).toDouble()
            speedSampleCount = prefs.getInt("speedSampleCount", 0)
            maxSpeedKmh = prefs.getFloat("maxSpeedKmh", 0f).toDouble()
            cadenceSampleSum = prefs.getInt("cadenceSampleSum", 0)
            cadenceSampleCount = prefs.getInt("cadenceSampleCount", 0)
            maxCadenceRpm = prefs.getInt("maxCadenceRpm", 0)
        }
        finalizeRide(recovered)
    }

    @SuppressLint("MissingPermission")
    private fun startRideLocationTracking() {
        if (rideLocationListener != null) return
        val listener = LocationListener { location ->
            val ride = activeRide ?: return@LocationListener
            ride.locationPoints += LocationPointEntity(
                rideId = 0,
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
            )
        }
        rideLocationListener = listener
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5_000L, 10f, listener,
            )
        } catch (_: SecurityException) {
            rideLocationListener = null
        }
    }

    private fun stopRideLocationTracking() {
        rideLocationListener?.let { locationManager?.removeUpdates(it) }
        rideLocationListener = null
    }

    fun loadLocationPoints(rideId: Long, callback: (List<LocationPointEntity>) -> Unit) {
        viewModelScope.launch {
            callback(rideDao.getLocationPointsForRide(rideId))
        }
    }

    fun deleteRide(id: Long) {
        viewModelScope.launch {
            rideDao.deleteLocationPointsByRideIds(listOf(id))
            rideDao.deleteById(id)
        }
    }

    fun mergeRides(ids: List<Long>) {
        if (ids.size < 2) return
        viewModelScope.launch {
            val rides = rideDao.getByIds(ids).sortedBy { it.startTime }
            if (rides.size < 2) return@launch
            val totalDistance = rides.sumOf { it.distanceKm }
            val totalDuration = rides.sumOf { it.endTime - it.startTime }
            val weightedSpeedSum = rides.sumOf { it.avgSpeedKmh * (it.endTime - it.startTime) }
            val avgSpeed = if (totalDuration > 0) weightedSpeedSum / totalDuration else 0.0
            val maxSpeed = rides.maxOf { it.maxSpeedKmh }
            val heartRates = rides.mapNotNull { it.avgHeartRateBpm }
            val cadences = rides.mapNotNull { it.avgCadenceRpm }
            val elevations = rides.mapNotNull { it.elevationGainM }
            val merged = RideEntity(
                startTime = rides.first().startTime,
                endTime = rides.last().endTime,
                distanceKm = totalDistance,
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = maxSpeed,
                avgHeartRateBpm = heartRates.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                maxHeartRateBpm = rides.mapNotNull { it.maxHeartRateBpm }.maxOrNull(),
                avgCadenceRpm = cadences.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                maxCadenceRpm = rides.mapNotNull { it.maxCadenceRpm }.maxOrNull(),
                startBatterySoc = rides.first().startBatterySoc,
                endBatterySoc = rides.last().endBatterySoc,
                elevationGainM = elevations.takeIf { it.isNotEmpty() }?.sum(),
            )
            val allPoints = ids.flatMap { rideDao.getLocationPointsForRide(it) }
            rideDao.deleteLocationPointsByRideIds(ids)
            rideDao.deleteByIds(ids)
            val mergedId = rideDao.insert(merged)
            if (allPoints.isNotEmpty()) {
                rideDao.insertLocationPoints(allPoints.map { it.copy(id = 0, rideId = mergedId) })
            }
        }
    }

    fun clearRideHistory() {
        viewModelScope.launch {
            rideDao.deleteLocationPointsByRideIds(rides.value.map { it.id })
            rideDao.deleteAll()
        }
    }

    // --- ride groups ---

    fun createGroup(name: String, rideIds: List<Long>) {
        viewModelScope.launch {
            val groupId = rideDao.insertGroup(RideGroupEntity(name = name))
            if (rideIds.isNotEmpty()) rideDao.assignRidesToGroup(rideIds, groupId)
        }
    }

    fun renameGroup(groupId: Long, name: String) {
        viewModelScope.launch { rideDao.renameGroup(groupId, name) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch {
            rideDao.ungroupRidesInGroup(groupId)
            rideDao.deleteGroup(groupId)
        }
    }

    fun addRidesToGroup(rideIds: List<Long>, groupId: Long) {
        viewModelScope.launch { rideDao.assignRidesToGroup(rideIds, groupId) }
    }

    fun removeRidesFromGroup(rideIds: List<Long>) {
        viewModelScope.launch { rideDao.removeRidesFromGroup(rideIds) }
    }

    // --- Google Drive backup (opt-in; see DriveSync) ---

    fun isDriveSyncEnabled(): Boolean = DriveSync.isEnabled(getApplication())

    fun setDriveSyncEnabled(enabled: Boolean) {
        DriveSync.setEnabled(getApplication(), enabled)
        if (enabled) syncToDriveNow()
    }

    /** Silent: only succeeds if Drive access was already granted (e.g. via the Information
     *  tab toggle). If access was revoked since, this just no-ops rather than surfacing a
     *  re-consent prompt from a background context. */
    fun syncToDriveNow() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val result = DriveSync.authorize(context)
                val token = result.accessToken
                if (result.hasResolution() || token == null) return@launch
                val json = DriveSync.buildBackupJson(lastKnownBikeName(), rides.value)
                DriveSync.uploadBackup(context, token, json)
            } catch (e: Exception) {
                android.util.Log.w("BikeViewModel", "Drive sync failed", e)
                _snackbar.emit(getApplication<Application>().getString(R.string.error_drive_sync))
            }
        }
    }

    // --- scanning / connection ---

    fun startScan() = ble.startScan()
    fun stopScan() = ble.stopScan()
    fun connect(device: BluetoothDevice, displayName: String) {
        val serial = displayName.removePrefix("V-")
        _state.update { it.copy(connectedName = displayName) }
        connectedSerial = serial
        val context = getApplication<Application>()
        LastKnownBike.save(context, device.address, displayName)
        KnownBikes.add(context, device.address, displayName, serial)
        BikeProximityScanner.startIfEnabled(context)
        ble.connect(device)
    }

    fun lastKnownBikeName(): String? = LastKnownBike.name(getApplication())

    fun connectLastKnownBike() {
        val context = getApplication<Application>()
        val address = LastKnownBike.address(context) ?: return
        val name = LastKnownBike.name(context) ?: return
        connectedSerial = name.removePrefix("V-")
        _state.update { it.copy(connectedName = name) }
        ble.connectByAddress(address)
    }

    fun connectKnownBike(address: String, name: String) {
        connectedSerial = name.removePrefix("V-")
        _state.update { it.copy(connectedName = name) }
        LastKnownBike.save(getApplication(), address, name)
        ble.connectByAddress(address)
    }

    fun knownBikes() = KnownBikes.getAll(getApplication())

    // --- autostart preferences ---

    fun isAutoStartOnBikeDetectedEnabled(): Boolean = AppSettings.isAutoStartOnBikeDetectedEnabled(getApplication())

    fun setAutoStartOnBikeDetectedEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        AppSettings.setAutoStartOnBikeDetectedEnabled(context, enabled)
        if (enabled) BikeProximityScanner.startIfEnabled(context) else BikeProximityScanner.stop(context)
    }

    fun isAutoStartWatchOnPedalEnabled(): Boolean = AppSettings.isAutoStartWatchOnPedalEnabled(getApplication())

    fun setAutoStartWatchOnPedalEnabled(enabled: Boolean) =
        AppSettings.setAutoStartWatchOnPedalEnabled(getApplication(), enabled)

    fun hasHeartRateEverBeenSeen(): Boolean = AppSettings.hasHeartRateEverBeenSeen(getApplication())

    // --- appearance ---

    fun appTheme(systemIsDark: Boolean): AppTheme = AppSettings.appTheme(getApplication(), systemIsDark)
    fun setAppTheme(theme: AppTheme) = AppSettings.setAppTheme(getApplication(), theme)
    fun appAccent(): AppAccent = AppSettings.appAccent(getApplication())
    fun setAppAccent(accent: AppAccent) = AppSettings.setAppAccent(getApplication(), accent)


    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectJob?.cancel()
        reconnectJob = null
        autoLightsJob?.cancel()
        mockJob?.cancel()
        cycleDisplayJob?.cancel()
        isMock = false
        ble.disconnect()
        enviolo.disconnect()
        _state.value = BikeUiState(navInstruction = NavBridgeState.latestInstruction)
        BikeWidgetProvider.pushDisconnected(getApplication())
        PhoneWearBridge.closeWatchApp(getApplication())
    }

    private fun startReconnect() {
        val address = LastKnownBike.address(getApplication()) ?: run {
            ConnectionService.stop(getApplication())
            return
        }
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val app = getApplication<Application>()
            ConnectionService.start(
                app, _state.value.connectedName ?: "Bike",
                app.getString(R.string.notif_reconnecting),
            )
            repeat(RECONNECT_ATTEMPTS) {
                delay(RECONNECT_INTERVAL_MS)
                if (_state.value.connectionState == ConnectionState.CONNECTED) return@launch
                ble.connectByAddress(address)
                delay(RECONNECT_INTERVAL_MS)
                if (_state.value.connectionState == ConnectionState.CONNECTED) return@launch
            }
            activeRide?.let { ride ->
                stopRideLocationTracking()
                finalizeRide(ride)
                clearPersistedActiveRide()
                activeRide = null
                rideIdleSince = 0L
            }
            ConnectionService.stop(app)
            PhoneWearBridge.closeWatchApp(app)
            _finishApp.emit(Unit)
        }
    }

    // --- Enviolo hub (start multiplier) ---

    /** No-op if no Enviolo hub is connected (state.startMultiplier stays null in that case). */
    fun setStartMultiplier(value: Double) {
        val clamped = value.coerceIn(0.0, 2.55)
        _state.update { it.copy(startMultiplier = clamped) }
        viewModelScope.launch { enviolo.writeStartMultiplier(clamped) }
    }

    /** No-op if no Enviolo hub is connected. */
    fun setEnvioloMode(mode: EnvioloMode) {
        _state.update { it.copy(envioloMode = mode) }
        viewModelScope.launch { enviolo.writeMode(mode) }
    }

    // --- handlebar long-press function ---

    fun handlebarFunction(): HandlebarFunction = AppSettings.handlebarFunction(getApplication())

    fun setHandlebarFunction(function: HandlebarFunction) =
        AppSettings.setHandlebarFunction(getApplication(), function)

    private var launchControlActive = false

    /**
     * Standard handlebar-button function (see applyEvent): sets motor assist and the
     * Enviolo hub's start multiplier to maximum, waits until the bike reaches its max
     * motor-assisted speed, then restores both to whatever they were before. One-shot per
     * long-press — ignores re-triggers while already running, and bails out cleanly if the
     * bike disconnects mid-wait (still restores launchControlActive so a future reconnect
     * + long-press can retry).
     */
    fun launchControl() {
        if (launchControlActive) return
        launchControlActive = true
        viewModelScope.launch {
            val originalAssist = _state.value.motorAssistLevel
            val originalMultiplier = _state.value.startMultiplier
            setMotorAssistLevel(4)
            if (originalMultiplier != null) enviolo.writeStartMultiplier(2.55)
            val targetSpeed = _state.value.maxMotorSpeedKmh?.toDouble() ?: 25.0
            while (_state.value.connectionState == ConnectionState.CONNECTED &&
                (_state.value.speedKmh ?: 0.0) < targetSpeed
            ) {
                delay(200)
            }
            setMotorAssistLevel(originalAssist)
            if (originalMultiplier != null) enviolo.writeStartMultiplier(originalMultiplier)
            launchControlActive = false
        }
    }


    // --- controls ---

    fun setMotorAssistLevel(level: Int) {
        if (!isMock) ble.send(BikeWrite.motorAssistLevel(level))
        _state.update { it.copy(motorAssistLevel = level) }
    }

    fun setLights(on: Boolean) {
        if (!isMock) ble.send(BikeWrite.lights(on))
        _state.update { it.copy(lightsOn = on) }
    }

    fun setBellSound(ping: Boolean) {
        if (!isMock) ble.send(BikeWrite.bellSound(ping))
    }

    /** 1 rpm precision — caller (UI, or the watch crown) controls the exact integer, no 5-rpm snapping. */
    fun setCadence(rpm: Int) {
        if (!isMock) ble.send(BikeWrite.cadence(rpm))
        _state.update { it.copy(cadenceRpm = rpm) }
    }

    fun setSafetyTrackingAvailable(on: Boolean) {
        if (!isMock) ble.send(BikeWrite.safetyTrackingAvailable(on))
        _state.update { it.copy(hasEmergencyContact = on) }
    }

    fun sendDirection(direction: BikeWrite.Direction, meters: Int) {
        if (!isMock) ble.send(BikeWrite.directionDistance(direction, meters))
    }

    fun sendWeather(tempC: Int, precipPercent: Int, icon: Boolean) {
        if (!isMock) ble.send(BikeWrite.weather(tempC, precipPercent, icon))
    }

    fun sendLocationText(text: String) {
        if (!isMock) ble.send(BikeWrite.location(text))
    }

    fun setDisplayMode(mode: DisplayMode?) {
        cycleDisplayJob?.cancel()
        _state.update { it.copy(displayMode = mode) }
        if (mode == null) {
            sendLocationText("")
            return
        }
        cycleDisplayJob = viewModelScope.launch {
            var showHeartRate = false
            while (true) {
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val hr = _state.value.heartRateBpm
                when (mode) {
                    DisplayMode.CLOCK -> sendLocationText("%02d:%02d".format(hour, minute))
                    DisplayMode.CLOCK_SPLIT -> {
                        sendLocationText("%02d".format(hour))
                        sendWeather(minute, 0, false)
                    }
                    DisplayMode.HEART_RATE -> sendLocationText(hr?.let { "$it bpm" } ?: "%02d:%02d".format(hour, minute))
                    DisplayMode.CLOCK_HR -> {
                        val text = if (showHeartRate && hr != null) "$hr bpm" else "%02d:%02d".format(hour, minute)
                        showHeartRate = !showHeartRate
                        sendLocationText(text)
                    }
                }
                delay(if (mode == DisplayMode.CLOCK || mode == DisplayMode.CLOCK_SPLIT) 60_000 else 5_000)
            }
        }
    }

    /**
     * Automatic lights: polls the phone's last-known location (no network, GPS/network
     * provider only) and the locally computed sunset time, toggles bike lights on/off.
     * Never leaves the device.
     */
    fun setAutoLights(enabled: Boolean) {
        _state.update { it.copy(autoLightsEnabled = enabled) }
        autoLightsJob?.cancel()
        if (!enabled) return
        autoLightsJob = viewModelScope.launch {
            while (true) {
                val location = lastKnownLocation()
                if (location != null) {
                    val pastSunset = SunCalculator.isPastSunset(location.latitude, location.longitude, TimeZone.getDefault())
                    if (pastSunset != _state.value.lightsOn) setLights(pastSunset)
                }
                delay(5 * 60_000)
            }
        }
    }

    fun setWeatherDisplay(enabled: Boolean) {
        weatherDisplayJob?.cancel()
        _state.update { it.copy(weatherDisplayEnabled = enabled) }
        AppSettings.setWeatherDisplayEnabled(getApplication(), enabled)
        if (!enabled) {
            sendWeather(0, 0, false)
            return
        }
        weatherDisplayJob = viewModelScope.launch {
            while (true) {
                val location = lastKnownLocation()
                if (location != null) {
                    val data = WeatherFetcher.fetch(location.latitude, location.longitude)
                    if (data != null) sendWeather(data.tempC, data.precipPercent, data.isSunny)
                    else _snackbar.emit(getApplication<Application>().getString(R.string.error_weather))
                }
                delay(15 * 60_000)
            }
        }
    }

    private fun lastKnownLocation(): Location? = try {
        locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
    } catch (_: SecurityException) {
        null
    }

    // --- app update ---

    private fun checkForUpdate() {
        val app = getApplication<Application>()
        if (!AppSettings.isUpdateCheckEnabled(app)) return
        viewModelScope.launch {
            val currentVersion = try {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: return@launch
            } catch (_: Exception) { return@launch }
            val update = AppUpdateChecker.check(currentVersion) ?: return@launch
            if (update.version == AppSettings.updateSkippedVersion(app)) return@launch
            _updateState.value = UpdateState(
                available = true,
                version = update.version,
                changelog = update.changelog,
                apkUrl = update.apkUrl,
            )
        }
    }

    fun downloadAndInstallUpdate() {
        val s = _updateState.value
        if (!s.available || s.downloading) return
        _updateState.value = s.copy(downloading = true, progress = 0, error = false)
        viewModelScope.launch {
            val file = AppUpdateChecker.download(getApplication(), s.apkUrl) { progress ->
                _updateState.value = _updateState.value.copy(progress = progress)
            }
            if (file != null) {
                _updateState.value = _updateState.value.copy(downloading = false)
                val intent = AppUpdateChecker.installIntent(getApplication(), file)
                getApplication<Application>().startActivity(intent)
            } else {
                _updateState.value = _updateState.value.copy(downloading = false, error = true)
            }
        }
    }

    fun skipUpdate() {
        val version = _updateState.value.version
        if (version.isNotEmpty()) {
            AppSettings.setUpdateSkippedVersion(getApplication(), version)
        }
        _updateState.value = UpdateState()
    }

    fun disableUpdateCheck() {
        AppSettings.setUpdateCheckEnabled(getApplication(), false)
        _updateState.value = UpdateState()
    }

    override fun onCleared() {
        try {
            Wearable.getDataClient(getApplication<Application>()).removeListener(dataClientListener)
        } catch (e: Exception) {
            // No watch support available on this device — nothing to clean up.
        }
    }
}
