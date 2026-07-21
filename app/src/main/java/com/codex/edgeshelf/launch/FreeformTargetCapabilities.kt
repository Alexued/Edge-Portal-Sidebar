package com.codex.edgeshelf.launch

import android.content.pm.ActivityInfo
import android.os.Build

internal enum class FreeformResizeCapability {
    RESIZABLE,
    PORTRAIT_ONLY,
    LANDSCAPE_ONLY,
    PRESERVE_ORIENTATION,
    NOT_RESIZABLE,
    UNKNOWN,
}

internal fun resolveFreeformResizeCapability(
    resizeMode: Int?,
    targetSdkVersion: Int?,
): FreeformResizeCapability = when {
    resizeMode in NON_RESIZEABLE_MODES -> FreeformResizeCapability.NOT_RESIZABLE
    resizeMode in RESIZEABLE_MODES -> when (resizeMode) {
        RESIZE_MODE_FORCE_RESIZEABLE_LANDSCAPE_ONLY -> FreeformResizeCapability.LANDSCAPE_ONLY
        RESIZE_MODE_FORCE_RESIZEABLE_PORTRAIT_ONLY -> FreeformResizeCapability.PORTRAIT_ONLY
        RESIZE_MODE_FORCE_RESIZEABLE_PRESERVE_ORIENTATION ->
            FreeformResizeCapability.PRESERVE_ORIENTATION
        else -> FreeformResizeCapability.RESIZABLE
    }
    resizeMode != null -> FreeformResizeCapability.UNKNOWN
    targetSdkVersion != null && targetSdkVersion < Build.VERSION_CODES.N ->
        FreeformResizeCapability.NOT_RESIZABLE
    else -> FreeformResizeCapability.UNKNOWN
}

internal fun ActivityInfo.freeformResizeCapability(): FreeformResizeCapability {
    val resizeMode = runCatching {
        ActivityInfo::class.java
            .getDeclaredField("resizeMode")
            .apply { isAccessible = true }
            .getInt(this)
    }.getOrNull()

    return resolveFreeformResizeCapability(
        resizeMode = resizeMode,
        targetSdkVersion = applicationInfo?.targetSdkVersion,
    )
}

private const val RESIZE_MODE_UNRESIZEABLE = 0
private const val RESIZE_MODE_FORCE_RESIZEABLE = 4
private const val RESIZE_MODE_FORCE_RESIZEABLE_LANDSCAPE_ONLY = 5
private const val RESIZE_MODE_FORCE_RESIZEABLE_PORTRAIT_ONLY = 6
private const val RESIZE_MODE_FORCE_RESIZEABLE_PRESERVE_ORIENTATION = 7
private val NON_RESIZEABLE_MODES = setOf(
    RESIZE_MODE_UNRESIZEABLE,
)
private val RESIZEABLE_MODES = setOf(1, 2, RESIZE_MODE_FORCE_RESIZEABLE, 5, 6, 7)
