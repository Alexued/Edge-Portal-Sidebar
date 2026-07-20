package com.codex.edgeshelf.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeformLaunchOptionsTest {
    @Test
    fun requestsAospFreeformWindowingMode() {
        val launchSpec = FreeformLaunchOptions.spec(
            FreeformWindowBounds(left = 108, top = 187, right = 972, bottom = 1918),
        )

        assertEquals("android.activity.windowingMode", launchSpec.windowingModeKey)
        assertEquals(5, launchSpec.windowingMode)
        assertEquals("key_miui_config_flag", launchSpec.miuiConfigFlagKey)
        assertEquals(3, launchSpec.miuiConfigFlag)
    }

    @Test
    fun preservesRequestedLaunchBounds() {
        val bounds = FreeformWindowBounds(left = 108, top = 187, right = 972, bottom = 1918)

        assertEquals(bounds, FreeformLaunchOptions.spec(bounds).bounds)
    }
}
