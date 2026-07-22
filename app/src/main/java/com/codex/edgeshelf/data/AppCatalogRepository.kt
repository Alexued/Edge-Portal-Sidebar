package com.codex.edgeshelf.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

data class LaunchableApp(
    val key: AppInstanceKey,
    val label: String,
    val icon: Drawable?,
    val componentName: ComponentName,
    val userHandle: UserHandle?,
    val launchIntent: Intent,
) {
    val packageName: String
        get() = key.packageName

    constructor(
        packageName: String,
        label: String,
        icon: Drawable?,
        launchIntent: Intent,
    ) : this(
        key = AppInstanceKey(
            packageName = packageName,
            userSerial = LEGACY_USER_SERIAL,
            componentName = launchIntent.component?.flattenToString().orEmpty(),
        ),
        label = label,
        icon = icon,
        componentName = launchIntent.component ?: ComponentName(packageName, ""),
        userHandle = null,
        launchIntent = launchIntent,
    )
}

data class AppCatalogSnapshot(
    val apps: List<LaunchableApp>,
    val currentUserSerial: Long,
)

internal fun interface AppCatalogSource {
    fun load(): AppCatalogSnapshot
}

class AppCatalogRepository {
    private val source: AppCatalogSource

    constructor(context: Context) {
        source = AndroidProfileCatalogSource(context.applicationContext)
    }

    internal constructor(source: AppCatalogSource) {
        this.source = source
    }

    fun loadCatalog(): AppCatalogSnapshot = source.load()

    fun loadLaunchableApps(): List<LaunchableApp> = loadCatalog().apps
}

private class AndroidProfileCatalogSource(
    private val context: Context,
) : AppCatalogSource {
    private val packageManager: PackageManager = context.packageManager
    private val userManager: UserManager = checkNotNull(context.getSystemService(UserManager::class.java)) {
        "UserManager service is unavailable"
    }

    override fun load(): AppCatalogSnapshot {
        val currentUser = Process.myUserHandle()
        val currentUserSerial = requireSerial(currentUser)
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val apps = if (launcherApps == null) {
            loadCurrentUserFallback(currentUser, currentUserSerial)
        } else {
            loadProfiles(launcherApps, currentUser, currentUserSerial)
        }
        return AppCatalogSnapshot(apps = apps, currentUserSerial = currentUserSerial)
    }

    private fun loadProfiles(
        launcherApps: LauncherApps,
        currentUser: UserHandle,
        currentUserSerial: Long,
    ): List<LaunchableApp> {
        val profiles = buildList {
            add(currentUser)
            launcherApps.profiles.forEach { profile ->
                if (profile != currentUser) add(profile)
            }
        }
        val candidates = profiles.flatMap { profile ->
            val serial = if (profile == currentUser) currentUserSerial else serialOrNull(profile)
                ?: return@flatMap emptyList()
            val activities = runCatching {
                launcherApps.getActivityList(null, profile)
            }.getOrDefault(emptyList())
            activities.mapNotNull { info -> toCandidate(info, profile, serial) }
        }
        val selected = selectCandidates(candidates)
        if (selected.any { app -> app.key.userSerial == currentUserSerial }) return selected

        return (selected + loadCurrentUserFallback(currentUser, currentUserSerial))
            .distinctBy { app -> app.key.packageName to app.key.userSerial }
            .sortedWith(launchableAppComparator())
    }

    private fun toCandidate(
        info: LauncherActivityInfo,
        profile: UserHandle,
        userSerial: Long,
    ): ProfileCandidate? {
        val component = info.componentName
        val packageName = component.packageName.trim()
        if (packageName.isEmpty() || packageName == context.packageName) return null
        val applicationInfo = info.applicationInfo
        if (!applicationInfo.enabled || (applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) {
            return null
        }
        val label = runCatching { info.label.toString().trim() }
            .getOrDefault("")
            .ifEmpty { packageName }
        return ProfileCandidate(
            descriptor = LaunchableInstanceDescriptor(
                packageName = packageName,
                userSerial = userSerial,
                componentName = component.flattenToString(),
                label = label,
            ),
            info = info,
            profile = profile,
        )
    }

    private fun selectCandidates(candidates: List<ProfileCandidate>): List<LaunchableApp> {
        val byDescriptor = candidates.associateBy(ProfileCandidate::descriptor)
        return normalizeLaunchableInstances(
            descriptors = candidates.map(ProfileCandidate::descriptor),
            selfPackage = context.packageName,
        ).mapNotNull { descriptor ->
            val candidate = byDescriptor[descriptor] ?: return@mapNotNull null
            val component = candidate.info.componentName
            val launchIntent = launcherIntent(component)
            LaunchableApp(
                key = AppInstanceKey(
                    packageName = descriptor.packageName,
                    userSerial = descriptor.userSerial,
                    componentName = descriptor.componentName,
                ),
                label = descriptor.label,
                icon = runCatching {
                    candidate.info.getBadgedIcon(context.resources.displayMetrics.densityDpi)
                }.getOrNull(),
                componentName = component,
                userHandle = candidate.profile,
                launchIntent = launchIntent,
            )
        }
    }

    private fun loadCurrentUserFallback(
        currentUser: UserHandle,
        currentUserSerial: Long,
    ): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName.trim()
                if (packageName.isEmpty() || packageName == context.packageName) return@mapNotNull null
                val applicationInfo = activityInfo.applicationInfo
                if (!applicationInfo.enabled ||
                    (applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0
                ) {
                    return@mapNotNull null
                }
                val component = ComponentName(packageName, activityInfo.name)
                FallbackCandidate(
                    descriptor = LaunchableInstanceDescriptor(
                        packageName = packageName,
                        userSerial = currentUserSerial,
                        componentName = component.flattenToString(),
                        label = resolveInfo.loadLabel(packageManager).toString().trim().ifEmpty { packageName },
                    ),
                    icon = runCatching { resolveInfo.loadIcon(packageManager) }.getOrNull(),
                    component = component,
                )
            }
        val byDescriptor = candidates.associateBy(FallbackCandidate::descriptor)
        return normalizeLaunchableInstances(
            descriptors = candidates.map(FallbackCandidate::descriptor),
            selfPackage = context.packageName,
        ).mapNotNull { descriptor ->
            val candidate = byDescriptor[descriptor] ?: return@mapNotNull null
            LaunchableApp(
                key = AppInstanceKey(
                    packageName = descriptor.packageName,
                    userSerial = descriptor.userSerial,
                    componentName = descriptor.componentName,
                ),
                label = descriptor.label,
                icon = candidate.icon,
                componentName = candidate.component,
                userHandle = currentUser,
                launchIntent = launcherIntent(candidate.component),
            )
        }
    }

    private fun launcherIntent(component: ComponentName): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun launchableAppComparator(): Comparator<LaunchableApp> =
        compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { app -> app.label }
            .thenBy(LaunchableApp::packageName)
            .thenBy { app -> app.key.userSerial }
            .thenBy { app -> app.key.componentName }

    private fun requireSerial(user: UserHandle): Long = userManager.getSerialNumberForUser(user)
        .takeIf { it >= 0L }
        ?: error("No stable serial is available for the current user")

    private fun serialOrNull(user: UserHandle): Long? = runCatching {
        userManager.getSerialNumberForUser(user)
    }.getOrNull()?.takeIf { it >= 0L }
}

