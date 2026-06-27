package com.velogappie.app.ui

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.health.connect.client.PermissionController
import com.google.android.gms.auth.api.identity.Identity
import com.velogappie.app.BikeUiState
import com.velogappie.app.HandlebarFunction
import com.velogappie.app.R
import com.velogappie.app.ble.BikeWrite
import com.velogappie.app.drive.DriveSync
import com.velogappie.app.health.HealthConnectBridge
import com.velogappie.app.nav.NavBridgeState
import com.velogappie.app.ui.theme.AppAccent
import com.velogappie.app.ui.theme.AppShapes
import com.velogappie.app.ui.theme.AppTheme
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.core.os.BuildCompat.PrereleaseSdkCheck::class)
@Composable
fun SettingsScreen(
    state: BikeUiState,
    theme: AppTheme,
    onSetTheme: (AppTheme) -> Unit,
    accent: AppAccent,
    onSetAccent: (AppAccent) -> Unit,
    handlebarFunction: HandlebarFunction,
    onSetHandlebarFunction: (HandlebarFunction) -> Unit,
    driveSyncEnabled: Boolean,
    onSetDriveSyncEnabled: (Boolean) -> Unit,
    autoStartOnBikeDetected: Boolean,
    onSetAutoStartOnBikeDetected: (Boolean) -> Unit,
    autoStartWatchOnPedal: Boolean,
    onSetAutoStartWatchOnPedal: (Boolean) -> Unit,
    onClearRideHistory: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val v = LocalVeloColors.current
    val healthConnectAvailable = remember { HealthConnectBridge.isAvailable(context) }
    var healthSyncOn by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (healthConnectAvailable) {
            healthSyncOn = HealthConnectBridge.hasPermissions(context) && HealthConnectBridge.isSyncEnabled(context)
        }
    }

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        val ok = granted.containsAll(HealthConnectBridge.REQUIRED_PERMISSIONS)
        HealthConnectBridge.setSyncEnabled(context, ok)
        healthSyncOn = ok
    }

    var driveSyncChecked by remember { mutableStateOf(driveSyncEnabled) }
    var autoStartBikeChecked by remember { mutableStateOf(autoStartOnBikeDetected) }
    var autoStartWatchChecked by remember { mutableStateOf(autoStartWatchOnPedal) }
    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val granted = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data).accessToken != null
        } catch (e: Exception) {
            false
        }
        driveSyncChecked = granted
        if (granted) onSetDriveSyncEnabled(true)
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text(stringResource(R.string.settings_clear_history_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmClearHistory = false; onClearRideHistory() }) {
                    Text(stringResource(R.string.settings_clear_history_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(stringResource(R.string.disclaimer_acknowledge)) }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Screen title ─────────────────────────────────────────
        Text(
            stringResource(R.string.tab_settings),
            fontFamily = HankenGrotesk,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.02).sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 2.dp, top = 2.dp),
        )

        // ── WEERGAVE ─────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_appearance_title)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Theme segmented
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(v.navBg, RoundedCornerShape(14.dp))
                            .padding(4.dp),
                    ) {
                        ThemePill(
                            label = stringResource(R.string.settings_theme_light),
                            selected = theme == AppTheme.LIGHT,
                            onClick = { onSetTheme(AppTheme.LIGHT) },
                            modifier = Modifier.weight(1f),
                        )
                        ThemePill(
                            label = stringResource(R.string.settings_theme_dark),
                            selected = theme == AppTheme.DARK,
                            onClick = { onSetTheme(AppTheme.DARK) },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Accent chooser
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AccentChip(stringResource(R.string.settings_accent_coral), Color(0xFFF0795A), accent == AppAccent.CORAL, Modifier.weight(1f)) { onSetAccent(AppAccent.CORAL) }
                        AccentChip(stringResource(R.string.settings_accent_sage), Color(0xFF8FAE8C), accent == AppAccent.SAGE, Modifier.weight(1f)) { onSetAccent(AppAccent.SAGE) }
                        AccentChip(stringResource(R.string.settings_accent_ruby), Color(0xFFC24A54), accent == AppAccent.RUBY, Modifier.weight(1f)) { onSetAccent(AppAccent.RUBY) }
                        AccentChip(stringResource(R.string.settings_accent_dune), Color(0xFFC7BDAD), accent == AppAccent.DUNE, Modifier.weight(1f)) { onSetAccent(AppAccent.DUNE) }
                    }

                    // Language switcher
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(v.hairline, RoundedCornerShape(0.dp))
                            .height(1.dp)
                    )
                    LanguageSwitcher()
                }
            }
        }

        // ── RIJDEN ───────────────────────────────────────────────
        SettingsSection(stringResource(R.string.info_autostart_title)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Autostart bike
                    SettingsRow(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.info_autostart_bike_detected),
                        subtitle = null,
                        trailing = {
                            Switch(
                                checked = autoStartBikeChecked,
                                onCheckedChange = { autoStartBikeChecked = it; onSetAutoStartOnBikeDetected(it) },
                            )
                        },
                    )
                    SettingsDivider()

                    // Autostart watch
                    SettingsRow(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.info_autostart_watch_pedal),
                        subtitle = null,
                        trailing = {
                            Switch(
                                checked = autoStartWatchChecked,
                                onCheckedChange = { autoStartWatchChecked = it; onSetAutoStartWatchOnPedal(it) },
                            )
                        },
                    )
                    SettingsDivider()

                    // Nav bridge
                    val hasAccess = remember { NavBridgeState.hasNotificationAccess(context) }
                    var navBridgeChecked by remember { mutableStateOf(NavBridgeState.isEnabled) }

                    if (!hasAccess) {
                        SettingsRow(
                            icon = Icons.Filled.Navigation,
                            title = stringResource(R.string.settings_nav_bridge_title),
                            subtitle = stringResource(R.string.settings_nav_bridge_grant_access),
                            onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                            trailing = {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = null,
                                    tint = v.iconInactive,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    } else {
                        SettingsRow(
                            icon = Icons.Filled.Navigation,
                            title = stringResource(R.string.settings_nav_bridge_active),
                            subtitle = null,
                            trailing = {
                                Switch(
                                    checked = navBridgeChecked,
                                    onCheckedChange = { navBridgeChecked = it; NavBridgeState.isEnabled = it },
                                )
                            },
                        )
                    }

                }
            }
        }

        // ── WAARSCHUWINGEN ───────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_speed_alert_title)) {
            var speedAlertEnabled by remember { mutableStateOf(com.velogappie.app.AppSettings.isSpeedAlertEnabled(context)) }
            var speedAlertThreshold by remember { mutableStateOf(com.velogappie.app.AppSettings.speedAlertThresholdKmh(context).toFloat()) }
            var batteryAlertEnabled by remember { mutableStateOf(com.velogappie.app.AppSettings.isBatteryLowAlertEnabled(context)) }
            var batteryAlertThreshold by remember { mutableStateOf(com.velogappie.app.AppSettings.batteryLowAlertThreshold(context).toFloat()) }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp, horizontal = 16.dp)) {
                    // Speed alert
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Icon(Icons.Filled.Speed, contentDescription = null, tint = v.textDim, modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(R.string.settings_speed_alert_toggle),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = speedAlertEnabled,
                            onCheckedChange = {
                                speedAlertEnabled = it
                                com.velogappie.app.AppSettings.setSpeedAlertEnabled(context, it)
                            },
                        )
                    }
                    if (speedAlertEnabled) {
                        Row(
                            Modifier.padding(start = 35.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = speedAlertThreshold,
                                onValueChange = { speedAlertThreshold = it },
                                onValueChangeFinished = {
                                    com.velogappie.app.AppSettings.setSpeedAlertThresholdKmh(context, speedAlertThreshold.toInt())
                                },
                                valueRange = 15f..50f,
                                steps = 34,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${speedAlertThreshold.toInt()} km/h",
                                fontFamily = HankenGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Box(
                        Modifier.fillMaxWidth().height(1.dp).background(v.hairline)
                    )

                    // Battery alert
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = v.textDim, modifier = Modifier.size(22.dp))
                        Text(
                            stringResource(R.string.settings_battery_alert_toggle),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = batteryAlertEnabled,
                            onCheckedChange = {
                                batteryAlertEnabled = it
                                com.velogappie.app.AppSettings.setBatteryLowAlertEnabled(context, it)
                            },
                        )
                    }
                    if (batteryAlertEnabled) {
                        Row(
                            Modifier.padding(start = 35.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = batteryAlertThreshold,
                                onValueChange = { batteryAlertThreshold = it },
                                onValueChangeFinished = {
                                    com.velogappie.app.AppSettings.setBatteryLowAlertThreshold(context, batteryAlertThreshold.toInt())
                                },
                                valueRange = 10f..50f,
                                steps = 39,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "${batteryAlertThreshold.toInt()}%",
                                fontFamily = HankenGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }

        // ── GEGEVENS (Data) ──────────────────────────────────────
        SettingsSection(stringResource(R.string.info_drive_sync_toggle)) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Health Connect
                    if (healthConnectAvailable) {
                        SettingsRow(
                            icon = null,
                            title = if (healthSyncOn) stringResource(R.string.info_health_connect_active)
                                    else stringResource(R.string.info_health_connect_activate),
                            subtitle = null,
                            trailing = {
                                if (healthSyncOn) {
                                    Switch(
                                        checked = true,
                                        onCheckedChange = {
                                            HealthConnectBridge.setSyncEnabled(context, false)
                                            healthSyncOn = false
                                        },
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.NavigateNext,
                                        contentDescription = null,
                                        tint = v.iconInactive,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            onClick = if (!healthSyncOn) {
                                {
                                    coroutineScope.launch {
                                        if (HealthConnectBridge.hasPermissions(context)) {
                                            HealthConnectBridge.setSyncEnabled(context, true)
                                            healthSyncOn = true
                                        } else {
                                            healthPermissionLauncher.launch(HealthConnectBridge.REQUIRED_PERMISSIONS)
                                        }
                                    }
                                }
                            } else null,
                        )
                        SettingsDivider()
                    }

                    // Drive sync
                    SettingsRow(
                        icon = null,
                        title = if (driveSyncChecked) stringResource(R.string.info_drive_sync_toggle)
                                else stringResource(R.string.info_drive_sync_activate),
                        subtitle = null,
                        trailing = {
                            if (driveSyncChecked) {
                                Switch(
                                    checked = true,
                                    onCheckedChange = {
                                        driveSyncChecked = false
                                        onSetDriveSyncEnabled(false)
                                    },
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = null,
                                    tint = v.iconInactive,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        onClick = if (!driveSyncChecked) {
                            {
                                coroutineScope.launch {
                                    try {
                                        val result = DriveSync.authorize(context)
                                        if (result.hasResolution()) {
                                            result.pendingIntent?.let { pendingIntent ->
                                                driveConsentLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                                            }
                                        } else if (result.accessToken != null) {
                                            driveSyncChecked = true
                                            onSetDriveSyncEnabled(true)
                                        }
                                    } catch (e: Exception) {
                                        driveSyncChecked = false
                                    }
                                }
                            }
                        } else null,
                    )
                }
            }
            Text(
                stringResource(R.string.info_drive_sync_revoke_hint),
                style = MaterialTheme.typography.labelSmall,
                color = v.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }

        // ── ONDERHOUD ────────────────────────────────────────────
        SettingsSection(stringResource(R.string.settings_service_reminder_title)) {
            var serviceKm by remember { mutableStateOf(com.velogappie.app.AppSettings.serviceReminderKm(context)) }
            var lastServiceOdo by remember { mutableStateOf(com.velogappie.app.AppSettings.lastServiceOdometer(context)) }
            val options = listOf(0, 500, 1000, 2000, 5000)

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.settings_service_reminder_description),
                        style = MaterialTheme.typography.labelSmall,
                        color = v.textFaint,
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        options.forEachIndexed { idx, km ->
                            SegmentedButton(
                                selected = serviceKm == km,
                                onClick = {
                                    serviceKm = km
                                    com.velogappie.app.AppSettings.setServiceReminderKm(context, km)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                            ) {
                                Text(if (km == 0) stringResource(R.string.lights_off) else "${km}km")
                            }
                        }
                    }
                    if (serviceKm > 0 && state.odometer != null) {
                        val kmSinceService = state.odometer - lastServiceOdo
                        val remaining = (serviceKm - kmSinceService).coerceAtLeast(0)
                        val progress = (kmSinceService.toFloat() / serviceKm).coerceIn(0f, 1f)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = v.hairline,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.settings_service_reminder_next, remaining),
                                style = MaterialTheme.typography.labelSmall,
                                color = v.textFaint,
                            )
                            TextButton(onClick = {
                                lastServiceOdo = state.odometer
                                com.velogappie.app.AppSettings.setLastServiceOdometer(context, state.odometer)
                            }) {
                                Text(stringResource(R.string.settings_service_reminder_reset), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }


        // ── Destructive actions ──────────────────────────────────
        OutlinedButton(
            onClick = { confirmClearHistory = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = v.textDim2),
        ) {
            Text(stringResource(R.string.settings_clear_ride_history))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(v.destructiveBg)
                .border(1.dp, v.destructiveBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onDisconnect)
                .padding(14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.LinkOff,
                contentDescription = null,
                tint = v.destructiveText,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.info_disconnect),
                color = v.destructiveText,
                fontFamily = HankenGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    val v = LocalVeloColors.current
    Column {
        Text(
            label.uppercase(),
            fontFamily = HankenGrotesk,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = v.textFaint,
            modifier = Modifier.padding(start = 4.dp, bottom = 9.dp),
        )
        content()
    }
}

@Composable
private fun ThemePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val v = LocalVeloColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .then(
                if (selected) Modifier.background(v.accentWeak, RoundedCornerShape(11.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = HankenGrotesk,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) v.accent else v.iconInactive,
        )
    }
}

@Composable
private fun AccentChip(label: String, swatch: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val v = LocalVeloColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) v.accent else v.hairline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp, horizontal = 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(12.dp)
                .background(swatch, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontFamily = HankenGrotesk,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else v.textDim,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val v = LocalVeloColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = v.textDim, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = v.textFaint,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsDivider() {
    val v = LocalVeloColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(v.hairline)
    )
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun LanguageSwitcher() {
    val activity = LocalContext.current.findActivity()
    val current = AppCompatDelegate.getApplicationLocales()
    val currentTag = if (current.isEmpty) null else current[0]?.language
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageChip("EN", "en", currentTag, activity)
        LanguageChip("NL", "nl", currentTag, activity)
        LanguageChip("DE", "de", currentTag, activity)
        LanguageChip(stringResource(R.string.info_language_system), null, currentTag, activity)
    }
}

@Composable
private fun LanguageChip(label: String, tag: String?, currentTag: String?, activity: Activity?) {
    FilterChip(
        selected = currentTag == tag,
        onClick = {
            val locales = if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
            activity?.recreate()
        },
        label = { Text(label) },
    )
}

