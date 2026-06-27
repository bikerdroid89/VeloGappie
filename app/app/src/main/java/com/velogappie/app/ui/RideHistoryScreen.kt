package com.velogappie.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velogappie.app.R
import com.velogappie.app.health.HealthConnectBridge
import com.velogappie.app.ride.LocationPointEntity
import com.velogappie.app.ride.RideEntity
import com.velogappie.app.ride.RideGroupEntity
import com.velogappie.app.ui.theme.AppShapes
import com.velogappie.app.ui.theme.HankenGrotesk
import com.velogappie.app.ui.theme.LocalVeloColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())

@Composable
fun RideHistoryScreen(
    rides: List<RideEntity>,
    groups: List<RideGroupEntity>,
    healthSyncEnabled: Boolean,
    onDeleteRide: (Long) -> Unit,
    onMergeRides: (List<Long>) -> Unit,
    onCreateGroup: (String, List<Long>) -> Unit,
    onRenameGroup: (Long, String) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onAddRidesToGroup: (List<Long>, Long) -> Unit,
    onRemoveRidesFromGroup: (List<Long>) -> Unit,
    onLoadLocationPoints: (Long, (List<LocationPointEntity>) -> Unit) -> Unit,
    onExportGpx: (RideEntity, List<LocationPointEntity>) -> Unit,
) {
    if (rides.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.trip_memory_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalVeloColors.current.textDim,
            )
        }
        return
    }

    var mergeMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var confirmMerge by remember { mutableStateOf(false) }
    var activeGroupId by remember { mutableStateOf<Long?>(null) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showAddToGroupDialog by remember { mutableStateOf<Long?>(null) }

    if (confirmMerge) {
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text(stringResource(R.string.trip_memory_merge_confirm_title, selectedIds.size)) },
            text = { Text(stringResource(R.string.trip_memory_merge_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmMerge = false
                    onMergeRides(selectedIds.toList())
                    selectedIds = emptySet()
                    mergeMode = false
                }) { Text(stringResource(R.string.trip_memory_merge_rides)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMerge = false }) { Text(stringResource(R.string.disclaimer_acknowledge)) }
            },
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onCreate = { name ->
                showCreateGroupDialog = false
                onCreateGroup(name, emptyList())
            },
        )
    }

    showAddToGroupDialog?.let { rideId ->
        AddToGroupDialog(
            groups = groups,
            onDismiss = { showAddToGroupDialog = null },
            onSelectGroup = { groupId ->
                showAddToGroupDialog = null
                onAddRidesToGroup(listOf(rideId), groupId)
            },
            onCreateNewGroup = { name ->
                showAddToGroupDialog = null
                onCreateGroup(name, listOf(rideId))
            },
        )
    }

    val displayRides = if (activeGroupId == null) rides
    else rides.filter { it.groupId == activeGroupId }

    Column(Modifier.fillMaxSize()) {
        if (mergeMode) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { mergeMode = false; selectedIds = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.a11y_close))
                    }
                    Text(
                        stringResource(R.string.trip_memory_select_to_merge),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = { confirmMerge = true },
                        enabled = selectedIds.size >= 2,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = stringResource(R.string.a11y_merge), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${selectedIds.size}")
                    }
                }
            }
        }

        if (groups.isNotEmpty() && !mergeMode) {
            GroupChipRow(
                groups = groups,
                activeGroupId = activeGroupId,
                onSelectGroup = { activeGroupId = it },
                onCreateGroup = { showCreateGroupDialog = true },
            )
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            if (!mergeMode) {
                item {
                    Spacer(Modifier.height(6.dp))
                    RideSummaryHero(displayRides)
                    Spacer(Modifier.height(13.dp))
                    ActionRow(
                        onGroup = { showCreateGroupDialog = true },
                        onMerge = { mergeMode = true },
                    )
                    Spacer(Modifier.height(13.dp))
                }

                if (activeGroupId != null) {
                    item {
                        ActiveGroupHeader(
                            group = groups.find { it.id == activeGroupId },
                            onRename = { id, name -> onRenameGroup(id, name) },
                            onDelete = { id ->
                                onDeleteGroup(id)
                                activeGroupId = null
                            },
                        )
                        Spacer(Modifier.height(13.dp))
                    }
                }
            }
            items(displayRides, key = { it.id }) { ride ->
                if (mergeMode) {
                    MergeSelectableRideCard(
                        ride = ride,
                        selected = ride.id in selectedIds,
                        onToggle = {
                            selectedIds = if (ride.id in selectedIds) selectedIds - ride.id else selectedIds + ride.id
                        },
                    )
                } else {
                    RideCard(
                        ride = ride,
                        groups = groups,
                        healthSyncEnabled = healthSyncEnabled,
                        onDelete = { onDeleteRide(ride.id) },
                        onAddToGroup = { showAddToGroupDialog = ride.id },
                        onRemoveFromGroup = { onRemoveRidesFromGroup(listOf(ride.id)) },
                        onLoadLocationPoints = onLoadLocationPoints,
                        onExportGpx = onExportGpx,
                    )
                }
                Spacer(Modifier.height(11.dp))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun RideSummaryHero(rides: List<RideEntity>) {
    if (rides.isEmpty()) return
    val velo = LocalVeloColors.current
    val now = remember { Calendar.getInstance() }
    val weekCutoff = remember { (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis }
    val monthCutoff = remember { (now.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis }
    val yearCutoff = remember { (now.clone() as Calendar).apply { add(Calendar.YEAR, -1) }.timeInMillis }

    val weekRides = remember(rides) { rides.filter { it.startTime >= weekCutoff } }
    val weekKm = remember(weekRides) { weekRides.sumOf { it.distanceKm } }
    val monthKm = remember(rides) { rides.filter { it.startTime >= monthCutoff }.sumOf { it.distanceKm } }
    val yearKm = remember(rides) { rides.filter { it.startTime >= yearCutoff }.sumOf { it.distanceKm } }
    val totalDuration = remember(rides) { rides.sumOf { it.endTime - it.startTime } }

    val dailyDistances = remember(weekRides) {
        val cal = Calendar.getInstance()
        val days = (0..6).map { daysAgo ->
            val dayCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val dayStart = (dayCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val dayEnd = dayStart + 86_400_000L
            weekRides.filter { it.startTime in dayStart until dayEnd }.sumOf { it.distanceKm }
        }.reversed()
        days
    }
    val maxDaily = dailyDistances.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val topThreshold = maxDaily * 0.6

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, AppShapes.HeroCard)
            .border(1.dp, velo.hairline, AppShapes.HeroCard)
            .padding(18.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.stats_week).uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = HankenGrotesk,
                    letterSpacing = 0.72.sp,
                    color = velo.textFaint,
                )
                Text(
                    "%d %s".format(weekRides.size, stringResource(R.string.tab_trip_memory).lowercase()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = HankenGrotesk,
                    color = velo.textFaint,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "%.0f".format(weekKm),
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = HankenGrotesk,
                    letterSpacing = (-1.26).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "km totaal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = HankenGrotesk,
                    color = velo.textDim,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                dailyDistances.forEach { km ->
                    val fraction = (km / maxDaily).toFloat().coerceIn(0.05f, 1f)
                    val isTop = km >= topThreshold && km > 0
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(fraction)
                            .background(
                                if (isTop) velo.accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                                RoundedCornerShape(5.dp),
                            )
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(velo.hairline)
            )

            Spacer(Modifier.height(13.dp))

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(
                        formatDuration(totalDuration),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = HankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.trip_memory_duration).lowercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = HankenGrotesk,
                        color = velo.textFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "%.0f km".format(monthKm),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = HankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.stats_month).lowercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = HankenGrotesk,
                        color = velo.textFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "%.0f km".format(yearKm),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = HankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.stats_year).lowercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = HankenGrotesk,
                        color = velo.textFaint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onGroup: () -> Unit, onMerge: () -> Unit) {
    val velo = LocalVeloColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .clickable(onClick = onGroup)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    Icons.Filled.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = velo.accent,
                )
                Text(
                    stringResource(R.string.trip_memory_create_group),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = HankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                .clickable(onClick = onMerge)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MergeType,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = velo.accent,
                )
                Text(
                    stringResource(R.string.trip_memory_merge_rides),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = HankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                )
            }
        }
    }
}

