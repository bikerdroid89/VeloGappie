package com.velogappie.wear

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val isAmbient = mutableStateOf(false)
    private val ambientTick = mutableStateOf(0)

    // Drives dim/simplify-on-ambient + brighten/restore-on-exit (wrist raise or tap).
    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                isAmbient.value = true
            }
            override fun onExitAmbient() {
                isAmbient.value = false
            }
            override fun onUpdateAmbient() {
                // System calls this roughly once a minute while ambient — just enough
                // to keep the clock honest without redrawing anything else.
                ambientTick.value++
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BikeStateRepository.init(this)
        lifecycle.addObserver(ambientObserver)
        // Keep the app itself on screen instead of timing out to the system watch face;
        // AmbientLifecycleObserver above takes over for dimming/power saving instead.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme { WatchScreen(isAmbient = isAmbient.value, ambientTick = ambientTick.value) }
        }
    }
}

// --- HR zones ---
// No per-user max-HR/age profile exists anywhere in this app, so these are fixed default
// bands (roughly %max-HR zones for an estimated ~190 max HR) rather than personalized.
private fun hrZone(bpm: Int): Int = when {
    bpm < 114 -> 1
    bpm < 133 -> 2
    bpm < 152 -> 3
    bpm < 171 -> 4
    else -> 5
}

private val zoneColors = mapOf(
    1 to Color(0xFF5C8BD9),
    2 to Color(0xFF46C99A),
    3 to Color(0xFFE8B34A),
    4 to Color(0xFFEE8B47),
    5 to Color(0xFFE85C5C),
)

private fun zoneColor(zone: Int): Color = zoneColors[zone] ?: zoneColors[2]!!
private fun zoneTint(zone: Int): Color = lerp(zoneColor(zone), Color.White, 0.35f)

private const val PAGE_COUNT = 2

