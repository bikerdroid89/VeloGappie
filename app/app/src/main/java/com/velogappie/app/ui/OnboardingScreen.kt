package com.velogappie.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.google.android.gms.auth.api.identity.Identity
import com.velogappie.app.BikeUiState
import com.velogappie.app.R
import com.velogappie.app.ble.ConnectionState
import com.velogappie.app.ble.DiscoveredBike
import com.velogappie.app.ble.KnownBike
import com.velogappie.app.drive.DriveSync
import com.velogappie.app.health.HealthConnectBridge
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onRequestPermissions: () -> Unit,
    permissionsGranted: Boolean,
    state: BikeUiState,
    knownBikes: List<KnownBike>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredBike) -> Unit,
    onConnectKnownBike: (String, String) -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    val velo = LocalVeloColors.current
    val connected = state.connectionState == ConnectionState.CONNECTED

    val autoAdvanceToScan = rememberUpdatedState(permissionsGranted)
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted && page == 1) page = 2
    }

    LaunchedEffect(connected) {
        if (connected && page == 2) page = 3
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 26.dp, vertical = 14.dp)
            .padding(bottom = 12.dp),
    ) {
        Crossfade(
            targetState = page,
            animationSpec = tween(300),
            modifier = Modifier.weight(1f),
        ) { p ->
            when (p) {
                0 -> WelcomePage()
                1 -> PermissionPage()
                2 -> ScanPage(
                    state = state,
                    knownBikes = knownBikes,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan,
                    onConnect = onConnect,
                    onConnectKnownBike = onConnectKnownBike,
                )
                3 -> FeaturePage()
            }
        }

        ProgressDots(
            current = page,
            total = 4,
            modifier = Modifier.padding(bottom = 22.dp),
        )

        val buttonText = when (page) {
            0 -> stringResource(R.string.onboarding_btn_start)
            1 -> stringResource(R.string.onboarding_btn_grant)
            2 -> if (connected) stringResource(R.string.onboarding_next) else stringResource(R.string.onboarding_btn_skip)
            else -> stringResource(R.string.onboarding_btn_finish)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(velo.accent, RoundedCornerShape(18.dp))
                .clickable {
                    when (page) {
                        0 -> page = 1
                        1 -> onRequestPermissions()
                        2 -> page = 3
                        3 -> onFinish()
                    }
                }
                .padding(vertical = 17.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buttonText,
                fontFamily = HankenGrotesk,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = velo.textOnAccent,
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    Column(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.welcome),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 26.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontFamily = HankenGrotesk,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            lineHeight = 31.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.5.sp,
            color = LocalVeloColors.current.textDim,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PermissionPage() {
    val velo = LocalVeloColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.connecting),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 26.dp)
                .clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = stringResource(R.string.onboarding_connect_title),
            fontFamily = HankenGrotesk,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            lineHeight = 31.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_connect_body),
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.5.sp,
            color = velo.textDim,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CheckItem(stringResource(R.string.onboarding_connect_check_1))
            CheckItem(stringResource(R.string.onboarding_connect_check_2))
        }
        Spacer(Modifier.weight(0.001f))
    }
}