@Composable
private fun GroupChipRow(
    groups: List<RideGroupEntity>,
    activeGroupId: Long?,
    onSelectGroup: (Long?) -> Unit,
    onCreateGroup: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = activeGroupId == null,
            onClick = { onSelectGroup(null) },
            label = { Text(stringResource(R.string.trip_memory_all_rides)) },
        )
        groups.forEach { group ->
            FilterChip(
                selected = activeGroupId == group.id,
                onClick = { onSelectGroup(group.id) },
                label = { Text(group.name) },
                leadingIcon = {
                    Icon(
                        if (activeGroupId == group.id) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        AssistChip(
            onClick = onCreateGroup,
            label = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.trip_memory_create_group), modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun ActiveGroupHeader(
    group: RideGroupEntity?,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (group == null) return
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        RenameGroupDialog(
            currentName = group.name,
            onDismiss = { showRenameDialog = false },
            onRename = { name -> showRenameDialog = false; onRename(group.id, name) },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.trip_memory_delete_group_confirm_title)) },
            text = { Text(stringResource(R.string.trip_memory_delete_group_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete(group.id) }) {
                    Text(stringResource(R.string.trip_memory_delete_group))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.disclaimer_acknowledge)) }
            },
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.a11y_folder), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.a11y_more_options))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_memory_rename_group)) },
                    onClick = { showMenu = false; showRenameDialog = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.trip_memory_delete_group)) },
                    onClick = { showMenu = false; showDeleteDialog = true },
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_memory_create_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.trip_memory_group_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.trip_memory_create_group))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.photo_crop_cancel)) }
        },
    )
}