@Composable
fun WatchScreen(isAmbient: Boolean, ambientTick: Int) {
    val context = LocalContext.current
    val bikeState by BikeStateRepository.state.collectAsState()
    val speedHistory by BikeStateRepository.speedHistory.collectAsState()
    var cadence by rememberSaveable { mutableIntStateOf(0) }
    var heartRate by rememberSaveable { mutableIntStateOf(0) }
    var hasBodySensorPermission by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    // Array ref so we can mutate without triggering recomposition on every rotary tick
    val scrollAcc = remember { arrayOf(0f) }
    val dragAcc = remember { arrayOf(0f) }

    LaunchedEffect(bikeState.cadenceRpm) { bikeState.cadenceRpm?.let { cadence = it } }

    // Primary path for receiving phone telemetry: the manifest-declared WearableListenerService
    // is kept only as a background-wake fallback, since on real hardware GMS never invoked it
    // at all (confirmed via dumpsys) despite data demonstrably arriving at the transport layer.
    DisposableEffect(Unit) {
        val listener = DataClient.OnDataChangedListener { dataEvents ->
            BikeStateRepository.handleDataEvents(dataEvents)
            for (event in dataEvents) {
                if (event.type == com.google.android.gms.wearable.DataEvent.TYPE_CHANGED &&
                    event.dataItem.uri.path == "/close_watch_app"
                ) {
                    BikeStateRepository.requestClose()
                }
            }
        }
        Wearable.getDataClient(context).addListener(listener)
        onDispose { Wearable.getDataClient(context).removeListener(listener) }
    }

    val activity = context as? Activity
    LaunchedEffect(Unit) {
        BikeStateRepository.closeRequested.collect { activity?.finishAndRemoveTask() }
    }

    val lastUpdate by BikeStateRepository.lastPhoneUpdateMs.collectAsState()
    LaunchedEffect(lastUpdate) {
        if (lastUpdate == 0L) return@LaunchedEffect
        delay(5 * 60 * 1000L)
        if (System.currentTimeMillis() - BikeStateRepository.lastPhoneUpdateMs.value >= 5 * 60 * 1000L) {
            activity?.finishAndRemoveTask()
        }
    }

    // The watch app should only be left via the physical crown — swallow back gestures.
    BackHandler(enabled = true) {}

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isAmbient, ambientTick) {
        if (isAmbient) {
            now = System.currentTimeMillis()
        } else {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    val timeText = remember(now) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    // Elapsed ride time: starts counting once pedaling/moving, resets when stopped — mirrors
    // the phone's own ride-start/stop condition (see BikeViewModel.trackRideTick), tracked
    // locally here since the phone doesn't currently push a ride-start timestamp over.
    val moving = (bikeState.cadenceRpm ?: 0) > 0 || (bikeState.speedKmh ?: 0.0) > 0.5
    var rideStartMs by remember { mutableStateOf<Long?>(null) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(moving, isAmbient) {
        if (!moving) {
            rideStartMs = null
            elapsedSeconds = 0
            return@LaunchedEffect
        }
        val start = rideStartMs ?: System.currentTimeMillis().also { rideStartMs = it }
        if (!isAmbient) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - start) / 1000).toInt()
                delay(1000)
            }
        }
    }
    val elapsedText = remember(elapsedSeconds) {
        val h = elapsedSeconds / 3600
        val m = (elapsedSeconds % 3600) / 60
        val s = elapsedSeconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBodySensorPermission = if (granted) 1 else 2 }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
        // Ask phone to re-push its current state so we have data immediately on open
        PhoneMessenger.sendRequestState(context)
    }

    // Pause the heart rate sensor while ambient/dimmed to save battery.
    if (hasBodySensorPermission == 1 && !isAmbient) {
        HeartRateSensor { bpm ->
            heartRate = bpm
            PhoneMessenger.sendHeartRate(context, bpm)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                // 80 px per cadence step — much less sensitive than 1 step/event
                scrollAcc[0] += event.verticalScrollPixels
                val steps = (scrollAcc[0] / 80f).toInt()
                if (steps != 0) {
                    scrollAcc[0] -= steps * 80f
                    val new = (cadence + steps).coerceIn(30, 120)
                    if (new != cadence) {
                        cadence = new
                        PhoneMessenger.sendCadence(context, cadence)
                    }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(isAmbient) {
                if (!isAmbient) {
                    detectTapGestures(onDoubleTap = { PhoneMessenger.sendToggleLights(context) })
                }
            }
            .pointerInput(isAmbient) {
                if (!isAmbient) {
                    detectHorizontalDragGestures(
                        onDragEnd = { dragAcc[0] = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragAcc[0] += dragAmount
                            // Resolved here (not via a separate effect watching this array) since
                            // mutating a plain array element doesn't notify Compose of a change —
                            // a LaunchedEffect keyed on it would only ever see its initial value.
                            if (dragAcc[0] < -60f && page < PAGE_COUNT - 1) {
                                page += 1
                                dragAcc[0] = 0f
                            } else if (dragAcc[0] > 60f && page > 0) {
                                page -= 1
                                dragAcc[0] = 0f
                            }
                        },
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (isAmbient) {
            // Ambient: minimal, monochrome, static layout — fewer draws, no animated
            // arc/histogram, less burn-in risk, easy on battery.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeText,
                    color = Color(0xFF8A8A8A),
                    style = MaterialTheme.typography.display1,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(6.dp))
                Text("$cadence rpm", color = Color(0xFF5A5A5A), style = MaterialTheme.typography.title3)
            }
        } else {
            when (page) {
                0 -> ActiveRideScreen(
                    elapsedText = elapsedText,
                    cadence = cadence,
                    heartRate = heartRate,
                    speedKmh = bikeState.speedKmh,
                )
                else -> SecondaryInfoScreen(
                    bikeState = bikeState,
                    speedHistory = speedHistory,
                )
            }

            PageIndicatorDots(pageCount = PAGE_COUNT, currentPage = page, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * Primary "active ride" page. Drops CSS-style drop-shadow glows from the design mock
 * (arc fill, end-marker dot, heart icon) — those require multi-pass blur draws or
 * RenderEffect (API 31+) in Compose, continuously re-rendered on an always-on watch screen;
 * not worth the battery cost for a glow.
 */
@Composable
private fun ActiveRideScreen(elapsedText: String, cadence: Int, heartRate: Int, speedKmh: Double?) {
    val zone = if (heartRate > 0) hrZone(heartRate) else 2
    val zColor = zoneColor(zone)
    val zTint = zoneTint(zone)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF0D1014),
                        0.58f to Color(0xFF050608),
                        1f to Color.Black,
                    ),
                    center = Offset.Unspecified,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        ProgressArc(speedKmh = speedKmh, zoneColor = zColor, zoneTint = zTint)

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(180.dp)) {
            Text(
                text = elapsedText,
                color = Color(0xFFE8ECF2).copy(alpha = 0.5f),
                fontSize = 19.sp,
                lineHeight = 20.sp,
            )
            Text(
                text = "$cadence",
                color = Color(0xFFF4F7FB),
                fontSize = 64.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2).sp,
            )
            Text(
                text = "RPM",
                color = Color(0xFFE8ECF2).copy(alpha = 0.46f),
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 4.dp),
            )

            if (heartRate > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    val pulse by rememberInfiniteTransition(label = "heartPulse").animateFloat(
                        initialValue = 1f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1100
                                1f at 0
                                1.22f at 154
                                1f at 308
                                1.12f at 462
                                1f at 616
                                1f at 1100
                            },
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "heartPulseValue",
                    )
                    Text("♥", color = zColor, fontSize = 14.sp, modifier = Modifier.scale(pulse))
                    Text(text = "$heartRate", color = zTint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "BPM",
                        color = zTint.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                    )
                    Box(
                        modifier = Modifier
                            .background(zColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "Z$zone",
                            color = Color(0xFF0A0A0A),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
    }
}

/** Bezel progress arc: 280° sweep with an 80° gap centered at the bottom, fill driven by
 *  live speed (0-30 km/h), color driven by the rider's current HR zone. */
@Composable
private fun ProgressArc(speedKmh: Double?, zoneColor: Color, zoneTint: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val strokeWidth = 5.dp.toPx()
        val radius = size.minDimension / 2f * 0.94f
        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val startAngle = 130f
        val sweepTotal = 280f

        drawArc(
            color = Color.White.copy(alpha = 0.07f),
            startAngle = startAngle,
            sweepAngle = sweepTotal,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        val fraction = ((speedKmh ?: 0.0) / 30.0).coerceIn(0.0, 1.0)
        val fillSweep = (sweepTotal * fraction).toFloat()
        if (fillSweep > 0.5f) {
            drawArc(
                color = zoneColor,
                startAngle = startAngle,
                sweepAngle = fillSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            val endAngleRad = Math.toRadians((startAngle + fillSweep).toDouble())
            val center = Offset(size.width / 2f, size.height / 2f)
            val dot = Offset(
                center.x + radius * cos(endAngleRad).toFloat(),
                center.y + radius * sin(endAngleRad).toFloat(),
            )
            drawCircle(color = zoneTint, radius = 5.dp.toPx(), center = dot)
        }
    }
}

/** Page 2: everything the active-ride screen doesn't show — connection/battery/assist/lights
 *  and the speed histogram. Nothing from before this redesign is lost, just moved here. */
@Composable
private fun SecondaryInfoScreen(bikeState: WatchBikeState, speedHistory: List<Double>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(horizontal = 22.dp).fillMaxSize(),
    ) {
        StatusRow(connected = bikeState.connected, mock = bikeState.mock, battery = bikeState.batteryPercent)
        Spacer(Modifier.height(4.dp))
        BatteryBar(batteryPercent = bikeState.batteryPercent)

        Spacer(Modifier.height(20.dp))
        AssistAndLights(assistLevel = bikeState.assistLevel, lightsOn = bikeState.lightsOn)

        Spacer(Modifier.height(20.dp))
        SpeedHistogram(history = speedHistory)
        bikeState.speedKmh?.let { speed ->
            Spacer(Modifier.height(4.dp))
            Text("%.1f km/h".format(speed), color = Color(0xFF546E7A), style = MaterialTheme.typography.caption3)
        }
    }
}

@Composable
private fun PageIndicatorDots(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until pageCount) {
            val active = i == currentPage
            Box(
                Modifier
                    .size(width = if (active) 16.dp else 5.dp, height = 5.dp)
                    .background(
                        if (active) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.28f),
                        RoundedCornerShape(100.dp),
                    )
            )
        }
    }
}

