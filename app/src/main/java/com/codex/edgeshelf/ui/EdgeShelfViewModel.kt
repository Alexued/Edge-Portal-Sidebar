package com.codex.edgeshelf.ui

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.edgeshelf.data.AppCatalogRepository
import com.codex.edgeshelf.data.AppInstanceKey
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfSettings
import com.codex.edgeshelf.data.ShelfMode
import com.codex.edgeshelf.data.ShelfSide
import com.codex.edgeshelf.data.ShelfStore
import com.codex.edgeshelf.data.rebindAppInstanceKey
import com.codex.edgeshelf.permissions.PermissionCoordinator
import com.codex.edgeshelf.permissions.PermissionSnapshot
import com.codex.edgeshelf.recording.RecordingEntry
import com.codex.edgeshelf.recording.RecordingDeleteConsentAction
import com.codex.edgeshelf.recording.RecordingDeleteResult
import com.codex.edgeshelf.recording.RecordingPlaybackController
import com.codex.edgeshelf.recording.RecordingPlaybackState
import com.codex.edgeshelf.recording.RecordingRepository
import com.codex.edgeshelf.recording.RecordingStateStore
import com.codex.edgeshelf.recording.RecordingService
import com.codex.edgeshelf.recording.isRecordingCaptureActive
import com.codex.edgeshelf.recording.removeRecordingEntry
import com.codex.edgeshelf.recording.shouldReleasePlaybackForDeletion
import com.codex.edgeshelf.service.EdgeShelfService
import com.codex.edgeshelf.screenshot.ScreenshotController
import com.codex.edgeshelf.screenshot.ScreenshotEntry
import com.codex.edgeshelf.screenshot.ScreenshotRepository
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppPickerPurpose(val maxSelection: Int?) {
    FAVORITES(null),
    PINNED(3),
}

data class AppPickerState(
    val isOpen: Boolean = false,
    val purpose: AppPickerPurpose = AppPickerPurpose.FAVORITES,
    val apps: List<LaunchableApp> = emptyList(),
    val selectedInstances: Set<AppInstanceKey> = emptySet(),
    val originalSelection: List<AppInstanceKey> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val returnToPreviousApp: Boolean = false,
)

data class EdgeShelfUiState(
    val settings: ShelfSettings = ShelfSettings(),
    val permissions: PermissionSnapshot = PermissionSnapshot(
        overlayGranted = false,
        notificationsGranted = false,
        usageAccessGranted = false,
        batteryOptimizationIgnored = false,
    ),
    val picker: AppPickerState = AppPickerState(),
    val recordingLibrary: RecordingLibraryUiState = RecordingLibraryUiState(),
    val screenshotLibrary: ScreenshotLibraryUiState = ScreenshotLibraryUiState(),
    val screenshotServiceConnected: Boolean = false,
    val screenshotSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
    val finishPickerHost: Boolean = false,
    val isLoading: Boolean = true,
)

data class RecordingLibraryUiState(
    val entries: List<RecordingEntry> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val playback: RecordingPlaybackState = RecordingPlaybackState(),
    val recordingActive: Boolean = false,
    val deletingId: String? = null,
    val deleteFailedId: String? = null,
    val deleteSuccessSerial: Long = 0L,
)

data class ScreenshotLibraryUiState(
    val entries: List<ScreenshotEntry> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val deletingId: String? = null,
    val deleteFailedId: String? = null,
    val deleteSuccessSerial: Long = 0L,
)

data class RecordingDeleteConsentRequest(
    val token: Long,
    val intentSender: IntentSender,
)

private data class PendingRecordingDeleteConsent(
    val entry: RecordingEntry,
    val actionAfterApproval: RecordingDeleteConsentAction,
)

class EdgeShelfViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val shelfStore = ShelfStore(app)
    private val appCatalogRepository = AppCatalogRepository(app)
    private val permissionCoordinator = PermissionCoordinator(app)
    private val permissions = MutableStateFlow(permissionCoordinator.snapshot())
    private val picker = MutableStateFlow(AppPickerState())
    private val finishPickerHost = MutableStateFlow(false)
    private val recordingRepository = RecordingRepository(app)
    private val recordingPlayback = RecordingPlaybackController(app)
    private val recordingEntries = MutableStateFlow<List<RecordingEntry>>(emptyList())
    private val recordingLoading = MutableStateFlow(true)
    private val recordingLoadFailed = MutableStateFlow(false)
    private val recordingActive = MutableStateFlow(false)
    private val recordingDeletingId = MutableStateFlow<String?>(null)
    private val recordingDeleteFailedId = MutableStateFlow<String?>(null)
    private val recordingDeleteSuccessSerial = MutableStateFlow(0L)
    private val screenshotRepository = ScreenshotRepository(app)
    private val screenshotEntries = MutableStateFlow<List<ScreenshotEntry>>(emptyList())
    private val screenshotLoading = MutableStateFlow(true)
    private val screenshotLoadFailed = MutableStateFlow(false)
    private val screenshotDeletingId = MutableStateFlow<String?>(null)
    private val screenshotDeleteFailedId = MutableStateFlow<String?>(null)
    private val screenshotDeleteSuccessSerial = MutableStateFlow(0L)
    private val mutableRecordingDeleteConsentRequest =
        MutableStateFlow<RecordingDeleteConsentRequest?>(null)
    private var catalogJob: Job? = null
    private var recordingsJob: Job? = null
    private var recordingDeleteJob: Job? = null
    private var screenshotsJob: Job? = null
    private var screenshotDeleteJob: Job? = null
    private var recordingsRefreshGeneration = 0L
    private var recordingDeleteConsentToken = 0L
    private var screenshotsRefreshGeneration = 0L
    private var pendingRecordingDeleteConsent: PendingRecordingDeleteConsent? = null

    val recordingDeleteConsentRequest: StateFlow<RecordingDeleteConsentRequest?> =
        mutableRecordingDeleteConsentRequest.asStateFlow()

    private val recordingLibrary = combine(
        recordingEntries,
        recordingLoading,
        recordingLoadFailed,
    ) { entries, loading, loadFailed ->
        RecordingLibraryUiState(
            entries = entries,
            isLoading = loading,
            loadFailed = loadFailed,
        )
    }.combine(recordingPlayback.state) { library, playback ->
        library.copy(playback = playback)
    }.combine(recordingActive) { library, active ->
        library.copy(recordingActive = active)
    }.combine(recordingDeletingId) { library, deletingId ->
        library.copy(deletingId = deletingId)
    }.combine(recordingDeleteFailedId) { library, deleteFailedId ->
        library.copy(deleteFailedId = deleteFailedId)
    }.combine(recordingDeleteSuccessSerial) { library, successSerial ->
        library.copy(deleteSuccessSerial = successSerial)
    }

    private val screenshotLibrary = combine(
        screenshotEntries,
        screenshotLoading,
        screenshotLoadFailed,
    ) { entries, loading, loadFailed ->
        ScreenshotLibraryUiState(
            entries = entries,
            isLoading = loading,
            loadFailed = loadFailed,
        )
    }.combine(screenshotDeletingId) { library, deletingId ->
        library.copy(deletingId = deletingId)
    }.combine(screenshotDeleteFailedId) { library, deleteFailedId ->
        library.copy(deleteFailedId = deleteFailedId)
    }.combine(screenshotDeleteSuccessSerial) { library, successSerial ->
        library.copy(deleteSuccessSerial = successSerial)
    }

    val uiState = combine(shelfStore.settings, permissions, picker) { settings, permissionSnapshot, pickerState ->
        EdgeShelfUiState(
            settings = settings,
            permissions = permissionSnapshot,
            picker = pickerState,
            isLoading = false,
        )
    }.combine(recordingLibrary) { state, library ->
        state.copy(recordingLibrary = library)
    }.combine(screenshotLibrary) { state, library ->
        state.copy(screenshotLibrary = library)
    }.combine(ScreenshotController.connected) { state, connected ->
        state.copy(screenshotServiceConnected = connected)
    }.combine(finishPickerHost) { state, shouldFinishPickerHost ->
        state.copy(finishPickerHost = shouldFinishPickerHost)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EdgeShelfUiState(permissions = permissions.value),
    )

    init {
        viewModelScope.launch {
            var wasRecording = false
            RecordingStateStore.state.collect { state ->
                val isRecording = isRecordingCaptureActive(state)
                recordingActive.value = isRecording
                if (isRecording) {
                    recordingPlayback.release()
                } else if (wasRecording) {
                    refreshRecordings()
                }
                wasRecording = isRecording
            }
        }
        viewModelScope.launch {
            var previousSerial = ScreenshotController.savedSerial.value
            ScreenshotController.savedSerial.collect { serial ->
                if (serial != previousSerial) refreshScreenshots()
                previousSerial = serial
            }
        }
    }

    fun refreshPermissions(): PermissionSnapshot = permissionCoordinator.snapshot().also {
        permissions.value = it
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled && !permissions.value.overlayGranted) return
        viewModelScope.launch {
            shelfStore.setEnabled(enabled)
            if (enabled) {
                EdgeShelfService.start(app)
            } else {
                stopService()
            }
        }
    }

    fun setSide(side: ShelfSide) {
        viewModelScope.launch { shelfStore.setSide(side) }
    }

    fun setMode(mode: ShelfMode) {
        viewModelScope.launch { shelfStore.setMode(mode) }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch { shelfStore.setAutoStart(enabled) }
    }

    fun setAutoHide(enabled: Boolean) {
        viewModelScope.launch { shelfStore.setAutoHide(enabled) }
    }

    fun setRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled && isRecordingCaptureActive(RecordingStateStore.state.value)) {
                RecordingService.stop(app)
            }
            shelfStore.setRecordingEnabled(enabled)
        }
    }

    fun clearRecents() {
        viewModelScope.launch { shelfStore.clearRecents() }
    }

    fun refreshRecordings() {
        if (recordingDeletingId.value != null) return
        val generation = ++recordingsRefreshGeneration
        recordingsJob?.cancel()
        recordingsJob = viewModelScope.launch {
            recordingLoading.value = true
            try {
                val entries = withContext(Dispatchers.IO) {
                    recordingRepository.loadRecordings()
                }
                if (generation == recordingsRefreshGeneration) {
                    recordingEntries.value = entries
                    recordingLoadFailed.value = false
                    if (recordingPlayback.state.value.activeId != null &&
                        entries.none { it.stableId == recordingPlayback.state.value.activeId }
                    ) {
                        recordingPlayback.release()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == recordingsRefreshGeneration) {
                    recordingLoadFailed.value = true
                }
            } finally {
                if (generation == recordingsRefreshGeneration) {
                    recordingLoading.value = false
                }
            }
        }
    }

    fun refreshScreenshots() {
        if (screenshotDeletingId.value != null) return
        val generation = ++screenshotsRefreshGeneration
        screenshotsJob?.cancel()
        screenshotsJob = viewModelScope.launch {
            screenshotLoading.value = true
            try {
                val entries = withContext(Dispatchers.IO) {
                    screenshotRepository.loadScreenshots()
                }
                if (generation == screenshotsRefreshGeneration) {
                    screenshotEntries.value = entries
                    screenshotLoadFailed.value = false
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == screenshotsRefreshGeneration) {
                    screenshotLoadFailed.value = true
                }
            } finally {
                if (generation == screenshotsRefreshGeneration) {
                    screenshotLoading.value = false
                }
            }
        }
    }

    fun deleteScreenshot(screenshotId: String) {
        if (screenshotDeletingId.value != null) return
        val entry = screenshotEntries.value.firstOrNull { it.stableId == screenshotId } ?: return
        ++screenshotsRefreshGeneration
        screenshotsJob?.cancel()
        screenshotLoading.value = false
        screenshotDeleteFailedId.value = null
        screenshotDeletingId.value = entry.stableId
        screenshotDeleteJob?.cancel()
        screenshotDeleteJob = viewModelScope.launch {
            val deleted = try {
                withContext(Dispatchers.IO) { screenshotRepository.deleteScreenshot(entry) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
            if (deleted) {
                screenshotEntries.value = screenshotEntries.value.filterNot {
                    it.stableId == entry.stableId
                }
                screenshotDeleteFailedId.value = null
                screenshotDeleteSuccessSerial.value += 1L
            } else {
                screenshotDeleteFailedId.value = entry.stableId
            }
            screenshotDeletingId.value = null
            refreshScreenshots()
        }
    }

    fun clearScreenshotDeleteError() {
        screenshotDeleteFailedId.value = null
    }

    fun toggleRecordingPlayback(recordingId: String) {
        if (recordingActive.value || isRecordingCaptureActive(RecordingStateStore.state.value)) return
        recordingEntries.value
            .firstOrNull { it.stableId == recordingId }
            ?.let(recordingPlayback::toggle)
    }

    fun deleteRecording(recordingId: String) {
        if (recordingDeletingId.value != null || pendingRecordingDeleteConsent != null) return
        val entry = recordingEntries.value.firstOrNull { it.stableId == recordingId } ?: return
        if (shouldReleasePlaybackForDeletion(
                activeId = recordingPlayback.state.value.activeId,
                deletingId = entry.stableId,
            )
        ) {
            recordingPlayback.release()
        }

        ++recordingsRefreshGeneration
        recordingsJob?.cancel()
        recordingLoading.value = false
        recordingDeleteFailedId.value = null
        recordingDeletingId.value = entry.stableId
        performRecordingDelete(entry = entry, allowConsent = true)
    }

    fun consumeRecordingDeleteConsentRequest(token: Long) {
        if (mutableRecordingDeleteConsentRequest.value?.token == token) {
            mutableRecordingDeleteConsentRequest.value = null
        }
    }

    fun onRecordingDeleteConsentResult(approved: Boolean) {
        val pending = pendingRecordingDeleteConsent ?: return
        pendingRecordingDeleteConsent = null
        mutableRecordingDeleteConsentRequest.value = null
        if (!approved) {
            finishRecordingDeletionFailure(entry = pending.entry, showError = false)
            return
        }
        when (pending.actionAfterApproval) {
            RecordingDeleteConsentAction.RETRY_DELETE ->
                performRecordingDelete(entry = pending.entry, allowConsent = false)

            RecordingDeleteConsentAction.REFRESH_ONLY ->
                finishRecordingDeletionSuccess(pending.entry)
        }
    }

    private fun performRecordingDelete(entry: RecordingEntry, allowConsent: Boolean) {
        recordingDeleteJob?.cancel()
        recordingDeleteJob = viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) {
                    recordingRepository.deleteRecording(entry)
                }) {
                    RecordingDeleteResult.Deleted -> finishRecordingDeletionSuccess(entry)
                    is RecordingDeleteResult.ConsentRequired -> {
                        if (!allowConsent) {
                            finishRecordingDeletionFailure(entry = entry, showError = true)
                            return@launch
                        }
                        pendingRecordingDeleteConsent = PendingRecordingDeleteConsent(
                            entry = entry,
                            actionAfterApproval = result.actionAfterApproval,
                        )
                        mutableRecordingDeleteConsentRequest.value =
                            RecordingDeleteConsentRequest(
                                token = ++recordingDeleteConsentToken,
                                intentSender = result.intentSender,
                            )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                finishRecordingDeletionFailure(entry = entry, showError = true)
            }
        }
    }

    private fun finishRecordingDeletionSuccess(entry: RecordingEntry) {
        if (recordingDeletingId.value != entry.stableId) return
        recordingEntries.value = removeRecordingEntry(
            entries = recordingEntries.value,
            stableId = entry.stableId,
            stableIdOf = RecordingEntry::stableId,
        )
        recordingDeleteFailedId.value = null
        recordingDeletingId.value = null
        pendingRecordingDeleteConsent = null
        mutableRecordingDeleteConsentRequest.value = null
        recordingDeleteSuccessSerial.value += 1L
        refreshRecordings()
    }

    private fun finishRecordingDeletionFailure(entry: RecordingEntry, showError: Boolean) {
        if (recordingDeletingId.value != entry.stableId) return
        recordingDeletingId.value = null
        pendingRecordingDeleteConsent = null
        mutableRecordingDeleteConsentRequest.value = null
        recordingDeleteFailedId.value = entry.stableId.takeIf { showError }
        refreshRecordings()
    }

    fun clearRecordingDeleteError() {
        recordingDeleteFailedId.value = null
    }

    fun stopRecordingPlayback() {
        recordingPlayback.release()
    }

    fun openAppPicker(
        returnToPreviousApp: Boolean = false,
        purpose: AppPickerPurpose = AppPickerPurpose.FAVORITES,
    ) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val settings = shelfStore.settings.first()
            val originalSelection = when (purpose) {
                AppPickerPurpose.FAVORITES -> settings.favorites
                AppPickerPurpose.PINNED -> settings.pinnedApps
            }
            picker.value = AppPickerState(
                isOpen = true,
                purpose = purpose,
                selectedInstances = originalSelection.toSet(),
                originalSelection = originalSelection,
                isLoading = true,
                returnToPreviousApp = returnToPreviousApp,
            )
            val catalogResult = try {
                Result.success(
                    withContext(Dispatchers.IO) { appCatalogRepository.loadCatalog() },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            val current = picker.value
            if (!current.isOpen) return@launch
            val catalog = catalogResult.getOrNull()
            val loadedApps = catalog?.apps.orEmpty()
            val availableKeys = loadedApps.map(LaunchableApp::key)
            val reboundSelection = if (catalog == null) {
                originalSelection
            } else {
                originalSelection.map { stored ->
                    rebindAppInstanceKey(
                        stored = stored,
                        available = availableKeys,
                        currentUserSerial = catalog.currentUserSerial,
                    ) ?: stored
                }.distinct()
            }
            picker.value = current.copy(
                apps = loadedApps,
                selectedInstances = reboundSelection.toSet(),
                originalSelection = reboundSelection,
                isLoading = false,
                loadFailed = catalogResult.isFailure || loadedApps.isEmpty(),
            )
        }
    }

    fun retryAppCatalog() {
        val current = picker.value
        if (!current.isOpen) return
        openAppPicker(
            returnToPreviousApp = current.returnToPreviousApp,
            purpose = current.purpose,
        )
    }

    fun togglePickerApp(instanceKey: AppInstanceKey) {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.isSaving) return
        val selected = togglePickerSelection(
            selected = current.selectedInstances,
            instanceKey = instanceKey,
            maximum = current.purpose.maxSelection,
        )
        picker.value = current.copy(selectedInstances = selected, saveFailed = false)
    }

    fun saveAppPicker() {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.loadFailed || current.isSaving) return
        picker.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val selection = mergeFavoriteSelection(
                existing = current.originalSelection,
                catalogOrder = current.apps.map(LaunchableApp::key),
                selected = current.selectedInstances,
            ).let { merged ->
                current.purpose.maxSelection?.let(merged::take) ?: merged
            }
            try {
                when (current.purpose) {
                    AppPickerPurpose.FAVORITES -> shelfStore.setFavorites(selection)
                    AppPickerPurpose.PINNED -> shelfStore.setPinnedApps(selection)
                }
                picker.value = AppPickerState()
                if (current.returnToPreviousApp) finishPickerHost.value = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                picker.value = current.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    fun dismissAppPicker(): Boolean {
        val current = picker.value
        if (current.isSaving) return false
        val shouldReturn = current.returnToPreviousApp
        catalogJob?.cancel()
        picker.value = AppPickerState()
        return shouldReturn
    }

    fun consumeFinishPickerHost() {
        finishPickerHost.value = false
    }

    fun syncService() {
        viewModelScope.launch {
            val settings = shelfStore.settings.first()
            if (settings.enabled && permissions.value.overlayGranted) {
                EdgeShelfService.refresh(app)
            } else {
                stopService()
            }
        }
    }

    private fun stopService() {
        EdgeShelfService.stop(app)
    }

    override fun onCleared() {
        recordingsJob?.cancel()
        recordingDeleteJob?.cancel()
        screenshotsJob?.cancel()
        screenshotDeleteJob?.cancel()
        recordingPlayback.release()
        super.onCleared()
    }
}

internal fun mergeFavoriteSelection(
    existing: List<AppInstanceKey>,
    catalogOrder: List<AppInstanceKey>,
    selected: Set<AppInstanceKey>,
): List<AppInstanceKey> {
    return buildList {
        addAll(existing.filter(selected::contains))
        addAll(catalogOrder.filter { key -> key in selected && key !in existing })
    }.distinct()
}

internal fun togglePickerSelection(
    selected: Set<AppInstanceKey>,
    instanceKey: AppInstanceKey,
    maximum: Int?,
): Set<AppInstanceKey> = selected.toMutableSet().apply {
    if (instanceKey in this) {
        remove(instanceKey)
    } else if (maximum == null || size < maximum.coerceAtLeast(0)) {
        add(instanceKey)
    }
}
