package com.codex.edgeshelf.launch

import android.app.ActivityOptions
import android.graphics.Rect
import android.os.Bundle

data class FreeformWindowBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class FreeformLaunchSpec(
    val bounds: FreeformWindowBounds,
    val windowingModeKey: String,
    val windowingMode: Int,
    val miuiConfigFlagKey: String,
    val miuiConfigFlag: Int,
)

object FreeformLaunchOptions {
    const val WINDOWING_MODE_KEY = "android.activity.windowingMode"
    const val WINDOWING_MODE_FREEFORM = 5
    const val MIUI_CONFIG_FLAG_KEY = "key_miui_config_flag"
    const val MIUI_CONFIG_FLAG_FREEFORM = 3

    fun spec(bounds: FreeformWindowBounds): FreeformLaunchSpec =
        FreeformLaunchSpec(
            bounds = bounds,
            windowingModeKey = WINDOWING_MODE_KEY,
            windowingMode = WINDOWING_MODE_FREEFORM,
            miuiConfigFlagKey = MIUI_CONFIG_FLAG_KEY,
            miuiConfigFlag = MIUI_CONFIG_FLAG_FREEFORM,
        )

    fun create(bounds: Rect): Bundle {
        val launchSpec = spec(
            FreeformWindowBounds(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
            ),
        )
        return ActivityOptions.makeBasic()
            .setLaunchBounds(
                Rect(
                    launchSpec.bounds.left,
                    launchSpec.bounds.top,
                    launchSpec.bounds.right,
                    launchSpec.bounds.bottom,
                ),
            )
            .toBundle()
            .apply {
                putInt(launchSpec.windowingModeKey, launchSpec.windowingMode)
                putInt(launchSpec.miuiConfigFlagKey, launchSpec.miuiConfigFlag)
            }
    }
}