@Composable
private fun BatteryBar(batteryPercent: Int?) {
    val pct = (batteryPercent ?: 0).coerceIn(0, 100)
    val color = when {
        pct > 50 -> Color(0xFF4CAF50)
        pct > 20 -> Color(0xFFFFB300)
        else -> Color(0xFFEF5350)
    }
    Box(
        Modifier
            .width(44.dp)
            .height(5.dp)
            .background(Color(0xFF1C2B30), RoundedCornerShape(2.dp))
    ) {
        if (batteryPercent != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct / 100f)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** Tiny rolling bar chart of the last ~20 speed samples, same 0-30 km/h scale as the arc. */
@Composable
private fun SpeedHistogram(history: List<Double>) {
    Canvas(Modifier.width(64.dp).height(18.dp)) {
        if (history.isEmpty()) return@Canvas
        val barWidth = size.width / history.size
        history.forEachIndexed { i, speed ->
            val fraction = (speed / 30.0).coerceIn(0.0, 1.0).toFloat()
            val barHeight = size.height * fraction
            drawRect(
                color = Color(0xFF4FC3F7),
                topLeft = Offset(i * barWidth, size.height - barHeight),
                size = Size(barWidth * 0.7f, barHeight),
            )
        }
    }
}

@Composable
private fun StatusRow(connected: Boolean, mock: Boolean, battery: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(6.dp)) {
            drawCircle(if (connected) Color(0xFF4CAF50) else Color(0xFF546E7A))
        }
        Text(
            text = if (mock) "mock" else if (connected) "bike" else "no bike",
            color = Color(0xFF607D8B),
            style = MaterialTheme.typography.caption3,
        )
        if (battery != null) {
            Text(
                "$battery%",
                color = when {
                    battery > 50 -> Color(0xFF4CAF50)
                    battery > 20 -> Color(0xFFFFB300)
                    else -> Color(0xFFEF5350)
                },
                style = MaterialTheme.typography.caption3,
            )
        }
    }
}

@Composable
private fun AssistAndLights(assistLevel: Int, lightsOn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        // Assist level: 4 orange dots
        for (i in 1..4) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (i <= assistLevel) Color(0xFFFF9800) else Color(0xFF263238),
                        CircleShape,
                    )
            )
            if (i < 4) Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(12.dp))
        // Lights: yellow circle when on
        Box(
            Modifier
                .size(16.dp)
                .background(if (lightsOn) Color(0xFFFFD54F) else Color(0xFF263238), CircleShape)
        )
    }
}

@Composable
private fun HeartRateSensor(onHeartRate: (Int) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val bpm = event.values.firstOrNull()?.toInt() ?: return
                if (bpm > 0) onHeartRate(bpm)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(listener) }
    }
}
