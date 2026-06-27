package com.velogappie.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velogappie.app.ble.ConnectionState
import com.velogappie.app.ble.EXTRA_AUTO_CONNECT
import com.velogappie.app.ui.BikePhotoCropScreen
import com.velogappie.app.ui.BikePhotoPickerSheet
import com.velogappie.app.ui.BikePhotoStorage
import com.velogappie.app.ui.DashboardScreen
import com.velogappie.app.ui.InformationScreen
import com.velogappie.app.ui.OnboardingScreen
import com.velogappie.app.ui.RideHistoryScreen
import com.velogappie.app.ui.ScanScreen
import com.velogappie.app.ui.SettingsScreen
import com.velogappie.app.ui.theme.AppAccent
import com.velogappie.app.ui.theme.AppShapes
import com.velogappie.app.ui.theme.AppTheme
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors
import com.velogappie.app.ui.theme.VeloGappieTheme
import com.velogappie.app.health.HealthConnectBridge
import com.velogappie.app.wear.PhoneWearBridge

// Per-app language switching (AppCompatDelegate.setApplicationLocales) silently does
// nothing on a plain ComponentActivity, even with Compose — it requires AppCompatActivity
// specifically to actually apply/recreate with the new locale.
class MainActivity : AppCompatActivity() {
    private val _autoConnect = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PhoneWearBridge.openWatchApp(this)
        _autoConnect.value = intent?.getBooleanExtra(EXTRA_AUTO_CONNECT, false) ?: false
        setContent {
            VeloGappieApp(
                autoConnect = _autoConnect.value,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_AUTO_CONNECT, false)) {
            _autoConnect.value = true
        }
    }
}

private fun requiredPermissions(): Array<String> {
    val perms = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        perms += Manifest.permission.BLUETOOTH_SCAN
        perms += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        perms += Manifest.permission.POST_NOTIFICATIONS
    }
    return perms.toTypedArray()
}

// Tab indices
private const val TAB_DASHBOARD = 0
private const val TAB_HISTORY = 1
private const val TAB_SETTINGS = 2
private const val TAB_INFORMATION = 3

