package com.codex.edgeshelf.launch

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.codex.edgeshelf.data.LEGACY_USER_SERIAL
import com.codex.edgeshelf.data.LaunchableApp
import kotlinx.coroutines.CancellationException

/** Launches an app that belongs to a profile other than the process user. */
class ProfileAppLauncher(
    private val isCurrentUser: (LaunchableApp) -> Boolean,
    private val freeformStarter: (LaunchableApp, Rect) -> Unit,
    private val normalStarter: (LaunchableApp) -> Unit,
    private val xSpaceFallback: (LaunchableApp, Rect) -> Boolean,
) {
    fun launch(app: LaunchableApp, bounds: Rect): Boolean {
        if (isCurrentUser(app)) return false

        if (attempt { freeformStarter(app, Rect(bounds)) }) return true
        if (attempt { normalStarter(app) }) return true
        return attemptBoolean { xSpaceFallback(app, Rect(bounds)) }
    }

    private inline fun attempt(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Log.d(TAG, "Profile launch attempt unavailable", error)
        false
    }

    private inline fun attemptBoolean(block: () -> Boolean): Boolean = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: RuntimeException) {
        Log.d(TAG, "Profile compatibility launch unavailable", error)
        false
    }

    companion object {
        private const val TAG = "EdgeShelfProfileLaunch"

        fun create(context: Context): ProfileAppLauncher {
            val userManager = context.getSystemService(UserManager::class.java)
            val currentSerial = runCatching {
                userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: LEGACY_USER_SERIAL
            }.getOrDefault(LEGACY_USER_SERIAL)
            return create(context, currentSerial)
        }

        fun create(
            context: Context,
            currentUserSerial: Long,
        ): ProfileAppLauncher {
            val appContext = context.applicationContext
            val launcherApps = appContext.getSystemService(LauncherApps::class.java)
            val xSpaceAdapter = XiaomiXSpaceLaunchAdapter(appContext)
            fun requireAccessibleProfile(app: LaunchableApp, userHandle: UserHandle) {
                val service = checkNotNull(launcherApps) { "LauncherApps service is unavailable" }
                check(userHandle in service.profiles) { "Target profile is no longer accessible" }
                check(service.isActivityEnabled(app.componentName, userHandle)) {
                    "Target launcher activity is disabled"
                }
            }
            return ProfileAppLauncher(
                isCurrentUser = { app ->
                    if (currentUserSerial >= 0L) {
                        app.key.userSerial == currentUserSerial
                    } else {
                        app.userHandle != null && app.userHandle == Process.myUserHandle()
                    }
                },
                freeformStarter = { app, bounds ->
                    val userHandle = checkNotNull(app.userHandle) { "Profile user is unavailable" }
                    requireAccessibleProfile(app, userHandle)
                    checkNotNull(launcherApps).startMainActivity(
                        app.componentName,
                        userHandle,
                        Rect(bounds),
                        FreeformLaunchOptions.create(bounds),
                    )
                },
                normalStarter = { app ->
                    val userHandle = checkNotNull(app.userHandle) { "Profile user is unavailable" }
                    requireAccessibleProfile(app, userHandle)
                    checkNotNull(launcherApps).startMainActivity(
                        app.componentName,
                        userHandle,
                        null,
                        null,
                    )
                },
                xSpaceFallback = xSpaceAdapter::launch,
            )
        }
    }
}

/**
 * Best-effort compatibility path for HyperOS builds that reject LauncherApps across XSpace.
 * These extras are isolated here because they are OEM-specific and may stop working after an
 * update. Clone launches use it only after public LauncherApps attempts; owner launches may use
 * it first when HyperOS would otherwise show its profile chooser for a package with a clone.
 */
internal class XiaomiXSpaceLaunchAdapter(
    private val context: Context,
    private val manufacturer: () -> String = { Build.MANUFACTURER.orEmpty() },
    private val currentUser: () -> UserHandle = { Process.myUserHandle() },
    // UserHandle has no public identifier API; Android's hashCode is its runtime user id.
    private val userIdentifier: (UserHandle) -> Int = { user -> user.hashCode() },
) {
    fun launchCurrentUser(intent: Intent, bounds: Rect): Boolean {
        val component = intent.component ?: return false
        val currentUser = currentUser()
        val spec = xSpaceUserSelectionSpec(
            manufacturer = manufacturer(),
            targetUserIdentifier = userIdentifier(currentUser),
        ) ?: return false
        return launchIntent(intent, component, bounds, spec)
    }

    fun launch(app: LaunchableApp, bounds: Rect): Boolean {
        val userHandle = app.userHandle ?: return false
        val spec = xSpaceLaunchSpec(
            manufacturer = manufacturer(),
            targetUserIdentifier = userIdentifier(userHandle),
            isCurrentUser = userHandle == currentUser(),
        ) ?: return false

        return launchIntent(app.launchIntent, app.componentName, bounds, spec)
    }

    private fun launchIntent(
        target: Intent,
        componentName: android.content.ComponentName,
        bounds: Rect,
        spec: XiaomiXSpaceLaunchSpec,
    ): Boolean {
        val intent = Intent(target).apply {
            component = componentName
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra(spec.cachedUidKey, spec.targetUserIdentifier)
            putExtra(spec.userSelectedKey, true)
        }
        context.startActivity(intent, FreeformLaunchOptions.create(bounds))
        return true
    }
}

internal data class XiaomiXSpaceLaunchSpec(
    val targetUserIdentifier: Int,
    val cachedUidKey: String,
    val userSelectedKey: String,
)

internal fun xSpaceLaunchSpec(
    manufacturer: String,
    targetUserIdentifier: Int,
    isCurrentUser: Boolean,
): XiaomiXSpaceLaunchSpec? {
    if (isCurrentUser) return null
    return xSpaceUserSelectionSpec(manufacturer, targetUserIdentifier)
}

internal fun xSpaceUserSelectionSpec(
    manufacturer: String,
    targetUserIdentifier: Int,
): XiaomiXSpaceLaunchSpec? {
    if (!manufacturer.equals("Xiaomi", ignoreCase = true)) return null
    return XiaomiXSpaceLaunchSpec(
        targetUserIdentifier = targetUserIdentifier,
        cachedUidKey = "android.intent.extra.xspace_cached_uid",
        userSelectedKey = "android.intent.extra.xspace_userid_selected",
    )
}
