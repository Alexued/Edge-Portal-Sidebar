package com.codex.edgeshelf.ui

import android.app.Application
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
import com.codex.edgeshelf.recording.RecordingPlaybackController
import com.codex.edgeshelf.recording.RecordingPlaybackState
import com.codex.edgeshelf.recording.RecordingRepository
import com.codex.edgeshelf.recording.RecordingStateStore
import com.codex.edgeshelf.recording.isRecordingCaptureActive
import com.codex.edgeshelf.service.EdgeShelfService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppPickerState(
    val isOpen: Boolean = false,
    val apps: List<LaunchableApp> = emptyList(),
    val selectedInstances: Set<AppInstanceKey> = emptySet(),
    val originalFavorites: List<AppInstanceKey> = emptyList(),
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
    val finishPickerHost: Boolean = false,
    val isLoading: Boolean = true,
)

data class RecordingLibraryUiState(
    val entries: List<RecordingEntry> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val playback: RecordingPlaybackState = RecordingPlaybackState(),
    val recordingActive: Boolean = false,
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
    private var catalogJob: Job? = null
    private var recordingsJob: Job? = null
    private var recordingsRefreshGeneration = 0L

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

    fun clearRecents() {
        viewModelScope.launch { shelfStore.clearRecents() }
    }

    fun refreshRecordings() {
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

    fun toggleRecordingPlayback(recordingId: String) {
        if (recordingActive.value || isRecordingCaptureActive(RecordingStateStore.state.value)) return
        recordingEntries.value
            .firstOrNull { it.stableId == recordingId }
            ?.let(recordingPlayback::toggle)
    }

    fun stopRecordingPlayback() {
        recordingPlayback.release()
    }

    fun openAppPicker(returnToPreviousApp: Boolean = false) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val favorites = shelfStore.settings.first().favorites
            picker.value = AppPickerState(
                isOpen = true,
                selectedInstances = favorites.toSet(),
                originalFavorites = favorites,
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
            val reboundFavorites = if (catalog == null) {
                favorites
            } else {
                favorites.map { stored ->
                    rebindAppInstanceKey(
                        stored = stored,
                        available = availableKeys,
                        currentUserSerial = catalog.currentUserSerial,
                    ) ?: stored
                }.distinct()
            }
            picker.value = current.copy(
                apps = loadedApps,
                selectedInstances = reboundFavorites.toSet(),
                originalFavorites = reboundFavorites,
                isLoading = false,
                loadFailed = catalogResult.isFailure || loadedApps.isEmpty(),
            )
        }
    }

    fun retryAppCatalog() {
        val current = picker.value
        if (!current.isOpen) return
        openAppPicker(current.returnToPreviousApp)
    }

    fun togglePickerApp(instanceKey: AppInstanceKey) {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.isSaving) return
        val selected = current.selectedInstances.toMutableSet().apply {
            if (!add(instanceKey)) remove(instanceKey)
        }
        picker.value = current.copy(selectedInstances = selected, saveFailed = false)
    }

    fun saveAppPicker() {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.loadFailed || current.isSaving) return
        picker.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val favorites = mergeFavoriteSelection(
                existing = current.originalFavorites,
                catalogOrder = current.apps.map(LaunchableApp::key),
                selected = current.selectedInstances,
            )
            try {
                shelfStore.setFavorites(favorites)
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