@Composable
fun VeloGappieApp(
    viewModel: BikeViewModel = viewModel(),
    autoConnect: Boolean = false,
) {
    val systemIsDark = isSystemInDarkTheme()
    var theme by remember { mutableStateOf(viewModel.appTheme(systemIsDark)) }
    var accent by remember { mutableStateOf(viewModel.appAccent()) }


    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(Unit) {
        viewModel.finishApp.collect { activity?.finishAndRemoveTask() }
    }

    VeloGappieTheme(theme = theme, accent = accent) {
        Surface {
            VeloGappieAppContent(
                viewModel = viewModel,
                autoConnect = autoConnect,
                theme = theme,
                onSetTheme = { theme = it; viewModel.setAppTheme(it) },
                accent = accent,
                onSetAccent = { accent = it; viewModel.setAppAccent(it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeloGappieAppContent(
    viewModel: BikeViewModel,
    autoConnect: Boolean,
    theme: AppTheme,
    onSetTheme: (AppTheme) -> Unit,
    accent: AppAccent,
    onSetAccent: (AppAccent) -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var permissionsGranted by remember {
        mutableStateOf(requiredPermissions().all {
            context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        })
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionsGranted = results.values.all { it }
    }

    val onboardingPrefs = remember { context.getSharedPreferences("onboarding", Context.MODE_PRIVATE) }
    var onboardingCompleted by remember { mutableStateOf(onboardingPrefs.getBoolean("completed", false)) }

    if (!onboardingCompleted) {
        OnboardingScreen(
            onFinish = {
                onboardingPrefs.edit().putBoolean("completed", true).apply()
                onboardingCompleted = true
            },
            onRequestPermissions = { permissionLauncher.launch(requiredPermissions()) },
            permissionsGranted = permissionsGranted,
            state = state,
            knownBikes = viewModel.knownBikes(),
            onStartScan = viewModel::startScan,
            onStopScan = viewModel::stopScan,
            onConnect = { bike -> viewModel.connect(bike.device, bike.name) },
            onConnectKnownBike = viewModel::connectKnownBike,
        )
        return
    }

    val disclaimerPrefs = remember { context.getSharedPreferences("disclaimer", Context.MODE_PRIVATE) }
    var disclaimerAcknowledged by remember { mutableStateOf(disclaimerPrefs.getBoolean("acknowledged", false)) }

    if (!disclaimerAcknowledged) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(stringResource(R.string.disclaimer_text)) },
            confirmButton = {
                Button(onClick = {
                    disclaimerPrefs.edit().putBoolean("acknowledged", true).apply()
                    disclaimerAcknowledged = true
                }) { Text(stringResource(R.string.disclaimer_acknowledge)) }
            },
        )
        return
    }

    val rides by viewModel.rides.collectAsState()
    val rideGroups by viewModel.rideGroups.collectAsState()

    if (!permissionsGranted) {
        LaunchedEffect(Unit) { permissionLauncher.launch(requiredPermissions()) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.permissions_explanation))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permissionLauncher.launch(requiredPermissions()) }) { Text(stringResource(R.string.permissions_grant)) }
            }
        }
        return
    }

    // Bike photo picker state
    var bikePhoto by remember { mutableStateOf(BikePhotoStorage.loadBitmap(context)) }
    var bikeModelName by remember { mutableStateOf(BikePhotoStorage.loadModelName(context)) }
    var showPhotoPickerSheet by remember { mutableStateOf(false) }
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cropBitmap = BikePhotoStorage.decodeBitmapFromUri(context, uri)
        }
    }

    LaunchedEffect(Unit) { if (autoConnect) viewModel.connectLastKnownBike() }

    var tab by remember { mutableStateOf(TAB_DASHBOARD) }

    // Lands on the (empty-values) dashboard immediately; connecting is a bottom-sheet
    // popover on top of it, not a blocking full-screen step. Disconnecting (including an
    // explicit "Disconnect" tap) returns to that exact same state, as if freshly started.
    var showConnectDialog by remember { mutableStateOf(state.connectionState != ConnectionState.CONNECTED) }
    LaunchedEffect(state.connectionState) {
        when (state.connectionState) {
            ConnectionState.CONNECTED -> showConnectDialog = false
            ConnectionState.DISCONNECTED -> {
                showConnectDialog = true
                tab = TAB_DASHBOARD
            }
            else -> {}
        }
    }

    if (showConnectDialog) {
        ModalBottomSheet(onDismissRequest = { showConnectDialog = false }) {
            ScanScreen(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                state = state,
                lastKnownBikeName = viewModel.lastKnownBikeName(),
                knownBikes = viewModel.knownBikes(),
                onStartScan = viewModel::startScan,
                onStopScan = viewModel::stopScan,
                onConnect = { bike -> viewModel.connect(bike.device, bike.name) },
                onConnectLastKnown = viewModel::connectLastKnownBike,
                onConnectKnownBike = viewModel::connectKnownBike,
            )
        }
    }

    // SharedPreferences-backed settings only need to be re-read when their owning screen
    // becomes visible again, not on every recomposition.
    val driveSyncEnabled = remember(tab) { viewModel.isDriveSyncEnabled() }
    val autoStartOnBikeDetected = remember(tab) { viewModel.isAutoStartOnBikeDetectedEnabled() }
    val autoStartWatchOnPedal = remember(tab) { viewModel.isAutoStartWatchOnPedalEnabled() }
    val handlebarFunction = remember(tab) { viewModel.handlebarFunction() }
    val healthSyncEnabled = remember(tab) { HealthConnectBridge.isSyncEnabled(context) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (state.connectionState != ConnectionState.CONNECTED) {
                TextButton(
                    onClick = { showConnectDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.reconnect_banner),
                        fontFamily = HankenGrotesk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalVeloColors.current.textDim2,
                    )
                }
            }
        },
        bottomBar = {
            VeloBottomNav(selectedTab = tab, onTabSelected = { tab = it })
        }
    ) { padding ->
        Crossfade(targetState = tab, animationSpec = tween(250), modifier = Modifier.padding(padding)) { currentTab ->
            when (currentTab) {
                TAB_DASHBOARD -> DashboardScreen(
                    state = state,
                    bikePhoto = bikePhoto,
                    bikeModelName = bikeModelName,
                    onBikePhotoTap = { showPhotoPickerSheet = true },
                    onSetAssistLevel = viewModel::setMotorAssistLevel,
                    onSetLights = viewModel::setLights,
                    onSetAutoLights = viewModel::setAutoLights,
                    onSetBellSound = viewModel::setBellSound,
                    onSetCadence = viewModel::setCadence,
                    onSetDisplayMode = viewModel::setDisplayMode,
                    onSetWeatherDisplay = viewModel::setWeatherDisplay,
                    onSetStartMultiplier = viewModel::setStartMultiplier,
                    onSetEnvioloMode = viewModel::setEnvioloMode,
                )
                TAB_HISTORY -> RideHistoryScreen(
                    rides = rides,
                    groups = rideGroups,
                    healthSyncEnabled = healthSyncEnabled,
                    onDeleteRide = viewModel::deleteRide,
                    onMergeRides = viewModel::mergeRides,
                    onCreateGroup = viewModel::createGroup,
                    onRenameGroup = viewModel::renameGroup,
                    onDeleteGroup = viewModel::deleteGroup,
                    onAddRidesToGroup = viewModel::addRidesToGroup,
                    onRemoveRidesFromGroup = viewModel::removeRidesFromGroup,
                    onLoadLocationPoints = viewModel::loadLocationPoints,
                    onExportGpx = { ride, points ->
                        com.velogappie.app.ride.GpxExporter.export(context, ride, points)
                    },
                )
                TAB_SETTINGS -> SettingsScreen(
                    state = state,
                    theme = theme,
                    onSetTheme = onSetTheme,
                    accent = accent,
                    onSetAccent = onSetAccent,
                    handlebarFunction = handlebarFunction,
                    onSetHandlebarFunction = viewModel::setHandlebarFunction,
                    driveSyncEnabled = driveSyncEnabled,
                    onSetDriveSyncEnabled = viewModel::setDriveSyncEnabled,
                    autoStartOnBikeDetected = autoStartOnBikeDetected,
                    onSetAutoStartOnBikeDetected = viewModel::setAutoStartOnBikeDetectedEnabled,
                    autoStartWatchOnPedal = autoStartWatchOnPedal,
                    onSetAutoStartWatchOnPedal = viewModel::setAutoStartWatchOnPedalEnabled,
                    onClearRideHistory = viewModel::clearRideHistory,
                    onDisconnect = viewModel::disconnect,
                )
                TAB_INFORMATION -> {
                    val updateState by viewModel.updateState.collectAsState()
                    InformationScreen(
                        state = state,
                        rides = rides,
                        updateState = updateState,
                        onDownloadUpdate = viewModel::downloadAndInstallUpdate,
                        onSkipUpdate = viewModel::skipUpdate,
                        onDisableUpdateCheck = viewModel::disableUpdateCheck,
                    )
                }
            }
        }
    }

    if (showPhotoPickerSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoPickerSheet = false }) {
            BikePhotoPickerSheet(
                onPickFromGallery = {
                    showPhotoPickerSheet = false
                    photoPickerLauncher.launch("image/*")
                },
                onModelSelected = { name ->
                    BikePhotoStorage.saveModelName(context, name)
                    bikeModelName = name
                },
                onDismiss = { showPhotoPickerSheet = false },
            )
        }
    }

    cropBitmap?.let { bmp ->
        BikePhotoCropScreen(
            bitmap = bmp,
            onCropConfirmed = { cropped ->
                BikePhotoStorage.saveCroppedBitmap(context, cropped)
                bikePhoto = BikePhotoStorage.loadBitmap(context)
                cropBitmap = null
            },
            onCancel = { cropBitmap = null },
        )
    }
}

