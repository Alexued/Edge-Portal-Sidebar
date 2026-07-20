package com.codex.edgeshelf.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val launchIntent: Intent,
)

class AppCatalogRepository(
    private val context: Context,
) {
    private val packageManager: PackageManager = context.packageManager

    fun loadLaunchableApps(): List<LaunchableApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == context.packageName) return@mapNotNull null
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: return@mapNotNull null
                val applicationInfo = resolveInfo.activityInfo.applicationInfo
                if ((applicationInfo.flags and ApplicationInfo.FLAG_INSTALLED) == 0) {
                    return@mapNotNull null
                }
                LaunchableApp(
                    packageName = packageName,
                    label = resolveInfo.loadLabel(packageManager).toString().trim()
                        .ifEmpty { packageName },
                    icon = resolveInfo.loadIcon(packageManager),
                    launchIntent = launchIntent,
                )
            }
            .distinctBy(LaunchableApp::packageName)
            .sortedWith(compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy(LaunchableApp::packageName))
            .toList()
    }
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
