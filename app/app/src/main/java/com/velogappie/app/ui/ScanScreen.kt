package com.velogappie.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velogappie.app.BikeUiState
import com.velogappie.app.R
import com.velogappie.app.ble.ConnectionState
import com.velogappie.app.ble.DiscoveredBike
import com.velogappie.app.ble.KnownBike
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors

@Composable
fun ScanScreen(
    state: BikeUiState,
    lastKnownBikeName: String?,
    knownBikes: List<KnownBike>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredBike) -> Unit,
    onConnectLastKnown: () -> Unit,
    onConnectKnownBike: (String, String) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val velo = LocalVeloColors.current
    val knownAddresses = knownBikes.map { it.address }.toSet()

    LaunchedEffect(lastKnownBikeName) {
        if (lastKnownBikeName != null && state.connectionState == ConnectionState.DISCONNECTED) {
            onConnectLastKnown()
        }
    }

    LazyColumn(
        modifier = modifier.padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.scan_title),
                fontFamily = HankenGrotesk,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.02).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.scan_helper),
                fontFamily = HankenGrotesk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                color = velo.textDim,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            Spacer(Modifier.height(32.dp))
            RadarVisual(
                isScanning = state.connectionState == ConnectionState.SCANNING,
                modifier = Modifier.size(260.dp),
            )
            Spacer(Modifier.height(28.dp))
        }


        if (state.discovered.isNotEmpty() || knownBikes.isNotEmpty()) {
            item {
                val totalCount = state.discovered.size + knownBikes.count { d ->
                    state.discovered.none { it.device.address == d.address }
                }
                Text(
                    stringResource(R.string.scan_found_count, totalCount),
                    fontFamily = HankenGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = velo.textFaint,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }

            items(state.discovered, key = { it.device.address }) { bike ->
                val isKnown = bike.device.address in knownAddresses
                DeviceCard(
                    name = bike.name,
                    subtitle = bike.serial,
                    isKnown = isKnown,
                    onClick = { onConnect(bike) },
                )
                Spacer(Modifier.height(10.dp))
            }

            val unseenKnown = knownBikes.filter { kb ->
                state.discovered.none { it.device.address == kb.address }
            }
            items(unseenKnown, key = { "known-${it.address}" }) { bike ->
                DeviceCard(
                    name = bike.name,
                    subtitle = bike.serial,
                    isKnown = true,
                    dimmed = true,
                    onClick = { onConnectKnownBike(bike.address, bike.name) },
                )
                Spacer(Modifier.height(10.dp))
            }
        } else {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.connectionState == ConnectionState.SCANNING) stringResource(R.string.scan_scanning)
                    else stringResource(R.string.scan_no_bikes_found),
                    fontFamily = HankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = velo.textDim,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        if (state.connectionState == ConnectionState.SCANNING) onStopScan() else onStartScan()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = velo.textDim,
                )
                Text(
                    if (state.connectionState == ConnectionState.SCANNING) stringResource(R.string.scan_stop)
                    else stringResource(R.string.scan_rescan),
                    fontFamily = HankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textDim,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RadarVisual(isScanning: Boolean, modifier: Modifier = Modifier) {
    val velo = LocalVeloColors.current
    val accent = velo.accent

    val pulseAlpha = if (isScanning) {
        val inf = rememberInfiniteTransition(label = "radar")
        val a by inf.animateFloat(
            initialValue = 0.04f,
            targetValue = 0.18f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
            label = "pulse",
        )
        a
    } else {
        0.06f
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r1 = size.minDimension / 2 * 0.95f
                    val r2 = size.minDimension / 2 * 0.68f
                    val r3 = size.minDimension / 2 * 0.42f
                    drawCircle(accent.copy(alpha = pulseAlpha * 0.5f), r1, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
                    drawCircle(accent.copy(alpha = pulseAlpha * 0.8f), r2, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(1.2.dp.toPx()))
                    drawCircle(accent.copy(alpha = pulseAlpha), r3, center = androidx.compose.ui.geometry.Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                }
        )
        Box(
            Modifier
                .size(74.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = velo.textOnAccent,
            )
        }
    }
}

@Composable
private fun DeviceCard(
    name: String,
    subtitle: String,
    isKnown: Boolean,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    val velo = LocalVeloColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.65f else 1f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
            .border(
                width = if (isKnown && !dimmed) 1.5.dp else 1.dp,
                color = if (isKnown && !dimmed) velo.accent else velo.hairline,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        if (isKnown) velo.accentWeak else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(13.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = if (isKnown) velo.accent else velo.textDim,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    fontFamily = HankenGrotesk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    fontFamily = HankenGrotesk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = velo.textDim2,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (isKnown && !dimmed) {
                Box(
                    Modifier
                        .background(velo.accent, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.scan_connect_button),
                        fontFamily = HankenGrotesk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = velo.textOnAccent,
                    )
                }
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.NavigateNext,
                    contentDescription = null,
                    tint = velo.iconInactive,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
