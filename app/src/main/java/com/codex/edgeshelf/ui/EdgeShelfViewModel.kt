package com.codex.edgeshelf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codex.edgeshelf.data.AppCatalogRepository
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfSettings
import com.codex.edgeshelf.data.ShelfMode
import com.codex.edgeshelf.data.ShelfSide
import com.codex.edgeshelf.data.ShelfStore
import com.codex.edgeshelf.permissions.PermissionCoordinator
import com.codex.edgeshelf.permissions.PermissionSnapshot
import com.codex.edgeshelf.service.EdgeShelfService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    val selectedPackages: Set<String> = emptySet(),
    val originalFavorites: List<String> = emptyList(),
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
    val finishPickerHost: Boolean = false,
    val isLoading: Boolean = true,
)

class EdgeShelfViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.applicationContext
    private val shelfStore = ShelfStore(app)
    private val appCatalogRepository = AppCatalogRepository(app)
    private val permissionCoordinator = PermissionCoordinator(app)
    private val permissions = MutableStateFlow(permissionCoordinator.snapshot())
    private val picker = MutableStateFlow(AppPickerState())
    private val finishPickerHost = MutableStateFlow(false)
    private var catalogJob: Job? = null

    val uiState = combine(shelfStore.settings, permissions, picker) { settings, permissionSnapshot, pickerState ->
        EdgeShelfUiState(
            settings = settings,
            permissions = permissionSnapshot,
            picker = pickerState,
            isLoading = false,
        )
    }.combine(finishPickerHost) { state, shouldFinishPickerHost ->
        state.copy(finishPickerHost = shouldFinishPickerHost)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EdgeShelfUiState(permissions = permissions.value),
    )

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

    fun openAppPicker(returnToPreviousApp: Boolean = false) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val favorites = shelfStore.settings.first().favorites
            picker.value = AppPickerState(
                isOpen = true,
                selectedPackages = favorites.toSet(),
                originalFavorites = favorites,
                isLoading = true,
                returnToPreviousApp = returnToPreviousApp,
            )
            val apps = runCatching {
                withContext(Dispatchers.IO) { appCatalogRepository.loadLaunchableApps() }
            }
            val current = picker.value
            if (!current.isOpen) return@launch
            val loadedApps = apps.getOrDefault(emptyList())
            picker.value = current.copy(
                apps = loadedApps,
                isLoading = false,
                loadFailed = apps.isFailure || loadedApps.isEmpty(),
            )
        }
    }

    fun retryAppCatalog() {
        val current = picker.value
        if (!current.isOpen) return
        openAppPicker(current.returnToPreviousApp)
    }

    fun togglePickerApp(packageName: String) {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.isSaving) return
        val selected = current.selectedPackages.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
        picker.value = current.copy(selectedPackages = selected, saveFailed = false)
    }

    fun saveAppPicker() {
        val current = picker.value
        if (!current.isOpen || current.isLoading || current.loadFailed || current.isSaving) return
        picker.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            val favorites = mergeFavoriteSelection(
                existing = current.originalFavorites,
                catalogOrder = current.apps.map(LaunchableApp::packageName),
                selected = current.selectedPackages,
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
}

internal fun mergeFavoriteSelection(
    existing: List<String>,
    catalogOrder: List<String>,
    selected: Set<String>,
): List<String> {
    return buildList {
        addAll(existing.filter(selected::contains))
        addAll(catalogOrder.filter { packageName -> packageName in selected && packageName !in existing })
    }.distinct()
}
