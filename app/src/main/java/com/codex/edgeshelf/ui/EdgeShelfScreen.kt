package com.codex.edgeshelf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.edgeshelf.R
import com.codex.edgeshelf.data.ShelfSide
import com.codex.edgeshelf.ui.theme.InkMuted
import com.codex.edgeshelf.ui.theme.Jade
import com.codex.edgeshelf.ui.theme.JadeSoft

@Composable
fun EdgeShelfScreen(
    uiState: EdgeShelfUiState,
    onEnabledChange: (Boolean) -> Unit,
    onSideChange: (ShelfSide) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onAutoHideChange: (Boolean) -> Unit,
    onManageApps: () -> Unit,
    onClearRecents: () -> Unit,
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item { Header() }
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
                title = stringResource(R.string.permissions_title),
                description = stringResource(R.string.setup_hint),
            )
            Spacer(Modifier.height(10.dp))
            SettingsCard {
                PermissionGuide(
                    permissions = listOf(
                        PermissionItem(1, stringResource(R.string.overlay_access), stringResource(R.string.overlay_access_description), stringResource(R.string.permission_required), permissions.overlayGranted, onOpenOverlayPermission),
                        PermissionItem(2, stringResource(R.string.notification_access), stringResource(R.string.notification_access_description), stringResource(R.string.permission_recommended), permissions.notificationsGranted, onRequestNotificationPermission),
                        PermissionItem(3, stringResource(R.string.usage_access), stringResource(R.string.usage_access_description), stringResource(R.string.permission_optional), permissions.usageAccessGranted, onOpenUsagePermission),
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
                        title = stringResource(R.string.favorites),
                        value = stringResource(R.string.items_count, settings.favorites.size),
                        description = stringResource(R.string.favorites_description),
                        action = {
                            OutlinedButton(
                                onClick = onManageApps,
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(stringResource(R.string.manage_apps)) }
                        },
                    )
                    SettingDivider()
                    DataSummaryRow(
                        title = stringResource(R.string.recents),
                        value = stringResource(R.string.items_count, settings.recents.size),
                        description = stringResource(R.string.recents_description),
                        action = {
                            OutlinedButton(
                                onClick = onClearRecents,
                                enabled = settings.recents.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                            ) { Text(stringResource(R.string.clear)) }
                        },
                    )
                }
            }
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
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.app_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        RailMark()
    }
}

@Composable
private fun RailMark() {
    val markDescription = stringResource(R.string.rail_preview)
    Canvas(
        modifier = Modifier.size(width = 38.dp, height = 58.dp).semantics { contentDescription = markDescription },
    ) {
        drawRoundRect(JadeSoft, Offset(size.width * .16f, 0f), Size(size.width * .84f, size.height), CornerRadius(14.dp.toPx()))
        drawRoundRect(Jade, Offset(size.width * .73f, size.height * .19f), Size(size.width * .18f, size.height * .62f), CornerRadius(5.dp.toPx()))
        drawCircle(Jade, 4.dp.toPx(), Offset(size.width * .41f, size.height * .28f))
        drawCircle(Jade, 4.dp.toPx(), Offset(size.width * .41f, size.height * .50f), style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(Jade, 4.dp.toPx(), Offset(size.width * .41f, size.height * .72f))
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
    Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(value, color = Jade, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        action?.invoke()
    }
}

@Composable
private fun SettingDivider() {
    Spacer(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
