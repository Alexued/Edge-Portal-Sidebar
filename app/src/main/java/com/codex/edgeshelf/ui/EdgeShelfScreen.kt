package com.codex.edgeshelf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.codex.edgeshelf.R
import com.codex.edgeshelf.data.ShelfMode
import com.codex.edgeshelf.data.ShelfSide
import com.codex.edgeshelf.recording.RecordingEntry
import com.codex.edgeshelf.recording.RecordingPlaybackState
import com.codex.edgeshelf.recording.formatRecordingDuration
import com.codex.edgeshelf.recording.formatRecordingFileSize
import com.codex.edgeshelf.recording.formatRecordingTimestamp
import com.codex.edgeshelf.ui.theme.InkMuted
import com.codex.edgeshelf.ui.theme.Jade
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun EdgeShelfScreen(
    uiState: EdgeShelfUiState,
    versionName: String,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (ShelfMode) -> Unit,
    onSideChange: (ShelfSide) -> Unit,
    onEdgeDistancePreview: (Float) -> Unit,
    onEdgeDistanceCommit: (Float) -> Unit,
    onEdgeDistancePreviewClear: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onRecordingEnabledChange: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    onManagePinnedApps: () -> Unit,
    onClearRecents: () -> Unit,
    onRefreshRecordings: () -> Unit,
    onToggleRecordingPlayback: (String) -> Unit,
    onDeleteRecording: (String) -> Unit,
    onClearRecordingDeleteError: () -> Unit,
    onRefreshScreenshots: () -> Unit,
    onDeleteScreenshot: (String) -> Unit,
    onClearScreenshotDeleteError: () -> Unit,
    onOpenScreenshotAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenUsagePermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val settings = uiState.settings
    val permissions = uiState.permissions
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.recording_deleted)
    val screenshotDeletedMessage = stringResource(R.string.screenshot_deleted)
    LaunchedEffect(uiState.recordingLibrary.deleteSuccessSerial) {
        if (uiState.recordingLibrary.deleteSuccessSerial > 0L) {
            snackbarHostState.showSnackbar(deletedMessage)
        }
    }
    LaunchedEffect(uiState.screenshotLibrary.deleteSuccessSerial) {
        if (uiState.screenshotLibrary.deleteSuccessSerial > 0L) {
            snackbarHostState.showSnackbar(screenshotDeletedMessage)
        }
    }
    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
        item { Header(versionName = versionName) }
        item {
            StatusCard(
                enabled = settings.enabled,
                overlayGranted = permissions.overlayGranted,
                side = settings.side,
                onEnabledChange = onEnabledChange,
            )
        }
        item {
            SectionTitle(
                title = stringResource(R.string.shelf_tools_title),
                description = stringResource(R.string.shelf_tools_description),
            )
            Spacer(Modifier.height(10.dp))
            ShelfToolsCard(
                recordingEnabled = settings.recordingEnabled,
                screenshotSupported = uiState.screenshotSupported,
                screenshotServiceConnected = uiState.screenshotServiceConnected,
                pinnedAppsCount = settings.pinnedApps.size,
                onRecordingEnabledChange = onRecordingEnabledChange,
                onOpenScreenshotAccess = onOpenScreenshotAccess,
                onManagePinnedApps = onManagePinnedApps,
            )
        }
        item {
            SectionTitle(
                title = stringResource(R.string.shelf_content_title),
                description = stringResource(R.string.shelf_content_description),
            )
            Spacer(Modifier.height(10.dp))
            ContentModeCard(
                mode = settings.mode,
                usageAccessGranted = permissions.usageAccessGranted,
                fixedAppsCount = settings.favorites.size,
                onModeChange = onModeChange,
                onManageApps = onManageApps,
                onOpenUsagePermission = onOpenUsagePermission,
            )
        }
        item {
            SectionTitle(
                title = stringResource(R.string.permissions_title),
                description = stringResource(R.string.setup_hint),
            )
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                PermissionGuide(
                    permissions = listOf(
                        PermissionItem(1, stringResource(R.string.overlay_access), stringResource(R.string.overlay_access_description), stringResource(R.string.permission_required), permissions.overlayGranted, onOpenOverlayPermission),
                        PermissionItem(2, stringResource(R.string.notification_access), stringResource(R.string.notification_access_description), stringResource(R.string.permission_recommended), permissions.notificationsGranted, onRequestNotificationPermission),
                        PermissionItem(
                            3,
                            stringResource(R.string.usage_access),
                            stringResource(R.string.usage_access_description),
                            stringResource(
                                if (settings.mode == ShelfMode.RECENT) {
                                    R.string.permission_recommended
                                } else {
                                    R.string.permission_optional
                                },
                            ),
                            permissions.usageAccessGranted,
                            onOpenUsagePermission,
                        ),
                        PermissionItem(4, stringResource(R.string.battery_access), stringResource(R.string.battery_access_description), stringResource(R.string.permission_optional), permissions.batteryOptimizationIgnored, onOpenBatterySettings),
                    ),
                )
            }
        }
        item {
            SectionTitle(title = stringResource(R.string.behavior_title))
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                Column {
                    SideSelector(selected = settings.side, onSelected = onSideChange)
                    SettingDivider()
                    EdgeDistanceControl(
                        currentDistanceDp =
                            uiState.edgeDistancePreviewDp ?: settings.edgeDistanceDp,
                        safetyFloorDp = uiState.edgeDistanceSafetyFloorDp,
                        shelfEnabled = settings.enabled,
                        onPreview = onEdgeDistancePreview,
                        onCommit = onEdgeDistanceCommit,
                        onClearPreview = onEdgeDistancePreviewClear,
                    )
                    SettingDivider()
                    ToggleRow(stringResource(R.string.auto_start), stringResource(R.string.auto_start_description), settings.autoStart, onAutoStartChange)
                    SettingDivider()
                    ToggleRow(stringResource(R.string.auto_hide), stringResource(R.string.auto_hide_description), settings.autoHide, onAutoHideChange)
                }
            }
        }
        item {
            SectionTitle(title = stringResource(R.string.local_data_title))
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                Column {
                    DataSummaryRow(
                        title = stringResource(R.string.shelf_launch_history),
                        value = stringResource(R.string.items_count, settings.recents.size),
                        description = stringResource(R.string.shelf_launch_history_description),
                        action = {
                            OutlinedButton(
                                onClick = onClearRecents,
                                enabled = settings.recents.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(stringResource(R.string.clear_local_history)) }
                        },
                    )
                }
            }
        }
        item {
            ScreenshotLibrarySection(
                state = uiState.screenshotLibrary,
                onRefresh = onRefreshScreenshots,
                onDelete = onDeleteScreenshot,
                onClearDeleteError = onClearScreenshotDeleteError,
            )
        }
        item {
            RecordingLibrarySection(
                state = uiState.recordingLibrary,
                onRefresh = onRefreshRecordings,
                onTogglePlayback = onToggleRecordingPlayback,
                onDelete = onDeleteRecording,
                onClearDeleteError = onClearRecordingDeleteError,
            )
        }
        item {
            Text(
                text = stringResource(R.string.privacy_note),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ShelfToolsCard(
    recordingEnabled: Boolean,
    screenshotSupported: Boolean,
    screenshotServiceConnected: Boolean,
    pinnedAppsCount: Int,
    onRecordingEnabledChange: (Boolean) -> Unit,
    onOpenScreenshotAccess: () -> Unit,
    onManagePinnedApps: () -> Unit,
) {
    SettingsCard {
        Column {
            ToggleRow(
                title = stringResource(R.string.recording_tool),
                description = stringResource(R.string.recording_tool_description),
                checked = recordingEnabled,
                onCheckedChange = onRecordingEnabledChange,
            )
            SettingDivider()
            DataSummaryRow(
                title = stringResource(R.string.screenshot_tool),
                value = stringResource(
                    when {
                        !screenshotSupported -> R.string.screenshot_unsupported
                        screenshotServiceConnected -> R.string.screenshot_service_connected
                        else -> R.string.screenshot_service_disconnected
                    },
                ),
                description = stringResource(R.string.screenshot_tool_description),
                action = if (screenshotSupported && !screenshotServiceConnected) {
                    {
                        OutlinedButton(
                            onClick = onOpenScreenshotAccess,
                            shape = RoundedCornerShape(12.dp),
                        ) { Text(stringResource(R.string.grant_screenshot_access)) }
                    }
                } else {
                    null
                },
            )
            SettingDivider()
            DataSummaryRow(
                title = stringResource(R.string.pinned_apps),
                value = stringResource(R.string.selected_limit_count, pinnedAppsCount, 3),
                description = stringResource(R.string.pinned_apps_description),
                action = {
                    OutlinedButton(
                        onClick = onManagePinnedApps,
                        shape = RoundedCornerShape(12.dp),
                    ) { Text(stringResource(R.string.manage_pinned_apps)) }
                },
            )
        }
    }
}

@Composable
private fun RecordingLibrarySection(
    state: RecordingLibraryUiState,
    onRefresh: () -> Unit,
    onTogglePlayback: (String) -> Unit,
    onDelete: (String) -> Unit,
    onClearDeleteError: () -> Unit,
) {
    val refreshDescription = stringResource(R.string.recordings_refresh)
    var visibleEntryCount by rememberSaveable { mutableIntStateOf(INITIAL_RECORDING_ROWS) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleEntries = state.entries.take(visibleEntryCount)
    val deleteDialogEntry = state.entries.firstOrNull { it.stableId == pendingDeleteId }
    LaunchedEffect(pendingDeleteId, state.entries) {
        if (pendingDeleteId != null && deleteDialogEntry == null) pendingDeleteId = null
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SectionTitle(
                title = stringResource(R.string.recordings_title),
                description = stringResource(R.string.recordings_description),
            )
        }
        IconButton(
            onClick = onRefresh,
            enabled = !state.isLoading && state.deletingId == null,
            modifier = Modifier.semantics {
                contentDescription = refreshDescription
            },
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (state.loadFailed && state.entries.isNotEmpty()) {
        Text(
            text = stringResource(R.string.recordings_load_failed),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.recordingActive) {
        Text(
            text = stringResource(R.string.recording_playback_blocked),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    SettingsCard {
        when {
            state.isLoading && state.entries.isEmpty() -> RecordingLoadingRow()
            state.loadFailed && state.entries.isEmpty() ->
                RecordingLoadFailureRow(onRetry = onRefresh)
            state.entries.isEmpty() -> RecordingEmptyRow()
            else -> Column {
                visibleEntries.forEachIndexed { index, entry ->
                    key(entry.stableId) {
                        RecordingRow(
                            entry = entry,
                            playback = state.playback.takeIf { it.activeId == entry.stableId },
                            failed = state.playback.errorId == entry.stableId,
                            recordingActive = state.recordingActive,
                            deleting = state.deletingId == entry.stableId,
                            deleteActionsDisabled = state.isLoading || state.deletingId != null,
                            onTogglePlayback = onTogglePlayback,
                            onRequestDelete = {
                                onClearDeleteError()
                                pendingDeleteId = entry.stableId
                            },
                        )
                    }
                    if (index < visibleEntries.lastIndex) SettingDivider()
                }
                if (visibleEntries.size < state.entries.size) {
                    SettingDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        OutlinedButton(
                            onClick = {
                                visibleEntryCount =
                                    (visibleEntryCount + RECORDING_PAGE_SIZE).coerceAtMost(state.entries.size)
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                stringResource(
                                    R.string.recordings_show_more,
                                    state.entries.size - visibleEntries.size,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
    deleteDialogEntry?.let { entry ->
        RecordingDeleteDialog(
            entry = entry,
            deleting = state.deletingId == entry.stableId,
            failed = state.deleteFailedId == entry.stableId,
            onConfirm = { onDelete(entry.stableId) },
            onDismiss = {
                onClearDeleteError()
                pendingDeleteId = null
            },
        )
    }
}

@Composable
private fun RecordingLoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.recording_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecordingLoadFailureRow(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
        ResponsiveActionRow(
            content = {
                Text(
                    text = stringResource(R.string.recordings_load_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            action = {
                OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.recordings_retry))
                }
            },
        )
    }
}

@Composable
private fun RecordingEmptyRow() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.recordings_empty),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.recordings_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun RecordingDeleteDialog(
    entry: RecordingEntry,
    deleting: Boolean,
    failed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val timestampLabel = formatRecordingTimestamp(entry.createdAtEpochMs)
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.recording_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        R.string.recording_delete_message,
                        timestampLabel,
                        formatRecordingDuration(entry.durationMs),
                        formatRecordingFileSize(entry.sizeBytes),
                    ),
                )
                if (failed) {
                    Text(
                        text = stringResource(R.string.recording_delete_failed),
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !deleting,
                modifier = Modifier.widthIn(min = 96.dp),
            ) {
                if (deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.recording_deleting))
                } else {
                    Text(
                        text = stringResource(
                            if (failed) {
                                R.string.recording_delete_retry
                            } else {
                                R.string.recording_delete_confirm
                            },
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.recording_delete_cancel))
            }
        },
    )
}

@Composable
private fun RecordingRow(
    entry: RecordingEntry,
    playback: RecordingPlaybackState?,
    failed: Boolean,
    recordingActive: Boolean,
    deleting: Boolean,
    deleteActionsDisabled: Boolean,
    onTogglePlayback: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val active = playback != null
    val progress = if (playback != null && playback.durationMs > 0L) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val preparing = playback?.isPreparing == true
    val playing = playback?.isPlaying == true
    val timestampLabel = formatRecordingTimestamp(entry.createdAtEpochMs)
    val effectiveDuration = if (playback != null && playback.durationMs > 0L) {
        playback.durationMs
    } else {
        entry.durationMs
    }
    val durationLabel = if (playback != null && !preparing) {
        stringResource(
            R.string.recording_progress,
            formatRecordingDuration(playback.positionMs),
            formatRecordingDuration(effectiveDuration),
        )
    } else {
        formatRecordingDuration(effectiveDuration)
    }
    val actionDescription = when {
        recordingActive -> stringResource(R.string.recording_playback_blocked)
        preparing -> stringResource(R.string.recording_preparing_item, timestampLabel)
        playing -> stringResource(R.string.recording_pause_item, timestampLabel)
        active -> stringResource(R.string.recording_resume_item, timestampLabel)
        else -> stringResource(R.string.recording_play_item, timestampLabel)
    }
    val metadataText = if (failed) {
        stringResource(R.string.recording_playback_failed)
    } else {
        stringResource(
            R.string.recording_metadata,
            durationLabel,
            formatRecordingFileSize(entry.sizeBytes),
        )
    }
    val deleteDescription = stringResource(R.string.recording_delete_action, timestampLabel)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = timestampLabel,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = metadataText,
                modifier = Modifier.fillMaxWidth(),
                color = if (failed) MaterialTheme.colorScheme.error else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodySmall,
            )
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .clearAndSetSemantics { },
                color = if (active) Jade else MaterialTheme.colorScheme.outlineVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
        IconButton(
            onClick = { onTogglePlayback(entry.stableId) },
            enabled = !recordingActive && !preparing && !deleting,
            modifier = Modifier.semantics { contentDescription = actionDescription },
        ) {
            if (preparing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else if (playing) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_media_pause),
                    contentDescription = null,
                    tint = Jade,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (recordingActive) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        Jade
                    },
                )
            }
        }
        IconButton(
            onClick = onRequestDelete,
            enabled = !deleteActionsDisabled,
            modifier = Modifier.semantics { contentDescription = deleteDescription },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            ),
        ) {
            if (deleting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            }
        }
    }
}

private const val INITIAL_RECORDING_ROWS = 20
private const val RECORDING_PAGE_SIZE = 20

@Composable
private fun ContentModeCard(
    mode: ShelfMode,
    usageAccessGranted: Boolean,
    fixedAppsCount: Int,
    onModeChange: (ShelfMode) -> Unit,
    onManageApps: () -> Unit,
    onOpenUsagePermission: () -> Unit,
) {
    SettingsCard {
        Column {
            ModeSelector(mode = mode, onModeChange = onModeChange)
            SettingDivider()
            if (mode == ShelfMode.RECENT) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.recent_mode_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!usageAccessGranted) {
                        ResponsiveActionRow(
                            content = {
                                Text(
                                    text = stringResource(R.string.recent_mode_permission_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            action = {
                                OutlinedButton(
                                    onClick = onOpenUsagePermission,
                                    shape = RoundedCornerShape(12.dp),
                                ) { Text(stringResource(R.string.grant_usage_access)) }
                            },
                        )
                    }
                }
            } else {
                DataSummaryRow(
                    title = stringResource(R.string.fixed_apps),
                    value = stringResource(R.string.items_count, fixedAppsCount),
                    description = stringResource(R.string.fixed_mode_description),
                    action = {
                        OutlinedButton(onClick = onManageApps, shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.manage_apps))
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: ShelfMode, onModeChange: (ShelfMode) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 15.dp)
            .selectableGroup(),
    ) {
        val stackChoices = maxWidth < 260.dp || fontScale > 1.2f
        if (stackChoices) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeButton(
                    text = stringResource(R.string.recent_mode),
                    selected = mode == ShelfMode.RECENT,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onModeChange(ShelfMode.RECENT) },
                )
                ModeButton(
                    text = stringResource(R.string.fixed_mode),
                    selected = mode == ShelfMode.FIXED,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onModeChange(ShelfMode.FIXED) },
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModeButton(
                    text = stringResource(R.string.recent_mode),
                    selected = mode == ShelfMode.RECENT,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(ShelfMode.RECENT) },
                )
                ModeButton(
                    text = stringResource(R.string.fixed_mode),
                    selected = mode == ShelfMode.FIXED,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeChange(ShelfMode.FIXED) },
                )
            }
        }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (selected) Jade else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Jade else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResponsiveActionRow(
    content: @Composable () -> Unit,
    action: @Composable () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stackAction = maxWidth < 340.dp || fontScale > 1.15f
        if (stackAction) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
                Box(modifier = Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) { content() }
                action()
            }
        }
    }
}