@Composable
private fun RenameGroupDialog(currentName: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_memory_rename_group)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.trip_memory_group_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.trip_memory_rename_group))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.photo_crop_cancel)) }
        },
    )
}

@Composable
private fun AddToGroupDialog(
    groups: List<RideGroupEntity>,
    onDismiss: () -> Unit,
    onSelectGroup: (Long) -> Unit,
    onCreateNewGroup: (String) -> Unit,
) {
    var showNewGroupInput by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.trip_memory_add_to_group)) },
        text = {
            Column {
                groups.forEach { group ->
                    TextButton(
                        onClick = { onSelectGroup(group.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = stringResource(R.string.a11y_folder), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(group.name, modifier = Modifier.weight(1f))
                    }
                }
                if (showNewGroupInput) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text(stringResource(R.string.trip_memory_group_name_hint)) },
                        singleLine = true,
                    )
                } else {
                    TextButton(onClick = { showNewGroupInput = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.a11y_add), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.trip_memory_create_group), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            if (showNewGroupInput) {
                TextButton(onClick = { onCreateNewGroup(newGroupName.trim()) }, enabled = newGroupName.isNotBlank()) {
                    Text(stringResource(R.string.trip_memory_create_group))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.photo_crop_cancel)) }
        },
    )
}

@Composable
private fun MergeSelectableRideCard(ride: RideEntity, selected: Boolean, onToggle: () -> Unit) {
    val velo = LocalVeloColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) velo.accentWeak else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(22.dp),
            )
            .border(
                1.dp,
                if (selected) velo.accent else velo.hairline,
                RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onToggle)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dateFormat.format(Date(ride.startTime)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = HankenGrotesk,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "%.1f km · %s".format(ride.distanceKm, formatDuration(ride.endTime - ride.startTime)),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = HankenGrotesk,
                    color = velo.textDim2,
                )
            }
        }
    }
}

