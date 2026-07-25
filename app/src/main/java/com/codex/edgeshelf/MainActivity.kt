package com.codex.edgeshelf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codex.edgeshelf.permissions.PermissionCoordinator
import com.codex.edgeshelf.recording.RecordingService
import com.codex.edgeshelf.ui.EdgeShelfScreen
import com.codex.edgeshelf.ui.EdgeShelfViewModel
import com.codex.edgeshelf.ui.AppPickerScreen
import com.codex.edgeshelf.ui.AppPickerPurpose
import com.codex.edgeshelf.ui.theme.EdgeShelfTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: EdgeShelfViewModel
    private lateinit var permissionCoordinator: PermissionCoordinator
    private var enableAfterOverlayGrant = false
    private var startRecordingWhenVisible = false
    private var finishAfterRecordingPermission = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshPermissions()
    }

    private val recordingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecordingWhenVisible = true
            scheduleRecordingStart()
        } else {
            Toast.makeText(
                this,
                getString(R.string.recording_permission_required),
                Toast.LENGTH_SHORT,
            ).show()
            finishRecordingPermissionHost()
        }
    }

    private val recordingDeleteConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.onRecordingDeleteConsentResult(
            approved = result.resultCode == android.app.Activity.RESULT_OK,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableAfterOverlayGrant = savedInstanceState?.getBoolean(KEY_ENABLE_AFTER_OVERLAY) == true
        startRecordingWhenVisible = savedInstanceState?.getBoolean(KEY_START_RECORDING) == true
        finishAfterRecordingPermission =
            savedInstanceState?.getBoolean(KEY_FINISH_AFTER_RECORDING_PERMISSION) == true
        viewModel = ViewModelProvider(this)[EdgeShelfViewModel::class.java]
        permissionCoordinator = PermissionCoordinator(this)

        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val deleteConsentRequest by
                viewModel.recordingDeleteConsentRequest.collectAsStateWithLifecycle()
            LaunchedEffect(deleteConsentRequest?.token) {
                deleteConsentRequest?.let { request ->
                    viewModel.consumeRecordingDeleteConsentRequest(request.token)
                    runCatching {
                        recordingDeleteConsentLauncher.launch(
                            IntentSenderRequest.Builder(request.intentSender).build(),
                        )
                    }.onFailure {
                        viewModel.onRecordingDeleteConsentResult(approved = false)
                    }
                }
            }
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
                        versionName = BuildConfig.VERSION_NAME,
                        onEnabledChange = ::setShelfEnabled,
                        onModeChange = viewModel::setMode,
                        onSideChange = viewModel::setSide,
                        onEdgeDistancePreview = viewModel::previewEdgeDistance,
                        onEdgeDistanceCommit = viewModel::commitEdgeDistance,
                        onEdgeDistancePreviewClear = viewModel::clearEdgeDistancePreview,
                        onAutoStartChange = viewModel::setAutoStart,
                        onAutoHideChange = viewModel::setAutoHide,
                        onRecordingEnabledChange = viewModel::setRecordingEnabled,
                        onManageApps = { viewModel.openAppPicker() },
                        onManagePinnedApps = {
                            viewModel.openAppPicker(purpose = AppPickerPurpose.PINNED)
                        },
                        onClearRecents = viewModel::clearRecents,
                        onRefreshRecordings = viewModel::refreshRecordings,
                        onToggleRecordingPlayback = viewModel::toggleRecordingPlayback,
                        onDeleteRecording = viewModel::deleteRecording,
                        onClearRecordingDeleteError = viewModel::clearRecordingDeleteError,
                        onRefreshScreenshots = viewModel::refreshScreenshots,
                        onDeleteScreenshot = viewModel::deleteScreenshot,
                        onClearScreenshotDeleteError = viewModel::clearScreenshotDeleteError,
                        onOpenScreenshotAccess = {
                            openSystemSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
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
        viewModel.refreshRecordings()
        viewModel.refreshScreenshots()
        scheduleRecordingStart()
    }

    override fun onPostResume() {
        super.onPostResume()
        scheduleRecordingStart()
    }

    override fun onPause() {
        if (::viewModel.isInitialized) {
            viewModel.stopRecordingPlayback()
            viewModel.clearEdgeDistancePreview()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_ENABLE_AFTER_OVERLAY, enableAfterOverlayGrant)
        outState.putBoolean(KEY_START_RECORDING, startRecordingWhenVisible)
        outState.putBoolean(
            KEY_FINISH_AFTER_RECORDING_PERMISSION,
            finishAfterRecordingPermission,
        )
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
        when (intent?.action) {
            ACTION_OPEN_APP_PICKER -> {
                intent.action = null
                viewModel.openAppPicker(returnToPreviousApp = true)
            }
            ACTION_OPEN_RECENT_SETTINGS -> {
                intent.action = null
                viewModel.dismissAppPicker()
                viewModel.refreshPermissions()
            }
            ACTION_REQUEST_RECORDING_PERMISSION -> {
                intent.action = null
                finishAfterRecordingPermission = true
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    startRecordingWhenVisible = true
                    scheduleRecordingStart()
                } else {
                    recordingPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            else -> return
        }
    }

    private fun scheduleRecordingStart() {
        if (!startRecordingWhenVisible) return
        window.decorView.post {
            if (!startRecordingWhenVisible || isFinishing || isDestroyed ||
                !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return@post
            }
            startRecordingWhenVisible = false
            runCatching { RecordingService.start(this) }
                .onFailure {
                    Toast.makeText(
                        this,
                        getString(R.string.recording_start_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            if (finishAfterRecordingPermission) finishRecordingPermissionHost()
        }
    }

    private fun finishRecordingPermissionHost() {
        if (!finishAfterRecordingPermission) return
        finishAfterRecordingPermission = false
        finish()
        overridePendingTransition(0, 0)
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
        const val ACTION_OPEN_RECENT_SETTINGS = "com.codex.edgeshelf.action.OPEN_RECENT_SETTINGS"
        const val ACTION_REQUEST_RECORDING_PERMISSION =
            "com.codex.edgeshelf.action.REQUEST_RECORDING_PERMISSION"
        const val KEY_ENABLE_AFTER_OVERLAY = "enable_after_overlay"
        const val KEY_START_RECORDING = "start_recording_when_visible"
        const val KEY_FINISH_AFTER_RECORDING_PERMISSION = "finish_after_recording_permission"
    }
}
