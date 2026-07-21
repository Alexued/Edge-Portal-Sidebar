package com.codex.edgeshelf.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeformTargetCapabilitiesTest {
    @Test
    fun unresizableModeIsRejected() {
        assertEquals(
            FreeformResizeCapability.NOT_RESIZABLE,
            resolveFreeformResizeCapability(resizeMode = 0, targetSdkVersion = 34),
        )
    }

    @Test
    fun forceResizableModeIsAccepted() {
        assertEquals(
            FreeformResizeCapability.RESIZABLE,
            resolveFreeformResizeCapability(resizeMode = 4, targetSdkVersion = 34),
        )
    }

    @Test
    fun landscapeOnlyModeCannotUseNarrowWindow() {
        assertEquals(
            FreeformResizeCapability.LANDSCAPE_ONLY,
            resolveFreeformResizeCapability(resizeMode = 5, targetSdkVersion = 34),
        )
    }

    @Test
    fun portraitOnlyModeCanUseNarrowWindow() {
        assertEquals(
            FreeformResizeCapability.PORTRAIT_ONLY,
            resolveFreeformResizeCapability(resizeMode = 6, targetSdkVersion = 34),
        )
    }

    @Test
    fun preserveOrientationModeIsReportedSeparately() {
        assertEquals(
            FreeformResizeCapability.PRESERVE_ORIENTATION,
            resolveFreeformResizeCapability(resizeMode = 7, targetSdkVersion = 34),
        )
    }

    @Test
    fun knownResizableModesAreAccepted() {
        listOf(1, 2, 4).forEach { resizeMode ->
            assertEquals(
                FreeformResizeCapability.RESIZABLE,
                resolveFreeformResizeCapability(resizeMode, targetSdkVersion = 34),
            )
        }
    }

    @Test
    fun removedPlatformModeDoesNotClaimSupport() {
        assertEquals(
            FreeformResizeCapability.UNKNOWN,
            resolveFreeformResizeCapability(resizeMode = 3, targetSdkVersion = 34),
        )
    }

    @Test
    fun unknownModeDoesNotClaimSupport() {
        assertEquals(
            FreeformResizeCapability.UNKNOWN,
            resolveFreeformResizeCapability(resizeMode = 99, targetSdkVersion = 34),
        )
    }

    @Test
    fun oldTargetWithoutResizeMetadataIsRejected() {
        assertEquals(
            FreeformResizeCapability.NOT_RESIZABLE,
            resolveFreeformResizeCapability(resizeMode = null, targetSdkVersion = 23),
        )
    }

    @Test
    fun modernTargetWithoutResizeMetadataRemainsUnknown() {
        assertEquals(
            FreeformResizeCapability.UNKNOWN,
            resolveFreeformResizeCapability(resizeMode = null, targetSdkVersion = 34),
        )
    }
}
