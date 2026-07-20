package com.codex.edgeshelf.permissions

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.codex.edgeshelf.data.UsageRepository

class PermissionCoordinator(private val context: Context) {
    private val appContext = context.applicationContext

    fun snapshot(): PermissionSnapshot {
        val power = appContext.getSystemService(PowerManager::class.java)
        return PermissionSnapshot(
            overlayGranted = Settings.canDrawOverlays(appContext),
            notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            usageAccessGranted = UsageRepository(appContext).canReadUsageStats(),
            batteryOptimizationIgnored = power?.isIgnoringBatteryOptimizations(appContext.packageName) == true,
        )
    }

    fun overlayIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${appContext.packageName}"),
    )

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun batteryOptimizationIntent(): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${appContext.packageName}"),
    )

    fun notificationPermission(): String? =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
}