private data class ProfileCandidate(
    val descriptor: LaunchableInstanceDescriptor,
    val info: LauncherActivityInfo,
    val profile: UserHandle,
)

private data class FallbackCandidate(
    val descriptor: LaunchableInstanceDescriptor,
    val icon: Drawable?,
    val component: ComponentName,
)

internal data class LaunchableInstanceDescriptor(
    val packageName: String,
    val userSerial: Long,
    val componentName: String,
    val label: String,
)

private const val SYNTHETIC_APP_DETAILS_ACTIVITY = "android.app.AppDetailsActivity"

internal fun normalizeLaunchableInstances(
    descriptors: Iterable<LaunchableInstanceDescriptor>,
    selfPackage: String,
): List<LaunchableInstanceDescriptor> {
    val normalized = descriptors.asSequence()
        .map { descriptor ->
            descriptor.copy(
                packageName = descriptor.packageName.trim(),
                componentName = descriptor.componentName.trim(),
                label = descriptor.label.trim(),
            )
        }
        .filter { descriptor ->
            descriptor.packageName.isNotEmpty() &&
            descriptor.packageName != selfPackage &&
            descriptor.userSerial >= 0L &&
            descriptor.componentName.isNotEmpty() &&
            descriptor.componentName.substringAfterLast('/') != SYNTHETIC_APP_DETAILS_ACTIVITY
        }
        .map { descriptor ->
            if (descriptor.label.isEmpty()) descriptor.copy(label = descriptor.packageName) else descriptor
        }
        .toList()

    return normalized
        .sortedWith(
            compareBy<LaunchableInstanceDescriptor>(LaunchableInstanceDescriptor::packageName)
                .thenBy(LaunchableInstanceDescriptor::userSerial)
                .thenBy(LaunchableInstanceDescriptor::componentName)
                .thenBy(LaunchableInstanceDescriptor::label),
        )
        .distinctBy { descriptor -> descriptor.packageName to descriptor.userSerial }
        .sortedWith(
            compareBy<LaunchableInstanceDescriptor, String>(String.CASE_INSENSITIVE_ORDER) {
                it.label
            }
                .thenBy(LaunchableInstanceDescriptor::packageName)
                .thenBy(LaunchableInstanceDescriptor::userSerial)
                .thenBy(LaunchableInstanceDescriptor::componentName),
        )
}

internal fun normalizeLaunchablePackages(
    packages: Iterable<Pair<String, String>>,
    selfPackage: String,
): List<Pair<String, String>> = packages
    .asSequence()
    .map { (packageName, label) -> packageName.trim() to label.trim() }
    .filter { (packageName, label) -> packageName.isNotEmpty() && packageName != selfPackage && label.isNotEmpty() }
    .distinctBy { it.first }
    .sortedWith(compareBy<Pair<String, String>> { it.second.lowercase() }.thenBy { it.first })
    .toList()