@Composable
private fun RideCard(
    ride: RideEntity,
    groups: List<RideGroupEntity>,
    healthSyncEnabled: Boolean,
    onDelete: () -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: () -> Unit,
    onLoadLocationPoints: (Long, (List<LocationPointEntity>) -> Unit) -> Unit,
    onExportGpx: (RideEntity, List<LocationPointEntity>) -> Unit,
) {
    val velo = LocalVeloColors.current
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var locationPoints by remember { mutableStateOf<List<LocationPointEntity>?>(null) }

    LaunchedEffect(expanded) {
        if (expanded && locationPoints == null) {
            onLoadLocationPoints(ride.id) { locationPoints = it }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.trip_memory_delete_confirm_title)) },
            text = { Text(stringResource(R.string.trip_memory_delete_confirm_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.settings_clear_history_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.disclaimer_acknowledge)) }
            },
        )
    }

    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
            .border(1.dp, velo.hairline, RoundedCornerShape(22.dp))
            .clickable { expanded = !expanded }
            .padding(14.dp)
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .background(
                            if (expanded) velo.accentWeak else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            RoundedCornerShape(13.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = null,
                        tint = if (expanded) velo.accent else velo.textDim,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        dateFormat.format(Date(ride.startTime)),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = HankenGrotesk,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "%.1f km · %s".format(ride.distanceKm, formatDuration(ride.endTime - ride.startTime)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = HankenGrotesk,
                        color = velo.textDim2,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp).rotate(chevronRotation),
                    tint = velo.iconInactive,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 13.dp)) {
                    locationPoints?.takeIf { it.size >= 2 }?.let { points ->
                        RouteMapCanvas(points = points)
                        Spacer(Modifier.height(12.dp))
                    }

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        RideStatCell("%.1f".format(ride.avgSpeedKmh), stringResource(R.string.trip_memory_avg_speed).lowercase(), Modifier.weight(1f))
                        RideStatCell("%.1f".format(ride.maxSpeedKmh), stringResource(R.string.trip_memory_max_speed).lowercase(), Modifier.weight(1f))
                        ride.avgHeartRateBpm?.let {
                            RideStatCell("$it", stringResource(R.string.trip_memory_avg_heart_rate).lowercase(), Modifier.weight(1f))
                        } ?: RideStatCell("—", "bpm", Modifier.weight(1f))
                        ride.avgCadenceRpm?.let {
                            RideStatCell("$it", stringResource(R.string.trip_memory_avg_cadence).lowercase(), Modifier.weight(1f))
                        } ?: RideStatCell("—", "tpm", Modifier.weight(1f))
                        RideStatCell(
                            ride.elevationGainM?.let { "%.0f m".format(it) } ?: "0 m",
                            stringResource(R.string.trip_memory_elevation_gain).lowercase(),
                            Modifier.weight(1f),
                        )
                        val batteryText = ride.endBatterySoc?.let { "$it%" } ?: "—"
                        RideStatCell(batteryText, stringResource(R.string.trip_memory_battery).lowercase(), Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(velo.hairline))
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth()) {
                        locationPoints?.takeIf { it.size >= 2 }?.let { pts ->
                            val context = LocalContext.current
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable { onExportGpx(ride, pts) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp), tint = velo.accent)
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    stringResource(R.string.trip_memory_export_gpx),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = HankenGrotesk,
                                    color = velo.accent,
                                )
                            }
                        }
                        if (healthSyncEnabled) {
                            val context = LocalContext.current
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable { HealthConnectBridge.openHealthConnect(context) }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.trip_memory_view_in_health),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = HankenGrotesk,
                                    color = velo.textDim2,
                                )
                            }
                        }
                        if (ride.groupId != null) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable(onClick = onRemoveFromGroup)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.trip_memory_remove_from_group),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = HankenGrotesk,
                                    color = velo.textDim2,
                                )
                            }
                        } else if (groups.isNotEmpty()) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .clickable(onClick = onAddToGroup)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp), tint = velo.textDim2)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.trip_memory_add_to_group),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = HankenGrotesk,
                                    color = velo.textDim2,
                                )
                            }
                        }
                        Row(
                            Modifier
                                .weight(1f)
                                .clickable { confirmDelete = true }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = velo.textDim2)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                stringResource(R.string.trip_memory_delete_ride),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = HankenGrotesk,
                                color = velo.textDim2,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RideStatCell(value: String, label: String, modifier: Modifier = Modifier) {
    val velo = LocalVeloColors.current
    Column(modifier) {
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = HankenGrotesk,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = HankenGrotesk,
            color = velo.textFaint,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