@Composable
private fun ScanPage(
    state: BikeUiState,
    knownBikes: List<KnownBike>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (DiscoveredBike) -> Unit,
    onConnectKnownBike: (String, String) -> Unit,
) {
    val velo = LocalVeloColors.current
    val knownAddresses = knownBikes.map { it.address }.toSet()
    val isScanning = state.connectionState == ConnectionState.SCANNING
    val connected = state.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(Unit) { onStartScan() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_scan_title),
            fontFamily = HankenGrotesk,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            lineHeight = 31.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_scan_body),
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.sp,
            color = velo.textDim,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(22.dp))

        if (connected) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF7BAE76),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.onboarding_scan_connected),
                fontFamily = HankenGrotesk,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            OnboardingRadar(isScanning = isScanning, modifier = Modifier.size(180.dp))
            Spacer(Modifier.height(18.dp))

            if (state.discovered.isNotEmpty()) {
                state.discovered.forEach { bike ->
                    val isKnown = bike.device.address in knownAddresses
                    OnboardingDeviceCard(
                        name = bike.name,
                        subtitle = bike.serial,
                        isKnown = isKnown,
                        onClick = { onConnect(bike) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            val unseen = knownBikes.filter { kb -> state.discovered.none { it.device.address == kb.address } }
            unseen.forEach { bike ->
                OnboardingDeviceCard(
                    name = bike.name,
                    subtitle = bike.serial,
                    isKnown = true,
                    dimmed = true,
                    onClick = { onConnectKnownBike(bike.address, bike.name) },
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.discovered.isEmpty() && unseen.isEmpty()) {
                Text(
                    if (isScanning) stringResource(R.string.scan_scanning) else stringResource(R.string.scan_no_bikes_found),
                    fontFamily = HankenGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = velo.textDim,
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FeaturePage() {
    val context = LocalContext.current
    val velo = LocalVeloColors.current
    val coroutineScope = rememberCoroutineScope()
    var autoStart by remember { mutableStateOf(com.velogappie.app.AppSettings.isAutoStartOnBikeDetectedEnabled(context)) }
    var batteryAlert by remember { mutableStateOf(false) }
    var healthSync by remember { mutableStateOf(false) }
    var driveSync by remember { mutableStateOf(DriveSync.isEnabled(context)) }
    val healthAvailable = remember { HealthConnectBridge.isAvailable(context) }
    val navHasAccess = remember { com.velogappie.app.nav.NavBridgeState.hasNotificationAccess(context) }
    var navEnabled by remember { mutableStateOf(com.velogappie.app.nav.NavBridgeState.isEnabled) }

    LaunchedEffect(Unit) {
        if (healthAvailable) {
            healthSync = HealthConnectBridge.hasPermissions(context) && HealthConnectBridge.isSyncEnabled(context)
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.containsAll(HealthConnectBridge.REQUIRED_PERMISSIONS)
        HealthConnectBridge.setSyncEnabled(context, ok)
        healthSync = ok
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val granted = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken != null
        } catch (e: Exception) {
            false
        }
        if (granted) {
            DriveSync.setEnabled(context, true)
            driveSync = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_customize_title),
            fontFamily = HankenGrotesk,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            lineHeight = 31.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_customize_body),
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 22.5.sp,
            color = velo.textDim,
        )
        Spacer(Modifier.height(20.dp))

        FeatureToggle(
            icon = Icons.Filled.Bolt,
            title = stringResource(R.string.onboarding_feature_autostart),
            checked = autoStart,
            onChecked = {
                autoStart = it
                com.velogappie.app.AppSettings.setAutoStartOnBikeDetectedEnabled(context, it)
            },
        )

        FeatureToggle(
            icon = Icons.Filled.Navigation,
            title = stringResource(R.string.onboarding_feature_nav),
            checked = navEnabled,
            onChecked = { enabled ->
                if (!navHasAccess) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } else {
                    navEnabled = enabled
                    com.velogappie.app.nav.NavBridgeState.isEnabled = enabled
                }
            },
        )

        FeatureToggle(
            icon = Icons.Filled.BatteryAlert,
            title = stringResource(R.string.onboarding_feature_battery),
            checked = batteryAlert,
            onChecked = {
                batteryAlert = it
                com.velogappie.app.AppSettings.setBatteryLowAlertEnabled(context, it)
            },
        )

        if (healthAvailable) {
            FeatureToggle(
                icon = Icons.Filled.FavoriteBorder,
                title = stringResource(R.string.onboarding_feature_health),
                checked = healthSync,
                onChecked = { enabled ->
                    if (enabled) {
                        coroutineScope.launch {
                            if (HealthConnectBridge.hasPermissions(context)) {
                                HealthConnectBridge.setSyncEnabled(context, true)
                                healthSync = true
                            } else {
                                healthPermissionLauncher.launch(HealthConnectBridge.REQUIRED_PERMISSIONS)
                            }
                        }
                    } else {
                        HealthConnectBridge.setSyncEnabled(context, false)
                        healthSync = false
                    }
                },
            )
        }

        FeatureToggle(
            icon = Icons.Filled.CloudSync,
            title = stringResource(R.string.onboarding_feature_drive),
            checked = driveSync,
            onChecked = { enabled ->
                if (enabled) {
                    coroutineScope.launch {
                        try {
                            val result = DriveSync.authorize(context)
                            if (result.hasResolution()) {
                                result.pendingIntent?.let { pendingIntent ->
                                    driveConsentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                }
                            } else if (result.accessToken != null) {
                                DriveSync.setEnabled(context, true)
                                driveSync = true
                            }
                        } catch (_: Exception) { }
                    }
                } else {
                    DriveSync.setEnabled(context, false)
                    driveSync = false
                }
            },
        )

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun FeatureToggle(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val velo = LocalVeloColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = velo.textDim,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            fontFamily = HankenGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CheckItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(0xFF7BAE76),
        )
        Text(
            text = text,
            fontFamily = HankenGrotesk,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFC6C8C3),
        )
    }
}

@Composable
private fun OnboardingRadar(isScanning: Boolean, modifier: Modifier = Modifier) {
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
    } else 0.06f

    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                val r1 = size.minDimension / 2 * 0.95f
                val r2 = size.minDimension / 2 * 0.68f
                val r3 = size.minDimension / 2 * 0.42f
                val c = center
                drawCircle(accent.copy(alpha = pulseAlpha * 0.5f), r1, c, style = Stroke(1.2.dp.toPx()))
                drawCircle(accent.copy(alpha = pulseAlpha * 0.8f), r2, c, style = Stroke(1.2.dp.toPx()))
                drawCircle(accent.copy(alpha = pulseAlpha), r3, c, style = Stroke(1.5.dp.toPx()))
            }
        )
        Box(
            Modifier.size(56.dp).background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsBike,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = velo.textOnAccent,
            )
        }
    }
}

@Composable
private fun OnboardingDeviceCard(
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
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .border(
                if (isKnown && !dimmed) 1.5.dp else 1.dp,
                if (isKnown && !dimmed) velo.accent else velo.hairline,
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(
                    if (isKnown) velo.accentWeak else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                    RoundedCornerShape(12.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = if (isKnown) velo.accent else velo.textDim,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontFamily = HankenGrotesk, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontFamily = HankenGrotesk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = velo.textDim2)
            }
            if (isKnown && !dimmed) {
                Box(
                    Modifier.background(velo.accent, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(stringResource(R.string.scan_connect_button), fontFamily = HankenGrotesk, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = velo.textOnAccent)
                }
            } else {
                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = velo.iconInactive, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun ProgressDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    val velo = LocalVeloColors.current
    val inactiveColor = Color(0x24E2E4E0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (i == current) 24.dp else 7.dp)
                    .background(
                        if (i == current) velo.accent else inactiveColor,
                        RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}
