package com.codex.edgeshelf.service

import android.Manifest
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codex.edgeshelf.MainActivity
import com.codex.edgeshelf.R
import com.codex.edgeshelf.data.AppCatalogRepository
import com.codex.edgeshelf.data.EdgeDistancePreview
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfMode
import com.codex.edgeshelf.data.ShelfSettings
import com.codex.edgeshelf.data.ShelfStore
import com.codex.edgeshelf.data.UsageRepository
import com.codex.edgeshelf.data.resolveShelfContent
import com.codex.edgeshelf.launch.LaunchCoordinator
import com.codex.edgeshelf.launch.LaunchProxyActivity
import com.codex.edgeshelf.launch.ProfileAppLauncher
import com.codex.edgeshelf.launch.FreeformLaunchOptions
import com.codex.edgeshelf.launch.FreeformWindowBounds
import com.codex.edgeshelf.launch.FreeformResizeCapability
import com.codex.edgeshelf.launch.XiaomiXSpaceLaunchAdapter
import com.codex.edgeshelf.launch.freeformResizeCapability
import com.codex.edgeshelf.launch.isLargeScreenWorkArea
import com.codex.edgeshelf.launch.resolveFreeformContentOrientation
import com.codex.edgeshelf.launch.responsiveFreeformBounds
import com.codex.edgeshelf.overlay.EdgeRailView
import com.codex.edgeshelf.overlay.RailWindowGeometry
import com.codex.edgeshelf.overlay.RailMotion
import com.codex.edgeshelf.overlay.buildRailRows
import com.codex.edgeshelf.recording.RecordingAction
import com.codex.edgeshelf.recording.RecordingLaunchActivity
import com.codex.edgeshelf.recording.RecordingService
import com.codex.edgeshelf.recording.RecordingStateStore
import com.codex.edgeshelf.recording.recordingActionFor
import com.codex.edgeshelf.screenshot.ScreenshotCaptureResult
import com.codex.edgeshelf.screenshot.ScreenshotController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EdgeShelfService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var shelfStore: ShelfStore
    private lateinit var appCatalogRepository: AppCatalogRepository
    private lateinit var usageRepository: UsageRepository
    private lateinit var launchCoordinator: LaunchCoordinator
    private lateinit var xSpaceLaunchAdapter: XiaomiXSpaceLaunchAdapter
    private var windowManager: WindowManager? = null
    private var railView: EdgeRailView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var pendingGeometry: RailWindowGeometry? = null
    private var latestSettings: ShelfSettings? = null
    private var settingsJob: Job? = null
    private var recordingStateJob: Job? = null
    private var launchJob: Job? = null
    private var contentRefreshJob: Job? = null
    private var screenshotInProgress = false
    private var pendingScreenshotCapture: Runnable? = null
    private var screenshotTimeout: Runnable? = null
    private var contentGeneration = 0L
    private var hasLoadedShelfContent = false
    private var contentRefreshNeedsRetry = false
    private val contentRefreshGate = ContentRefreshGate(CONTENT_REFRESH_INTERVAL_MS)
    private var screenReceiverRegistered = false
    private var screenInteractive = true
    private var deviceLocked = false
    private var attachFailureReported = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT,
                Intent.ACTION_USER_UNLOCKED,
                -> refreshSystemVisibility()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        shelfStore = ShelfStore(applicationContext)
        appCatalogRepository = AppCatalogRepository(applicationContext)
        usageRepository = UsageRepository(applicationContext)
        xSpaceLaunchAdapter = XiaomiXSpaceLaunchAdapter(applicationContext)
        val profileAppLauncher = ProfileAppLauncher.create(applicationContext)
        launchCoordinator = LaunchCoordinator(
            collapse = { railView?.collapse(preservePendingLaunch = true) },
            recordRecent = { instanceKey -> shelfStore.recordRecent(instanceKey) },
            freeformAttempts = listOf(
                // HyperOS otherwise shows an owner/clone chooser even with an explicit user.
                ::tryXiaomiOwnerFreeform,
                ::tryLauncherAppsFreeform,
                ::tryFreeformLaunch,
                ::tryLauncherAppsNormal,
            ),
            normalStarter = ::startActivity,
            isCrossProfile = { app ->
                app.userHandle != null && app.userHandle != Process.myUserHandle()
            },
            profileLaunchAttempt = { app ->
                profileAppLauncher.launch(app, freeformBounds(app.launchIntent))
            },
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        registerScreenStateReceiver()
        refreshSystemVisibility()
        observeRecordingState()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ServiceActions.ACTION_STOP -> {
                removeRail()
                stopSelf()
                return START_NOT_STICKY
            }

            ServiceActions.ACTION_REFRESH -> refreshRail()
            ServiceActions.ACTION_START, null -> reconcileRail()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        settingsJob?.cancel()
        recordingStateJob?.cancel()
        launchJob?.cancel()
        contentRefreshJob?.cancel()
        pendingScreenshotCapture?.let(mainHandler::removeCallbacks)
        screenshotTimeout?.let(mainHandler::removeCallbacks)
        pendingScreenshotCapture = null
        screenshotTimeout = null
        screenshotInProgress = false
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenStateReceiver) }
            screenReceiverRegistered = false
        }
        removeRail()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val view = railView
        if (view == null) {
            reconcileRail()
            return
        }
        view.post {
            val settings = latestSettings ?: return@post
            view.collapse(immediate = true)
            updateWindowGeometry(initialGeometry(settings))
            view.updateSettings(settings)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeSettings() {
        settingsJob?.cancel()
        settingsJob = scope.launch {
            combine(
                shelfStore.settings,
                EdgeDistancePreview.distanceDp,
            ) { settings, previewDistanceDp ->
                previewDistanceDp?.let { settings.copy(edgeDistanceDp = it) } ?: settings
            }.collectLatest { settings ->
                val previousSettings = latestSettings
                latestSettings = settings
                reconcileRail()
                if (settings.affectsShelfContent(previousSettings)) {
                    if (previousSettings?.mode != settings.mode) {
                        contentRefreshGate.reset()
                        hasLoadedShelfContent = false
                        contentRefreshNeedsRetry = false
                    }
                    refreshShelfContent(force = true)
                }
            }
        }
    }

    private fun observeRecordingState() {
        recordingStateJob?.cancel()
        recordingStateJob = scope.launch {
            RecordingStateStore.state.collectLatest { state ->
                railView?.updateRecordingState(state)
            }
        }
    }

    private fun refreshRail() {
        attachFailureReported = false
        latestSettings?.let { settings -> railView?.updateSettings(settings) }
        reconcileRail()
        refreshShelfContent(force = true)
    }

    private fun reconcileRail() {
        val settings = latestSettings ?: return
        if (!settings.enabled || !Settings.canDrawOverlays(this)) {
            removeRail()
            stopSelf()
            return
        }

        val systemHidden = !screenInteractive || deviceLocked
        if (!systemHidden) ensureRail(settings)
        railView?.apply {
            updateSettings(settings)
            setSystemHidden(systemHidden)
        }
    }

    private fun ensureRail(settings: ShelfSettings) {
        if (railView != null || !Settings.canDrawOverlays(this)) return
        val manager = getSystemService(WindowManager::class.java) ?: return
        val view = EdgeRailView(
            context = this,
            onLaunch = ::launchApp,
            onAddApp = ::openAppPicker,
            onToggleRecording = ::toggleRecording,
            onTakeScreenshot = ::takeScreenshot,
            onOpenRecentSettings = ::openRecentSettings,
            onRefreshRequested = { refreshShelfContent(force = false) },
            onVerticalFractionChanged = { fraction ->
                scope.launch { shelfStore.setVerticalFraction(fraction) }
            },
            onWindowGeometryChanged = ::updateWindowGeometry,
        ).apply {
            updateSettings(settings)
            updateRecordingState(RecordingStateStore.state.value)
        }
        val initialGeometry = pendingGeometry ?: initialGeometry(settings)
        val params = WindowManager.LayoutParams(
            initialGeometry.widthPx,
            initialGeometry.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or sideGravity(initialGeometry.side)
            x = initialGeometry.edgeOffsetPx
            y = initialGeometry.yPx
            windowAnimations = android.R.style.Animation_Toast
            title = getString(R.string.app_name)
        }

        runCatching { manager.addView(view, params) }
            .onSuccess {
                windowManager = manager
                railView = view
                windowParams = params
                pendingGeometry = null
                attachFailureReported = false
                view.post {
                    view.updateSettings(settings)
                    if (!hasLoadedShelfContent && contentRefreshJob?.isActive != true) {
                        refreshShelfContent(force = true)
                    }
                }
            }
            .onFailure { error ->
                Log.w(TAG, "Unable to attach edge shelf overlay", error)
                if (!attachFailureReported) {
                    Toast.makeText(this, getString(R.string.overlay_unavailable), Toast.LENGTH_SHORT).show()
                    attachFailureReported = true
                }
                removeRail()
                stopSelf()
            }
    }

    private fun removeRail() {
        val view = railView
        railView = null
        val manager = windowManager
        windowManager = null
        windowParams = null
        pendingGeometry = null
        contentRefreshJob?.cancel()
        contentRefreshJob = null
        contentGeneration += 1L
        hasLoadedShelfContent = false
        contentRefreshNeedsRetry = false
        contentRefreshGate.reset()
        if (view != null && manager != null) {
            runCatching { manager.removeViewImmediate(view) }
                .onFailure { error -> Log.d(TAG, "Overlay was already detached", error) }
        }
    }

    private fun updateWindowGeometry(geometry: RailWindowGeometry) {
        pendingGeometry = geometry
        val manager = windowManager ?: return
        val view = railView ?: return
        val params = windowParams ?: return
        val gravity = Gravity.TOP or sideGravity(geometry.side)
        if (
            params.width == geometry.widthPx &&
            params.height == geometry.heightPx &&
            params.y == geometry.yPx &&
            params.x == geometry.edgeOffsetPx &&
            params.gravity == gravity
        ) {
            return
        }
        params.width = geometry.widthPx
        params.height = geometry.heightPx
        params.y = geometry.yPx
        params.x = geometry.edgeOffsetPx
        params.gravity = gravity
        runCatching { manager.updateViewLayout(view, params) }
            .onFailure { error -> Log.d(TAG, "Unable to update edge shelf bounds", error) }
    }

    private fun initialGeometry(settings: ShelfSettings): RailWindowGeometry {
        val density = resources.displayMetrics.density
        val width = (28f * density).toInt().coerceAtLeast(1)
        val height = (116f * density).toInt().coerceAtLeast(1)
        val screenHeight = displayHeightPx()
        val (topInset, bottomInset) = systemBarInsets()
        val y = com.codex.edgeshelf.overlay.verticalTop(
            verticalFraction = settings.verticalFraction,
            screenHeight = screenHeight,
            railHeight = height,
            topInset = topInset,
            bottomInset = bottomInset,
        )
        return RailWindowGeometry(width, height, y, settings.side)
    }

    private fun systemBarInsets(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = getSystemService(WindowManager::class.java)
                ?.currentWindowMetrics
                ?.windowInsets
                ?.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
            if (insets != null) return insets.top to insets.bottom
        }
        return systemDimension("status_bar_height") to systemDimension("navigation_bar_height")
    }

    private fun systemDimension(name: String): Int {
        val identifier = resources.getIdentifier(name, "dimen", "android")
        return if (identifier == 0) 0 else resources.getDimensionPixelSize(identifier)
    }

    private fun displayHeightPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        getSystemService(WindowManager::class.java)
            ?.currentWindowMetrics
            ?.bounds
            ?.height()
            ?: resources.displayMetrics.heightPixels
    } else {
        resources.displayMetrics.heightPixels
    }

    private fun sideGravity(side: com.codex.edgeshelf.data.ShelfSide): Int =
        if (side == com.codex.edgeshelf.data.ShelfSide.RIGHT) Gravity.END else Gravity.START

    private fun launchApp(app: LaunchableApp) {
        // The view starts its exit before dispatching this callback. Cancelling here makes the
        // newest tap win without restarting that exit animation.
        launchJob?.cancel()
        launchJob = scope.launch {
            if (!launchCoordinator.launch(app)) {
                Log.w(TAG, "Unable to launch ${app.key.stableId}")
            }
        }
    }

    private fun refreshShelfContent(force: Boolean) {
        val settings = latestSettings ?: return
        if (railView == null) return
        if (!contentRefreshGate.shouldRefresh(
                nowMs = SystemClock.elapsedRealtime(),
                force = force,
            )
        ) {
            return
        }

        val generation = ++contentGeneration
        contentRefreshJob?.cancel()
        contentRefreshJob = scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val catalog = appCatalogRepository.loadCatalog()
                    Log.d(
                        TAG,
                        "Catalog loaded: apps=${catalog.apps.size}, profileSerials=" +
                            catalog.apps.map { app -> app.key.userSerial }.distinct().sorted(),
                    )
                    val systemRecents = if (settings.mode == ShelfMode.RECENT) {
                        usageRepository.loadRecentPackages(limit = RECENT_QUERY_CANDIDATE_LIMIT)
                    } else {
                        emptyList()
                    }
                    resolveShelfContent(
                        mode = settings.mode,
                        favorites = settings.favorites,
                        systemRecents = systemRecents,
                        localRecents = settings.recents,
                        catalog = catalog.apps,
                        currentUserSerial = catalog.currentUserSerial,
                        recentLimit = RECENT_APP_LIMIT,
                        pinnedApps = settings.pinnedApps,
                    )
                }
            }

            if (generation != contentGeneration || latestSettings?.mode != settings.mode) return@launch
            result.onSuccess { content ->
                Log.d(
                    TAG,
                    "Shelf content: recent=${content.recentApps.size}, all=${content.allApps.size}, " +
                        "fixed=${content.fixedApps.size}",
                )
                contentRefreshGate.markSucceeded(SystemClock.elapsedRealtime())
                hasLoadedShelfContent = true
                contentRefreshNeedsRetry = false
                railView?.updateDisplayRows(
                    buildRailRows(
                        mode = settings.mode,
                        recentApps = content.recentApps,
                        allApps = content.allApps,
                        fixedApps = content.fixedApps,
                        contentLoaded = true,
                        allAppsSectionTitle = getString(R.string.all_apps_section),
                    ),
                )
                railView?.updatePinnedApps(content.pinnedApps)
            }.onFailure { error ->
                if (error is CancellationException) return@onFailure
                contentRefreshGate.markFailed()
                contentRefreshNeedsRetry = true
                Log.w(TAG, "Unable to resolve shelf content", error)
                if (!hasLoadedShelfContent) {
                    railView?.updatePinnedApps(emptyList())
                    railView?.updateDisplayRows(
                        buildRailRows(
                            mode = settings.mode,
                            contentLoaded = true,
                            allAppsSectionTitle = getString(R.string.all_apps_section),
                        ),
                    )
                }
            }
        }
    }

    private fun openAppPicker() {
        railView?.collapse(immediate = true)
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_APP_PICKER)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        runCatching { startActivity(intent) }
            .onFailure { error -> Log.w(TAG, "Unable to open app picker", error) }
    }

    private fun toggleRecording() {
        when (recordingActionFor(RecordingStateStore.state.value)) {
            RecordingAction.STOP -> RecordingService.stop(this)
            RecordingAction.START -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    runCatching {
                        startActivity(RecordingLaunchActivity.createIntent(this))
                    }.onFailure { error ->
                        Log.w(TAG, "Unable to open recording host", error)
                        Toast.makeText(
                            this,
                            getString(R.string.recording_start_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    railView?.collapse(immediate = true)
                    val intent = Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_REQUEST_RECORDING_PERMISSION)
                        .addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )
                    runCatching { startActivity(intent) }
                        .onFailure { error ->
                            Log.w(TAG, "Unable to open recording permission flow", error)
                            Toast.makeText(
                                this,
                                getString(R.string.recording_permission_required),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
            }
            null -> Unit
        }
    }

    private fun takeScreenshot() {
        if (screenshotInProgress) {
            Toast.makeText(this, getString(R.string.screenshot_busy), Toast.LENGTH_SHORT).show()
            return
        }
        screenshotInProgress = true
        railView?.collapse()
        val capture = Runnable {
            pendingScreenshotCapture = null
            setRailCaptureHidden(true)
            mainHandler.postDelayed(
                {
                    ScreenshotController.capture(::handleScreenshotResult)
                },
                SCREENSHOT_HIDE_FRAME_DELAY_MS,
            )
        }
        pendingScreenshotCapture = capture
        mainHandler.postDelayed(capture, RailMotion.COLLAPSE_DURATION_MS)
        val timeout = Runnable {
            if (screenshotInProgress) {
                handleScreenshotResult(ScreenshotCaptureResult.Failed())
            }
        }
        screenshotTimeout = timeout
        mainHandler.postDelayed(timeout, SCREENSHOT_TIMEOUT_MS)
    }

    private fun handleScreenshotResult(result: ScreenshotCaptureResult) {
        mainHandler.post {
            if (!screenshotInProgress) return@post
            screenshotInProgress = false
            pendingScreenshotCapture?.let(mainHandler::removeCallbacks)
            screenshotTimeout?.let(mainHandler::removeCallbacks)
            pendingScreenshotCapture = null
            screenshotTimeout = null
            setRailCaptureHidden(false)
            val message = when (result) {
                is ScreenshotCaptureResult.Saved -> R.string.screenshot_saved
                ScreenshotCaptureResult.Busy -> R.string.screenshot_busy
                ScreenshotCaptureResult.Unsupported -> R.string.screenshot_unsupported
                ScreenshotCaptureResult.ServiceUnavailable -> R.string.screenshot_permission_required
                is ScreenshotCaptureResult.Failed -> R.string.screenshot_failed
            }
            Toast.makeText(this, getString(message), Toast.LENGTH_SHORT).show()
            if (result == ScreenshotCaptureResult.ServiceUnavailable) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { error ->
                    Log.w(TAG, "Unable to open screenshot accessibility settings", error)
                }
            }
        }
    }

    private fun setRailCaptureHidden(hidden: Boolean) {
        railView?.setCaptureHidden(hidden)
        val manager = windowManager ?: return
        val view = railView ?: return
        val params = windowParams ?: return
        val targetAlpha = if (hidden) 0f else 1f
        if (params.alpha == targetAlpha) return
        params.alpha = targetAlpha
        runCatching { manager.updateViewLayout(view, params) }
            .onFailure { error -> Log.d(TAG, "Unable to update screenshot rail alpha", error) }
    }

    private fun openRecentSettings() {
        railView?.collapse(immediate = true)
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_RECENT_SETTINGS)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        runCatching { startActivity(intent) }
            .onFailure { error -> Log.w(TAG, "Unable to open recent mode settings", error) }
    }

    private fun tryFreeformLaunch(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return runCatching {
            val freeformIntent = Intent(intent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            startActivity(
                LaunchProxyActivity.createIntent(
                    context = this,
                    target = freeformIntent,
                    bounds = freeformBounds(freeformIntent),
                ),
            )
            true
        }.onFailure { error ->
            Log.d(TAG, "Freeform launch unavailable; using normal launch", error)
        }.getOrDefault(false)
    }

    /**
     * Explicitly targets the owner profile before using the legacy proxy. HyperOS can otherwise
     * turn a package-shared owner/clone intent into a profile chooser, even when the component
     * itself is explicit.
     */
    private fun tryXiaomiOwnerFreeform(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val packageName = intent.component?.packageName ?: return false
        val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java) ?: return false
        val currentUser = Process.myUserHandle()
        val hasOtherProfileInstance = runCatching {
            launcherApps.profiles
                .asSequence()
                .filter { profile ->
                    profile != currentUser && xSpaceLaunchAdapter.isXSpaceProfile(profile)
                }
                .any { profile -> launcherApps.getActivityList(packageName, profile).isNotEmpty() }
        }.getOrDefault(false)
        if (!hasOtherProfileInstance) return false

        return runCatching {
            xSpaceLaunchAdapter.launchCurrentUser(intent, freeformBounds(intent))
        }.onFailure { error ->
            Log.d(TAG, "XSpace owner selection unavailable; trying public API", error)
        }.getOrDefault(false)
    }

    private fun tryLauncherAppsFreeform(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val component = intent.component ?: return false
        val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java) ?: return false
        val user = Process.myUserHandle()
        return runCatching {
            if (user !in launcherApps.profiles || !launcherApps.isActivityEnabled(component, user)) {
                return@runCatching false
            }
            val bounds = freeformBounds(intent)
            launcherApps.startMainActivity(
                component,
                user,
                Rect(bounds),
                FreeformLaunchOptions.create(bounds),
            )
            true
        }.onFailure { error ->
            Log.d(TAG, "LauncherApps owner freeform unavailable; trying proxy", error)
        }.getOrDefault(false)
    }

    private fun tryLauncherAppsNormal(intent: Intent): Boolean {
        val component = intent.component ?: return false
        val launcherApps = getSystemService(android.content.pm.LauncherApps::class.java) ?: return false
        val user = Process.myUserHandle()
        return runCatching {
            if (user !in launcherApps.profiles || !launcherApps.isActivityEnabled(component, user)) {
                return@runCatching false
            }
            launcherApps.startMainActivity(component, user, null, null)
            true
        }.onFailure { error ->
            Log.d(TAG, "LauncherApps owner normal launch unavailable", error)
        }.getOrDefault(false)
    }

    private fun freeformBounds(intent: Intent): Rect {
        val available = availableWindowBounds()
        val targetInfo = targetActivityInfo(intent)
        val resizeCapability = targetInfo?.freeformResizeCapability()
            ?: FreeformResizeCapability.UNKNOWN
        val workArea = FreeformWindowBounds(
            left = available.left,
            top = available.top,
            right = available.right,
            bottom = available.bottom,
        )
        // A proxy Activity briefly gives this process a phone-sized window configuration.
        // Classify the device from the display work area so later launches remain tablet-sized.
        val isLargeScreen = isLargeScreenWorkArea(
            availableBounds = workArea,
            density = resources.displayMetrics.density,
        )
        val calculated = responsiveFreeformBounds(
            availableBounds = workArea,
            contentOrientation = resolveFreeformContentOrientation(
                requestedOrientation = targetInfo?.screenOrientation,
                isLargeScreen = isLargeScreen,
                resizeCapability = resizeCapability,
                displayIsPortrait = available.height() >= available.width(),
            ),
            isLargeScreen = isLargeScreen,
        )
        Log.d(
            TAG,
            "Freeform bounds for ${intent.component}: ${calculated.left},${calculated.top}-" +
                "${calculated.right},${calculated.bottom}, orientation=" +
                "${targetInfo?.screenOrientation}, resizable=" +
                resizeCapability,
        )
        return Rect(calculated.left, calculated.top, calculated.right, calculated.bottom)
    }

    private fun availableWindowBounds(): Rect {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = getSystemService(WindowManager::class.java)?.currentWindowMetrics
            if (metrics != null) {
                val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                val available = Rect(
                    metrics.bounds.left + insets.left,
                    metrics.bounds.top + insets.top,
                    metrics.bounds.right - insets.right,
                    metrics.bounds.bottom - insets.bottom,
                )
                if (available.width() > 0 && available.height() > 0) return available
            }
        }
        val (topInset, bottomInset) = systemBarInsets()
        val height = resources.displayMetrics.heightPixels
        return Rect(
            0,
            topInset,
            resources.displayMetrics.widthPixels,
            (height - bottomInset).coerceAtLeast(topInset + 1),
        )
    }

    private fun targetActivityInfo(intent: Intent): ActivityInfo? {
        val component = intent.component ?: intent.resolveActivity(packageManager)
        return component?.let(::resolveActivityInfo)
    }

    private fun resolveActivityInfo(
        component: ComponentName,
        depth: Int = 0,
    ): ActivityInfo? {
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getActivityInfo(
                    component,
                    PackageManager.ComponentInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getActivityInfo(component, 0)
            }
        }.getOrNull() ?: return null
        val targetActivity = info.targetActivity
        if (targetActivity.isNullOrBlank() || depth >= MAX_TARGET_ACTIVITY_ALIAS_DEPTH) {
            return info
        }
        val targetClassName = if (targetActivity.startsWith('.')) {
            info.packageName + targetActivity
        } else {
            targetActivity
        }
        return resolveActivityInfo(
            component = ComponentName(info.packageName, targetClassName),
            depth = depth + 1,
        ) ?: info
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_USER_UNLOCKED)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenReceiverRegistered = true
    }

    private fun refreshSystemVisibility() {
        screenInteractive = getSystemService(PowerManager::class.java)?.isInteractive ?: true
        deviceLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: false
        reconcileRail()
    }

    private fun notification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_notification_title))
            .setContentText(getString(R.string.service_notification))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        private const val TAG = "EdgeShelfService"
        private const val CHANNEL_ID = "edge_shelf_service"
        private const val NOTIFICATION_ID = 1001
        private const val CONTENT_REFRESH_INTERVAL_MS = 3_000L
        private const val RECENT_QUERY_CANDIDATE_LIMIT = 80
        private const val RECENT_APP_LIMIT = 40
        private const val MAX_TARGET_ACTIVITY_ALIAS_DEPTH = 2
        private const val SCREENSHOT_HIDE_FRAME_DELAY_MS = 64L
        private const val SCREENSHOT_TIMEOUT_MS = 8_000L

        fun start(context: Context) {
            val intent = Intent(context, EdgeShelfService::class.java)
                .setAction(ServiceActions.ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun refresh(context: Context) {
            val intent = Intent(context, EdgeShelfService::class.java)
                .setAction(ServiceActions.ACTION_REFRESH)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EdgeShelfService::class.java))
        }
    }
}

private fun ShelfSettings.affectsShelfContent(previous: ShelfSettings?): Boolean = when {
    previous == null -> true
    pinnedApps != previous.pinnedApps -> true
    mode != previous.mode -> true
    mode == ShelfMode.FIXED -> favorites != previous.favorites
    else -> recents != previous.recents
}
