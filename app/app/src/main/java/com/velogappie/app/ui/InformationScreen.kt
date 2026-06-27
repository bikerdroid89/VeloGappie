package com.velogappie.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velogappie.app.BikeUiState
import com.velogappie.app.R
import com.velogappie.app.ble.EnvioloMode
import com.velogappie.app.ride.RideEntity
import com.velogappie.app.ui.theme.AppShapes
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors
import com.velogappie.app.update.UpdateState

@Composable
fun InformationScreen(
    state: BikeUiState,
    rides: List<RideEntity> = emptyList(),
    updateState: UpdateState = UpdateState(),
    onDownloadUpdate: () -> Unit = {},
    onSkipUpdate: () -> Unit = {},
    onDisableUpdateCheck: () -> Unit = {},
) {
    val context = LocalContext.current
    val velo = LocalVeloColors.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }
    val dash = stringResource(R.string.placeholder_dash)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Connection header ────────────────────────────────────
        Card(
            shape = AppShapes.Card,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(velo.connectedDot, CircleShape)
                    )
                    Column {
                        Text(
                            stringResource(R.string.dashboard_connected),
                            fontFamily = HankenGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.01).sp,
                        )
                        Text(
                            state.connectedName ?: dash,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = velo.textDim2,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }

        if (updateState.available) {
            UpdateCard(
                updateState = updateState,
                onDownload = onDownloadUpdate,
                onSkip = onSkipUpdate,
                onDisable = onDisableUpdateCheck,
            )
        }

        // ── Live ─────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.info_section_live))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            StatTile(
                icon = Icons.Filled.BatteryChargingFull,
                label = stringResource(R.string.info_battery),
                value = state.batteryPercent?.toString() ?: dash,
                unit = "%",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Filled.HealthAndSafety,
                label = stringResource(R.string.info_battery_health),
                value = state.batteryHealthPercent?.toString() ?: dash,
                unit = "%",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            StatTile(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.info_speed),
                value = state.speedKmh?.let { "%.1f".format(it) } ?: dash,
                unit = " km/h",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                icon = Icons.Filled.Speed,
                label = stringResource(R.string.info_cadence),
                value = state.cadenceRpm?.toString() ?: dash,
                unit = " rpm",
                modifier = Modifier.weight(1f),
            )
        }

        // ── Enviolo hub ──────────────────────────────────────────
        if (state.startMultiplier != null || state.envioloSerialNumber != null) {
            SectionHeader(stringResource(R.string.info_enviolo_title))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SpecRow(stringResource(R.string.info_enviolo_serial), state.envioloSerialNumber ?: dash)
                    SpecRow(
                        stringResource(R.string.info_enviolo_mode),
                        when (state.envioloMode) {
                            EnvioloMode.ECONOMY -> stringResource(R.string.enviolo_mode_economy)
                            EnvioloMode.COMFORT -> stringResource(R.string.enviolo_mode_comfort)
                            EnvioloMode.SPORT -> stringResource(R.string.enviolo_mode_sport)
                            null -> dash
                        },
                    )
                    state.envioloMinCadence?.let { min ->
                        val max = state.envioloMaxCadence
                        SpecRow(
                            stringResource(R.string.info_enviolo_cadence_range),
                            if (max != null) "$min–$max rpm" else "$min rpm",
                        )
                    }
                    SpecRow(
                        stringResource(R.string.info_enviolo_odometer),
                        state.envioloOdometer?.let { "$it km" } ?: dash,
                        last = true,
                    )
                }
            }
        }

        // ── Display info ─────────────────────────────────────────
        SectionHeader(stringResource(R.string.info_section_display))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SpecRow(stringResource(R.string.info_display_serial), state.displaySerial ?: dash)
                SpecRow(stringResource(R.string.info_display_hardware), state.displayHardwareVersion ?: dash)
                SpecRow(stringResource(R.string.info_display_firmware), state.displayFirmwareVersion ?: dash)
                SpecRow(stringResource(R.string.info_experience_module_firmware), state.experienceModuleFirmwareVersion ?: dash, last = true)
            }
        }

        // ── Battery efficiency ───────────────────────────────────
        BatteryEfficiencyCard(rides)

        // ── Footer ───────────────────────────────────────────────
        Column {
            Text(
                stringResource(R.string.disclaimer_text),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = velo.textFaint,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.info_version, versionName ?: dash),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = velo.textFaint,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val velo = LocalVeloColors.current
    Text(
        title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = velo.textFaint,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val velo = LocalVeloColors.current
    Card(
        shape = AppShapes.Tile,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 9.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = velo.accent,
                )
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textDim2,
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontFamily = HankenGrotesk,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).sp,
                )
                if (value != "—") {
                    Text(
                        unit,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = velo.textFaint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String, last: Boolean = false) {
    val velo = LocalVeloColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = velo.textDim2,
            )
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!last) {
            HorizontalDivider(color = velo.hairline)
        }
    }
}

private data class EfficiencyPoint(val rideIndex: Int, val pctPerKm: Double)

@Composable
private fun BatteryEfficiencyCard(rides: List<RideEntity>) {
    val velo = LocalVeloColors.current
    val points = remember(rides) {
        rides
            .filter { it.startBatterySoc != null && it.endBatterySoc != null && it.distanceKm >= 0.5 }
            .sortedBy { it.startTime }
            .mapIndexed { i, ride ->
                val used = ride.startBatterySoc!! - ride.endBatterySoc!!
                if (used > 0) EfficiencyPoint(i, used.toDouble() / ride.distanceKm) else null
            }
            .filterNotNull()
    }
    if (points.size < 3) return

    val overallAvg = points.map { it.pctPerKm }.average()

    val accentColor = velo.accent

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = velo.accent,
                    )
                    Text(
                        stringResource(R.string.battery_efficiency_title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "%.1f%%/km".format(overallAvg),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textDim2,
                )
            }

            Spacer(Modifier.height(12.dp))

            val maxVal = points.maxOf { it.pctPerKm }.coerceAtLeast(1.0)
            val dashLineColor = velo.hairline

            Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                val w = size.width
                val h = size.height
                val padBottom = 4f
                val usableH = h - padBottom
                val xStep = if (points.size > 1) w / (points.size - 1) else w

                val path = Path()
                points.forEachIndexed { i, pt ->
                    val x = if (points.size > 1) i * xStep else w / 2
                    val y = usableH - (pt.pctPerKm / maxVal * usableH).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, accentColor, style = Stroke(width = 3f))

                points.forEachIndexed { i, pt ->
                    val x = if (points.size > 1) i * xStep else w / 2
                    val y = usableH - (pt.pctPerKm / maxVal * usableH).toFloat()
                    drawCircle(accentColor, radius = 4f, center = Offset(x, y))
                }

                val avgY = usableH - (overallAvg / maxVal * usableH).toFloat()
                drawLine(
                    dashLineColor,
                    start = Offset(0f, avgY),
                    end = Offset(w, avgY),
                    strokeWidth = 1f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.battery_efficiency_hint),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = velo.textFaint,
            )
        }
    }
}

@Composable
private fun UpdateCard(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onSkip: () -> Unit,
    onDisable: () -> Unit,
) {
    Card(
        shape = AppShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.update_version, updateState.version),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (updateState.changelog.isNotBlank()) {
                Text(
                    updateState.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (updateState.downloading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.update_downloading, updateState.progress),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else {
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (updateState.error) stringResource(R.string.update_error)
                        else stringResource(R.string.update_download),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.update_skip), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onDisable) {
                    Text(stringResource(R.string.update_disable), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