@Composable
private fun Header(versionName: String) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Keep the metadata below the subtitle when space or enlarged text makes the
        // inline row too tight. The rail mark remains a fixed 38x58dp sibling.
        val stackMetadata = shouldStackHeaderMetadata(maxWidth.value, fontScale)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (stackMetadata) {
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    VersionLabel(versionName)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        VersionLabel(versionName)
                    }
                }
            }
            RailMark()
        }
    }
}

internal fun shouldStackHeaderMetadata(maxWidthDp: Float, fontScale: Float): Boolean =
    maxWidthDp < 360f || fontScale > 1.2f

@Composable
private fun VersionLabel(versionName: String) {
    val normalizedVersion = versionName.trim()
    if (normalizedVersion.isNotEmpty()) {
        Text(
            text = stringResource(R.string.version_label, normalizedVersion),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun RailMark() {
    val markDescription = stringResource(R.string.rail_preview)
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 58.dp)
            .semantics { contentDescription = markDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_legacy),
            contentDescription = null,
            modifier = Modifier.size(38.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun StatusCard(enabled: Boolean, overlayGranted: Boolean, side: ShelfSide, onEnabledChange: (Boolean) -> Unit) {
    val canRun = enabled && overlayGranted
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (canRun) Jade else MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (canRun) 0.dp else 1.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (canRun) Color(0xFFBCE6D5) else MaterialTheme.colorScheme.outline, CircleShape))
            Column(Modifier.weight(1f).padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        canRun -> stringResource(R.string.status_running)
                        enabled -> stringResource(R.string.status_permission_needed)
                        else -> stringResource(R.string.status_stopped)
                    },
                    color = if (canRun) Color.White else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (canRun) stringResource(R.string.status_running_description, stringResource(if (side == ShelfSide.RIGHT) R.string.right else R.string.left)) else stringResource(R.string.status_stopped_description),
                    color = if (canRun) Color.White.copy(alpha = .78f) else InkMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors = if (canRun) SwitchDefaults.colors(checkedThumbColor = Jade, checkedTrackColor = Color.White) else SwitchDefaults.colors(),
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        description?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
    ) { content() }
}

@Composable
private fun SideSelector(selected: ShelfSide, onSelected: (ShelfSide) -> Unit) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.side), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.side_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SideButton(stringResource(R.string.left), selected == ShelfSide.LEFT, Modifier.weight(1f)) { onSelected(ShelfSide.LEFT) }
            SideButton(stringResource(R.string.right), selected == ShelfSide.RIGHT, Modifier.weight(1f)) { onSelected(ShelfSide.RIGHT) }
        }
    }
}

