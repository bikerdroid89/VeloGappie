package com.velogappie.app.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velogappie.app.BikeUiState
import com.velogappie.app.DisplayMode
import com.velogappie.app.R
import com.velogappie.app.ble.EnvioloMode
import com.velogappie.app.nav.NavDirection
import com.velogappie.app.nav.NavInstruction
import com.velogappie.app.ui.theme.AppShapes
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: BikeUiState,
    bikePhoto: Bitmap?,
    bikeModelName: String?,
    onBikePhotoTap: () -> Unit,
    onSetAssistLevel: (Int) -> Unit,
    onSetLights: (Boolean) -> Unit,
    onSetAutoLights: (Boolean) -> Unit,
    onSetBellSound: (Boolean) -> Unit,
    onSetCadence: (Int) -> Unit,
    onSetDisplayMode: (DisplayMode?) -> Unit,
    onSetWeatherDisplay: (Boolean) -> Unit,
    onSetStartMultiplier: (Double) -> Unit,
    onSetEnvioloMode: (EnvioloMode) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val velo = LocalVeloColors.current
    var cadenceValue by rememberSaveable { mutableIntStateOf(0) }
    var pingSelected by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.cadenceRpm) { state.cadenceRpm?.let { cadenceValue = it } }

    val lightsMode = when {
        state.autoLightsEnabled -> 2
        state.lightsOn -> 1
        else -> 0
    }

    var showLightsSheet by remember { mutableStateOf(false) }
    var showBellSheet by remember { mutableStateOf(false) }
    var showDisplaySheet by remember { mutableStateOf(false) }
    var showRideModeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Navigation card overlay
        AnimatedVisibility(
            visible = state.navInstruction != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            state.navInstruction?.let { NavigationCard(it) }
        }

        // Music track
        AnimatedVisibility(
            visible = state.currentTrack != null && state.navInstruction == null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            state.currentTrack?.let { track ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = AppShapes.Card,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("♪", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = track,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // ── HERO CARD ──────────────────────────────────────────────
        HeroCard(
            state = state,
            bikePhoto = bikePhoto,
            bikeModelName = bikeModelName,
            onBikePhotoTap = onBikePhotoTap,
        )

        // ── ASSIST LEVEL ───────────────────────────────────────────
        AssistLevelControl(
            currentLevel = state.motorAssistLevel,
            onSetLevel = { level ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSetAssistLevel(level)
            },
        )

        // ── CADENCE + STARTBOOST ───────────────────────────────────
        if (state.cadenceRpm != null || state.startMultiplier != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (state.cadenceRpm != null) {
                    StepperTile(
                        label = stringResource(R.string.label_cadence),
                        value = "$cadenceValue ${stringResource(R.string.unit_rpm)}",
                        onMinus = {
                            cadenceValue = (cadenceValue - 1).coerceIn(30, 120)
                            onSetCadence(cadenceValue)
                        },
                        onPlus = {
                            cadenceValue = (cadenceValue + 1).coerceIn(30, 120)
                            onSetCadence(cadenceValue)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (state.startMultiplier != null) {
                    StepperTile(
                        label = stringResource(R.string.dashboard_start_multiplier_title),
                        value = "%.2f×".format(state.startMultiplier),
                        onMinus = { onSetStartMultiplier(state.startMultiplier - 0.05) },
                        onPlus = { onSetStartMultiplier(state.startMultiplier + 0.05) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── QUICK GRID ─────────────────────────────────────────────
        val lightsModeLabel = when (lightsMode) {
            0 -> stringResource(R.string.lights_off)
            1 -> stringResource(R.string.lights_manual_on)
            else -> stringResource(R.string.lights_auto)
        }
        val bellLabel = if (pingSelected) stringResource(R.string.bell_ping) else stringResource(R.string.bell_default)
        val displayModeLabel = when (state.displayMode) {
            DisplayMode.CLOCK -> stringResource(R.string.display_mode_clock)
            DisplayMode.CLOCK_SPLIT -> stringResource(R.string.display_mode_clock_split)
            DisplayMode.HEART_RATE -> stringResource(R.string.display_mode_heart_rate)
            DisplayMode.CLOCK_HR -> stringResource(R.string.display_mode_clock_hr)
            null -> stringResource(R.string.display_mode_clock)
        }
        val rideModeLabel = when (state.envioloMode) {
            EnvioloMode.ECONOMY -> stringResource(R.string.enviolo_mode_economy)
            EnvioloMode.COMFORT -> stringResource(R.string.enviolo_mode_comfort)
            EnvioloMode.SPORT -> stringResource(R.string.enviolo_mode_sport)
            null -> stringResource(R.string.enviolo_mode_sport)
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                QuickTile(
                    icon = { Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.size(22.dp), tint = velo.textDim) },
                    label = stringResource(R.string.label_lights),
                    value = lightsModeLabel,
                    onClick = { showLightsSheet = true },
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = { Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(22.dp), tint = velo.textDim) },
                    label = stringResource(R.string.label_bell),
                    value = bellLabel,
                    onClick = { showBellSheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                QuickTile(
                    icon = { Icon(Icons.Filled.Watch, contentDescription = null, modifier = Modifier.size(22.dp), tint = velo.textDim) },
                    label = stringResource(R.string.bike_display_title),
                    value = displayModeLabel,
                    onClick = { showDisplaySheet = true },
                    modifier = Modifier.weight(1f),
                )
                QuickTile(
                    icon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary) },
                    label = stringResource(R.string.dashboard_enviolo_mode_title),
                    value = rideModeLabel,
                    onClick = { showRideModeSheet = true },
                    modifier = Modifier.weight(1f),
                )
            }
        }

    }

    // ── BOTTOM SHEETS ──────────────────────────────────────────
    if (showLightsSheet) {
        ModalBottomSheet(onDismissRequest = { showLightsSheet = false }) {
            LightsSheetContent(
                lightsMode = lightsMode,
                onSetMode = { mode ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    when (mode) {
                        0 -> { onSetAutoLights(false); onSetLights(false) }
                        1 -> { onSetAutoLights(false); onSetLights(true) }
                        2 -> { onSetAutoLights(true) }
                    }
                },
            )
        }
    }

    if (showBellSheet) {
        ModalBottomSheet(onDismissRequest = { showBellSheet = false }) {
            BellSheetContent(
                pingSelected = pingSelected,
                onSetBell = { ping ->
                    pingSelected = ping
                    onSetBellSound(ping)
                },
            )
        }
    }

    if (showDisplaySheet) {
        ModalBottomSheet(onDismissRequest = { showDisplaySheet = false }) {
            DisplaySheetContent(
                currentMode = state.displayMode,
                weatherEnabled = state.weatherDisplayEnabled,
                onSetMode = onSetDisplayMode,
                onSetWeather = onSetWeatherDisplay,
            )
        }
    }

    if (showRideModeSheet) {
        ModalBottomSheet(onDismissRequest = { showRideModeSheet = false }) {
            RideModeSheetContent(
                currentMode = state.envioloMode,
                onSetMode = onSetEnvioloMode,
            )
        }
    }
}

// ── HERO CARD ──────────────────────────────────────────────────────

@Composable
private fun HeroCard(
    state: BikeUiState,
    bikePhoto: Bitmap?,
    bikeModelName: String?,
    onBikePhotoTap: () -> Unit,
) {
    val velo = LocalVeloColors.current
    val displayName = bikeModelName
        ?: if (state.mockMode) stringResource(R.string.dashboard_bike_name_mock)
        else (state.connectedName ?: stringResource(R.string.dashboard_bike_name_default))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = AppShapes.HeroCard,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, velo.hairline),
    ) {
        Column(Modifier.padding(18.dp)) {
            // Top row: bike name + connected pill
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = displayName,
                    fontFamily = HankenGrotesk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.connectionState == com.velogappie.app.ble.ConnectionState.CONNECTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(velo.connectedBg, RoundedCornerShape(13.dp))
                            .padding(horizontal = 11.dp, vertical = 5.dp),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(velo.connectedDot, CircleShape)
                        )
                        Text(
                            stringResource(R.string.dashboard_connected),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = velo.connectedLabel,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bike photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(
                        width = 1.dp,
                        color = velo.hairline,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable(onClick = onBikePhotoTap),
                contentAlignment = Alignment.Center,
            ) {
                if (bikePhoto != null) {
                    Image(
                        bitmap = bikePhoto.asImageBitmap(),
                        contentDescription = stringResource(R.string.dashboard_bike_image_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    var showPlaceholder = true
                    if (showPlaceholder) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.CameraAlt,
                                contentDescription = stringResource(R.string.photo_picker_tap_to_change),
                                modifier = Modifier.size(32.dp),
                                tint = velo.textFaint,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.photo_picker_tap_to_change),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = velo.textFaint,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Battery section
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.label_battery),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textDim,
                )
                state.batteryPercent?.let { pct ->
                    val rangeKm = (pct * 0.55).toInt()
                    Text(
                        "~$rangeKm km bereik",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = velo.textFaint,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val pct = (state.batteryPercent ?: 0).coerceIn(0, 100)
                Box(
                    Modifier
                        .weight(1f)
                        .height(9.dp)
                        .background(velo.hairline, RoundedCornerShape(6.dp))
                ) {
                    if (state.batteryPercent != null) {
                        val barColor = if (pct <= 20) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(pct / 100f)
                                .background(barColor, RoundedCornerShape(6.dp))
                        )
                    }
                }
                Text(
                    state.batteryPercent?.let { "$it%" } ?: "—",
                    fontFamily = HankenGrotesk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Stat row
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(velo.hairline)
            )

            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth()) {
                StatColumn(
                    value = state.speedKmh?.let { "%.1f".format(it) } ?: "—",
                    label = "km/h",
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(velo.hairline)
                )
                StatColumn(
                    value = state.odometer?.toString() ?: "—",
                    label = stringResource(R.string.label_odometer).lowercase(),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(velo.hairline)
                )
                StatColumn(
                    value = "%.1f".format(state.tripDistanceKm),
                    label = stringResource(R.string.label_trip).lowercase(),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    val velo = LocalVeloColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontFamily = HankenGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.4).sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = velo.textFaint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── ASSIST LEVEL CONTROL ───────────────────────────────────────────

@Composable
private fun AssistLevelControl(currentLevel: Int, onSetLevel: (Int) -> Unit) {
    val velo = LocalVeloColors.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.label_motor_assist),
                fontFamily = HankenGrotesk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "0–4 · S = max",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = velo.textFaint,
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val labels = listOf("0", "1", "2", "3", "4", "S")
            labels.forEachIndexed { index, label ->
                val selected = currentLevel == index
                val desc = stringResource(R.string.a11y_assist_level, label)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .then(
                            if (selected) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(16.dp),
                                )
                            } else {
                                Modifier
                                    .background(
                                        Color(0x0AE2E4E0),
                                        RoundedCornerShape(16.dp),
                                    )
                                    .border(
                                        1.dp,
                                        Color(0x1AE2E4E0),
                                        RoundedCornerShape(16.dp),
                                    )
                            }
                        )
                        .clickable { onSetLevel(index) }
                        .semantics { contentDescription = desc },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = HankenGrotesk,
                        fontSize = 18.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (selected) velo.textOnAccent else Color(0xFF989B96),
                    )
                }
            }
        }
    }
}

// ── STEPPER TILE ───────────────────────────────────────────────────

@Composable
private fun StepperTile(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val velo = LocalVeloColors.current
    Box(
        modifier = modifier
            .background(velo.surface2, AppShapes.Tile)
            .border(1.dp, velo.hairline, AppShapes.Tile)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Column {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = velo.textDim,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RoundIconButton(
                    icon = { Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(18.dp), tint = velo.textDim) },
                    onClick = onMinus,
                )
                Text(
                    text = value,
                    fontFamily = HankenGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                RoundIconButton(
                    icon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = velo.textDim) },
                    onClick = onPlus,
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val velo = LocalVeloColors.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .border(1.dp, Color(0x24E2E4E0), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

// ── QUICK TILE ─────────────────────────────────────────────────────

@Composable
private fun QuickTile(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val velo = LocalVeloColors.current
    Box(
        modifier = modifier
            .background(velo.surface2, AppShapes.Tile)
            .border(1.dp, velo.hairline, AppShapes.Tile)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = velo.textFaint.copy(alpha = 0.5f),
                )
            }
            Column {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textFaint,
                )
                Text(
                    text = value,
                    fontFamily = HankenGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

// ── BOTTOM SHEET CONTENTS ──────────────────────────────────────────

@Composable
private fun LightsSheetContent(lightsMode: Int, onSetMode: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.label_lights),
            fontFamily = HankenGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = lightsMode == 0,
                onClick = { onSetMode(0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.lights_off)) }
            SegmentedButton(
                selected = lightsMode == 1,
                onClick = { onSetMode(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.lights_manual_on)) }
            SegmentedButton(
                selected = lightsMode == 2,
                onClick = { onSetMode(2) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.lights_auto)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BellSheetContent(pingSelected: Boolean, onSetBell: (Boolean) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.label_bell),
            fontFamily = HankenGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !pingSelected,
                onClick = { onSetBell(false) },
                label = { Text(stringResource(R.string.bell_default)) },
            )
            FilterChip(
                selected = pingSelected,
                onClick = { onSetBell(true) },
                label = { Text(stringResource(R.string.bell_ping)) },
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplaySheetContent(
    currentMode: DisplayMode?,
    weatherEnabled: Boolean,
    onSetMode: (DisplayMode?) -> Unit,
    onSetWeather: (Boolean) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.bike_display_title),
            fontFamily = HankenGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val modes = listOf(
                DisplayMode.CLOCK to R.string.display_mode_clock,
                DisplayMode.CLOCK_SPLIT to R.string.display_mode_clock_split,
                DisplayMode.HEART_RATE to R.string.display_mode_heart_rate,
                DisplayMode.CLOCK_HR to R.string.display_mode_clock_hr,
            )
            modes.forEach { (mode, labelRes) ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onSetMode(if (currentMode == mode) null else mode) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.display_mode_weather),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = weatherEnabled, onCheckedChange = onSetWeather)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RideModeSheetContent(
    currentMode: EnvioloMode?,
    onSetMode: (EnvioloMode) -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            stringResource(R.string.dashboard_enviolo_mode_title),
            fontFamily = HankenGrotesk,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = currentMode == EnvioloMode.ECONOMY,
                onClick = { onSetMode(EnvioloMode.ECONOMY) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.enviolo_mode_economy)) }
            SegmentedButton(
                selected = currentMode == EnvioloMode.COMFORT,
                onClick = { onSetMode(EnvioloMode.COMFORT) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.enviolo_mode_comfort)) }
            SegmentedButton(
                selected = currentMode == EnvioloMode.SPORT,
                onClick = { onSetMode(EnvioloMode.SPORT) },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                icon = {},
            ) { Text(stringResource(R.string.enviolo_mode_sport)) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ── NAVIGATION CARD ────────────────────────────────────────────────

@Composable
private fun NavigationCard(instruction: NavInstruction) {
    val velo = LocalVeloColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = AppShapes.Card,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = when (instruction.direction) {
                    NavDirection.LEFT -> "←"
                    NavDirection.RIGHT -> "→"
                    NavDirection.U_TURN -> "↓"
                    NavDirection.ARRIVE -> "◉"
                    NavDirection.FORWARD -> "↑"
                },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDistance(instruction.distanceMeters),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                instruction.streetName?.let { street ->
                    Text(
                        text = street,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.nav_card_active),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
                instruction.eta?.let { eta ->
                    Text(
                        text = eta,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Int): String = when {
    meters >= 1000 -> "%.1f km".format(meters / 1000.0)
    else -> "$meters m"
}
