package com.codex.edgeshelf.overlay

import android.content.Context
import android.os.Build
import android.provider.Settings

internal const val GESTURE_SAFE_SYSTEM_INSET_DP = 20f
internal const val GESTURE_SAFE_MAXIMUM_SYSTEM_INSET_DP = 20f

internal fun gestureSafeMinimumEdgeDistanceDp(context: Context): Float =
    if (usesAffectedVendorGestureNavigation(context)) GESTURE_SAFE_SYSTEM_INSET_DP else 0f

internal fun usesAffectedVendorGestureNavigation(context: Context): Boolean {
    val vendor = listOf(Build.MANUFACTURER, Build.BRAND)
        .any { value ->
            value.equals("xiaomi", ignoreCase = true) ||
                value.equals("redmi", ignoreCase = true) ||
                value.equals("poco", ignoreCase = true)
        }
    if (!vendor) return false
    val navigationMode = runCatching {
        Settings.Secure.getInt(context.contentResolver, "navigation_mode", 0)
    }.getOrDefault(0)
    val fullScreenGestures = runCatching {
        Settings.Global.getInt(context.contentResolver, "force_fsg_nav_bar", 0)
    }.getOrDefault(0)
    return navigationMode == 2 || fullScreenGestures == 1
}