@Composable
private fun VeloBottomNav(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val velo = LocalVeloColors.current
    val accent = MaterialTheme.colorScheme.primary
    val hairlineColor = velo.hairline

    data class NavItem(val index: Int, val icon: ImageVector, val labelRes: Int)
    val items = listOf(
        NavItem(TAB_DASHBOARD, Icons.AutoMirrored.Filled.DirectionsBike, R.string.tab_dashboard),
        NavItem(TAB_HISTORY, Icons.Filled.History, R.string.tab_trip_memory),
        NavItem(TAB_SETTINGS, Icons.Filled.Settings, R.string.tab_settings),
        NavItem(TAB_INFORMATION, Icons.Filled.Info, R.string.tab_information),
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .background(velo.navBg)
            .drawBehind {
                drawLine(
                    hairlineColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
    ) {
        Row(
            Modifier.fillMaxSize().padding(top = 11.dp, start = 6.dp, end = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val selected = selectedTab == item.index
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        ) { onTabSelected(item.index) }
                        .padding(top = if (selected) 0.dp else 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.16f), AppShapes.NavPill)
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = stringResource(item.labelRes),
                                tint = accent,
                                modifier = Modifier.size(23.dp),
                            )
                        }
                    } else {
                        Icon(
                            item.icon,
                            contentDescription = stringResource(item.labelRes),
                            tint = velo.iconInactive,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(item.labelRes),
                        fontFamily = HankenGrotesk,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (selected) accent else velo.iconInactive,
                    )
                }
            }
        }
    }
}