@Composable
private fun SideButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick, modifier, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Jade)) { Text(text) }
    } else {
        OutlinedButton(onClick, modifier, shape = RoundedCornerShape(12.dp)) { Text(text) }
    }
}

@Composable
private fun EdgeDistanceControl(
    currentDistanceDp: Float,
    safetyFloorDp: Float,
    shelfEnabled: Boolean,
    onPreview: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onClearPreview: () -> Unit,
) {
    val minimum = safetyFloorDp.coerceIn(0f, 40f).roundToInt().toFloat()
    val maximum = 40f
    val currentEffective = max(currentDistanceDp, minimum).coerceIn(minimum, maximum)
    var distanceDp by remember(currentEffective, minimum) {
        mutableStateOf(currentEffective)
    }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentEffective, minimum) {
        if (!dragging) distanceDp = currentEffective
    }
    DisposableEffect(Unit) {
        onDispose(onClearPreview)
    }

    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.edge_distance),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.edge_distance_value, distanceDp.roundToInt()),
                color = Jade,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = stringResource(
                if (shelfEnabled) {
                    R.string.edge_distance_description
                } else {
                    R.string.edge_distance_disabled_description
                },
                minimum.roundToInt(),
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Slider(
            value = distanceDp,
            onValueChange = { rawValue ->
                dragging = true
                distanceDp = rawValue.roundToInt().toFloat().coerceIn(minimum, maximum)
                onPreview(distanceDp)
            },
            onValueChangeFinished = {
                dragging = false
                onCommit(distanceDp)
            },
            valueRange = minimum..maximum,
            steps = (maximum - minimum).roundToInt().minus(1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onCheckedChange)
    }
}

@Composable
private fun DataSummaryRow(title: String, value: String, description: String, action: (@Composable () -> Unit)? = null) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp)) {
        val stackAction = action != null && (maxWidth < 340.dp || fontScale > 1.15f)
        val stackHeading = maxWidth < 250.dp || fontScale > 1.3f
        val summary = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (stackHeading) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(value, color = Jade, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(value, color = Jade, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (action == null) {
            summary()
        } else if (stackAction) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                summary()
                Box(modifier = Modifier.align(Alignment.End)) { action() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(end = 12.dp)) { summary() }
                action()
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
