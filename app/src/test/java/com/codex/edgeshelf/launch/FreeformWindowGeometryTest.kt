package com.codex.edgeshelf.launch

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeformWindowGeometryTest {
    @Test
    fun tabletAdaptiveOrientationPrefersNarrowWindowInLandscapeDisplay() {
        val orientation = resolveFreeformContentOrientation(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            isLargeScreen = true,
            resizeCapability = FreeformResizeCapability.RESIZABLE,
            displayIsPortrait = false,
        )

        assertEquals(FreeformContentOrientation.PORTRAIT, orientation)
    }

    @Test
    fun tabletAdaptiveOrientationPrefersNarrowWindowInPortraitDisplay() {
        val orientation = resolveFreeformContentOrientation(
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            isLargeScreen = true,
            resizeCapability = FreeformResizeCapability.RESIZABLE,
            displayIsPortrait = true,
        )

        assertEquals(FreeformContentOrientation.PORTRAIT, orientation)
    }

    @Test
    fun resizableTabletExplicitOrientationOverridesAdaptivePreference() {
        assertEquals(
            FreeformContentOrientation.PORTRAIT,
            resolveFreeformContentOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,
                isLargeScreen = true,
                resizeCapability = FreeformResizeCapability.RESIZABLE,
            ),
        )
        assertEquals(
            FreeformContentOrientation.LANDSCAPE,
            resolveFreeformContentOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
                isLargeScreen = true,
                resizeCapability = FreeformResizeCapability.RESIZABLE,
            ),
        )
    }

    @Test
    fun nonResizableTabletActivityFallsBackToWideWindow() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.NOT_RESIZABLE,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun explicitPortraitCannotOverrideNonResizableTabletActivity() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                resizeCapability = FreeformResizeCapability.NOT_RESIZABLE,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun landscapeOnlyTabletActivityFallsBackToWideWindow() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.LANDSCAPE_ONLY,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun portraitOnlyTabletActivityPrefersNarrowWindow() {
        assertEquals(
            FreeformWindowShape.NARROW,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.PORTRAIT_ONLY,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun resizableTabletActivityPrefersNarrowWindow() {
        assertEquals(
            FreeformWindowShape.NARROW,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.RESIZABLE,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun explicitLandscapeAlwaysUsesWideWindow() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                resizeCapability = FreeformResizeCapability.RESIZABLE,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun unknownTabletCapabilityFallsBackToWideWindow() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.UNKNOWN,
                isLargeScreen = true,
            ),
        )
    }

    @Test
    fun preserveOrientationUsesCurrentTabletOrientation() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.PRESERVE_ORIENTATION,
                isLargeScreen = true,
                displayIsPortrait = false,
            ),
        )
        assertEquals(
            FreeformWindowShape.NARROW,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                resizeCapability = FreeformResizeCapability.PRESERVE_ORIENTATION,
                isLargeScreen = true,
                displayIsPortrait = true,
            ),
        )
    }

    @Test
    fun phoneAdaptiveOrientationKeepsPortraitFallback() {
        assertEquals(
            FreeformContentOrientation.PORTRAIT,
            resolveFreeformContentOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                isLargeScreen = false,
            ),
        )
    }

    @Test
    fun phoneAdaptiveOrientationIgnoresTabletResizeCapability() {
        FreeformResizeCapability.values().forEach { resizeCapability ->
            assertEquals(
                FreeformWindowShape.NARROW,
                resolveFreeformWindowShape(
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    resizeCapability = resizeCapability,
                    isLargeScreen = false,
                ),
            )
        }
    }

    @Test
    fun phoneExplicitLandscapeStillUsesWideWindow() {
        assertEquals(
            FreeformWindowShape.WIDE,
            resolveFreeformWindowShape(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                resizeCapability = FreeformResizeCapability.RESIZABLE,
                isLargeScreen = false,
            ),
        )
    }

    @Test
    fun phonePortraitUsesNativeFiveByEightTaskRatio() {
        val bounds = responsiveFreeformBounds(
            availableBounds = FreeformWindowBounds(0, 0, 1080, 2340),
            contentOrientation = FreeformContentOrientation.PORTRAIT,
            isLargeScreen = false,
        )

        assertEquals(1080, bounds.width)
        assertEquals(1728, bounds.height)
        assertCentered(bounds, FreeformWindowBounds(0, 0, 1080, 2340))
    }

    @Test
    fun tabletLandscapeKeepsPortraitAppInsideComfortableWorkArea() {
        val available = FreeformWindowBounds(0, 0, 3200, 2136)
        val bounds = responsiveFreeformBounds(
            availableBounds = available,
            contentOrientation = FreeformContentOrientation.PORTRAIT,
            isLargeScreen = true,
        )

        assertNineBySixteen(bounds)
        assertTrue(bounds.width <= (available.width * 0.78f).toInt() + 1)
        assertTrue(bounds.height <= (available.height * 0.88f).toInt() + 1)
        assertCentered(bounds, available)
    }

    @Test
    fun tabletLandscapeKeepsLandscapeAppInsideComfortableWorkArea() {
        val available = FreeformWindowBounds(0, 0, 3200, 2136)
        val bounds = responsiveFreeformBounds(
            availableBounds = available,
            contentOrientation = FreeformContentOrientation.LANDSCAPE,
            isLargeScreen = true,
        )

        assertEquals(8f / 5f, bounds.width.toFloat() / bounds.height, 0.002f)
        assertContainedAndCentered(bounds, available)
    }

    @Test
    fun tabletPortraitKeepsLandscapeAppInsideComfortableWorkArea() {
        val available = FreeformWindowBounds(0, 0, 2136, 3200)
        val bounds = responsiveFreeformBounds(
            availableBounds = available,
            contentOrientation = FreeformContentOrientation.LANDSCAPE,
            isLargeScreen = true,
        )

        assertEquals(8f / 5f, bounds.width.toFloat() / bounds.height, 0.002f)
        assertTrue(bounds.width <= (available.width * 0.78f).toInt() + 1)
        assertTrue(bounds.height <= (available.height * 0.82f).toInt() + 1)
        assertCentered(bounds, available)
    }

    @Test
    fun tabletPortraitKeepsPortraitAppInsideComfortableWorkArea() {
        val available = FreeformWindowBounds(0, 0, 2136, 3200)
        val bounds = responsiveFreeformBounds(
            availableBounds = available,
            contentOrientation = FreeformContentOrientation.PORTRAIT,
            isLargeScreen = true,
        )

        assertNineBySixteen(bounds)
        assertContainedAndCentered(bounds, available)
    }

    @Test
    fun phoneLandscapeSupportsBothTargetOrientations() {
        val available = FreeformWindowBounds(0, 0, 2340, 1080)
        val portrait = responsiveFreeformBounds(
            available,
            FreeformContentOrientation.PORTRAIT,
            isLargeScreen = false,
        )
        val landscape = responsiveFreeformBounds(
            available,
            FreeformContentOrientation.LANDSCAPE,
            isLargeScreen = false,
        )

        assertFiveByEight(portrait)
        assertEquals(8f / 5f, landscape.width.toFloat() / landscape.height, 0.002f)
        assertContainedAndCentered(portrait, available)
        assertContainedAndCentered(landscape, available)
    }

    @Test
    fun respectsNonZeroWindowOriginAndInsets() {
        val available = FreeformWindowBounds(40, 80, 1040, 2280)
        val bounds = responsiveFreeformBounds(
            availableBounds = available,
            contentOrientation = FreeformContentOrientation.PORTRAIT,
            isLargeScreen = false,
        )

        assertFiveByEight(bounds)
        assertContainedAndCentered(bounds, available)
    }

    @Test
    fun tinySplitScreenBoundsNeverEscapeTheirAvailableArea() {
        val available = FreeformWindowBounds(100, 200, 101, 201)
        val bounds = responsiveFreeformBounds(
            available,
            FreeformContentOrientation.LANDSCAPE,
            isLargeScreen = true,
        )

        assertContainedAndCentered(bounds, available)
    }

    private fun assertFiveByEight(bounds: FreeformWindowBounds) {
        assertEquals(5f / 8f, bounds.width.toFloat() / bounds.height, 0.002f)
    }

    private fun assertNineBySixteen(bounds: FreeformWindowBounds) {
        assertEquals(9f / 16f, bounds.width.toFloat() / bounds.height, 0.002f)
    }

    private fun assertCentered(bounds: FreeformWindowBounds, available: FreeformWindowBounds) {
        assertTrue(kotlin.math.abs((bounds.left - available.left) - (available.right - bounds.right)) <= 1)
        assertTrue(kotlin.math.abs((bounds.top - available.top) - (available.bottom - bounds.bottom)) <= 1)
    }

    private fun assertContainedAndCentered(
        bounds: FreeformWindowBounds,
        available: FreeformWindowBounds,
    ) {
        assertTrue(bounds.left >= available.left)
        assertTrue(bounds.top >= available.top)
        assertTrue(bounds.right <= available.right)
        assertTrue(bounds.bottom <= available.bottom)
        assertCentered(bounds, available)
    }

    private val FreeformWindowBounds.width: Int
        get() = right - left

    private val FreeformWindowBounds.height: Int
        get() = bottom - top
}
