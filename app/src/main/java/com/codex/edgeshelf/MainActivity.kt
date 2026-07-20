package com.codex.edgeshelf

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import com.codex.edgeshelf.permissions.PermissionCoordinator
import com.codex.edgeshelf.ui.EdgeShelfScreen
import com.codex.edgeshelf.ui.EdgeShelfViewModel
import com.codex.edgeshelf.ui.AppPickerScreen
import com.codex.edgeshelf.ui.theme.EdgeShelfTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: EdgeShelfViewModel
    private lateinit var permissionCoordinator: PermissionCoordinator
    private var enableAfterOverlayGrant = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAfterOverlayGrant = savedInstanceState?.getBoolean(KEY_ENABLE_AFTER_OVERLAY) == true
        viewModel = ViewModelProvider(this)[EdgeShelfViewModel::class.java]
        permissionCoordinator = PermissionCoordinator(this)

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            LaunchedEffect(uiState.finishPickerHost) {
                if (uiState.finishPickerHost) {
                    viewModel.consumeFinishPickerHost()
                    finish()
                }
            }
            EdgeShelfTheme {
                if (uiState.picker.isOpen) {
                    AppPickerScreen(
                        state = uiState.picker,
                        onToggle = viewModel::togglePickerApp,
                        onDone = viewModel::saveAppPicker,
                        onCancel = ::closeAppPicker,
                        onRetry = viewModel::retryAppCatalog,
                    )
                } else {
                    EdgeShelfScreen(
                        uiState = uiState,
                        onEnabledChange = ::setShelfEnabled,
                        onSideChange = viewModel::setSide,
                        onAutoStartChange = viewModel::setAutoStart,
                        onAutoHideChange = viewModel::setAutoHide,
                        onManageApps = { viewModel.openAppPicker() },
                        onClearRecents = viewModel::clearRecents,
                        onOpenOverlayPermission = {
                            openSystemSettings(permissionCoordinator.overlayIntent())
                        },
                        onRequestNotificationPermission = ::requestNotificationPermission,
                        onOpenUsagePermission = {
                            openSystemSettings(permissionCoordinator.usageAccessIntent())
                        },
                        onOpenBatterySettings = {
                            openSystemSettings(permissionCoordinator.batteryOptimizationIntent())
                        },
                    )
                }
            }
        }
        consumeLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!::viewModel.isInitialized) return

        val permissions = viewModel.refreshPermissions()
        if (enableAfterOverlayGrant) {
            enableAfterOverlayGrant = false
            if (permissions.overlayGranted) viewModel.setEnabled(true)
        }
        viewModel.syncService()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_ENABLE_AFTER_OVERLAY, enableAfterOverlayGrant)
        super.onSaveInstanceState(outState)
    }

    private fun setShelfEnabled(enabled: Boolean) {
        if (!enabled) {
            enableAfterOverlayGrant = false
            viewModel.setEnabled(false)
            return
        }

        val permissions = viewModel.refreshPermissions()
        if (permissions.overlayGranted) {
            viewModel.setEnabled(true)
        } else {
            enableAfterOverlayGrant = true
            openSystemSettings(permissionCoordinator.overlayIntent())
        }
    }

    private fun requestNotificationPermission() {
        val permission = permissionCoordinator.notificationPermission() ?: return
        notificationPermissionLauncher.launch(permission)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        if (intent?.action != ACTION_OPEN_APP_PICKER) return
        intent.action = null
        viewModel.openAppPicker(returnToPreviousApp = true)
    }

    private fun closeAppPicker() {
        if (viewModel.dismissAppPicker()) finish()
    }

    private fun openSystemSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    companion object {
        const val ACTION_OPEN_APP_PICKER = "com.codex.edgeshelf.action.OPEN_APP_PICKER"
        const val KEY_ENABLE_AFTER_OVERLAY = "enable_after_overlay"
    }
}
