package com.codex.edgeshelf.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailMotionTest {
    @Test
    fun triggerThresholdAndVisualTravelAreIndependent() {
        assertEquals(24f, RailMotion.EXPAND_TRIGGER_THRESHOLD_DP, 0f)
        assertEquals(72f, RailMotion.PANEL_TRAVEL_DP, 0f)
        assertEquals(0f, RailMotion.panelTravelDp(0f), 0f)
        assertEquals(72f, RailMotion.panelTravelDp(1f), 0f)
        assertEquals(36f, RailMotion.panelTravelDp(0.5f), 0f)
    }

    @Test
    fun interpolatorsExposeTheApprovedPathControlPoints() {
        assertEquals(CubicBezierSpec(0.22f, 0f, 0.20f, 1f), RailMotion.EXPAND_INTERPOLATOR)
        assertEquals(CubicBezierSpec(0.40f, 0f, 0.60f, 1f), RailMotion.COLLAPSE_INTERPOLATOR)
        assertEquals(0f, RailMotion.ease(0f, RailMotion.EXPAND_INTERPOLATOR), 0f)
        assertEquals(1f, RailMotion.ease(1f, RailMotion.EXPAND_INTERPOLATOR), 0f)
        assertTrue(RailMotion.ease(0.25f, RailMotion.COLLAPSE_INTERPOLATOR) < 0.25f)
        assertTrue(RailMotion.ease(0.75f, RailMotion.COLLAPSE_INTERPOLATOR) > 0.75f)
    }

    @Test
    fun panelProgressUsesSeparateExpandAndCollapseDurations() {
        assertEquals(0f, RailMotion.panelProgress(0L, expanding = true), 0f)
        assertEquals(1f, RailMotion.panelProgress(RailMotion.EXPAND_DURATION_MS, expanding = true), 0f)
        assertEquals(1f, RailMotion.panelProgress(0L, expanding = false), 0f)
        assertEquals(0f, RailMotion.panelProgress(RailMotion.COLLAPSE_DURATION_MS, expanding = false), 0f)
        assertTrue(
            RailMotion.panelProgress(180L, expanding = true) >
                RailMotion.panelProgress(180L, expanding = false),
        )
    }

    @Test
    fun panelProgressClampsElapsedTimeAndTravel() {
        assertEquals(0f, RailMotion.panelProgress(-100L, expanding = true), 0f)
        assertEquals(1f, RailMotion.panelProgress(10_000L, expanding = true), 0f)
        assertEquals(1f, RailMotion.panelProgress(-100L, expanding = false), 0f)
        assertEquals(0f, RailMotion.panelProgress(10_000L, expanding = false), 0f)
        assertEquals(0f, RailMotion.panelTravelDp(-1f), 0f)
        assertEquals(72f, RailMotion.panelTravelDp(2f), 0f)
    }

    @Test
    fun panelContentScaleTracksAvailableWidthWithoutClippingIcons() {
        val collapsedFraction = 5f / 56f
        var previousScale = 0f
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val contentWidth = 5f + 51f * progress
            val scale = RailMotion.panelContentScale(progress, collapsedFraction)
            val pressedIconWidth = 40f * 1.06f * scale

            assertTrue(scale >= previousScale)
            assertTrue(pressedIconWidth <= contentWidth)
            previousScale = scale
        }
        assertEquals(collapsedFraction, RailMotion.panelContentScale(0f, collapsedFraction), 0f)
        assertEquals(1f, RailMotion.panelContentScale(1f, collapsedFraction), 0f)
    }

    @Test
    fun exitOffsetReplacesEntranceOffsetWithoutExceedingContentBounds() {
        val entranceScale = 0.936f
        val entranceOffset = 8f
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { exitProgress ->
            val iconHalfWidth = 20f * entranceScale * 1.06f
            val edgeOffset = RailMotion.contentEdgeOffsetDp(entranceOffset, exitProgress)
            assertTrue(iconHalfWidth + edgeOffset <= 28f)
        }
        assertEquals(entranceOffset, RailMotion.contentEdgeOffsetDp(entranceOffset, 0f), 0f)
        assertEquals(
            RailMotion.CONTENT_EXIT_EDGE_OFFSET_DP,
            RailMotion.contentEdgeOffsetDp(entranceOffset, 1f),
            0f,
        )
    }

    @Test
    fun panelContentFadesOnlyNearTheCollapsedEdge() {
        assertEquals(0f, RailMotion.panelContentAlpha(0f), 0f)
        assertEquals(
            0f,
            RailMotion.panelContentAlpha(RailMotion.CONTENT_FADE_INVISIBLE_PROGRESS),
            0f,
        )
        assertEquals(
            1f,
            RailMotion.panelContentAlpha(RailMotion.CONTENT_FADE_OPAQUE_PROGRESS),
            0f,
        )
        assertEquals(1f, RailMotion.panelContentAlpha(1f), 0f)
        assertTrue(RailMotion.panelContentAlpha(0.2f) in 0f..1f)
    }

    @Test
    fun iconDelayAndStaggerAreCapped() {
        assertEquals(72L, RailMotion.iconStartDelayMillis(0))
        assertEquals(88L, RailMotion.iconStartDelayMillis(1))
        assertEquals(168L, RailMotion.iconStartDelayMillis(6))
        assertEquals(168L, RailMotion.iconStartDelayMillis(20))
        assertEquals(348L, RailMotion.CONTENT_TIMELINE_DURATION_MS)
    }

    @Test
    fun iconProgressRunsLocallyForEachRow() {
        val firstDelay = RailMotion.iconStartDelayMillis(0)
        val secondDelay = RailMotion.iconStartDelayMillis(1)
        assertEquals(0f, RailMotion.iconProgress(0, firstDelay), 0f)
        assertEquals(0f, RailMotion.iconProgress(1, secondDelay), 0f)
        assertEquals(1f, RailMotion.iconProgress(0, firstDelay + RailMotion.ICON_DURATION_MS), 0f)
        assertEquals(1f, RailMotion.iconProgress(1, secondDelay + RailMotion.ICON_DURATION_MS), 0f)
        assertTrue(RailMotion.iconProgress(0, secondDelay) > 0f)
        assertEquals(0f, RailMotion.iconProgress(1, firstDelay), 0f)
    }

    @Test
    fun iconFrameInterpolatesAlphaScaleAndEdgeOffset() {
        val delay = RailMotion.iconStartDelayMillis(0)
        val start = RailMotion.iconFrame(0, delay)
        val end = RailMotion.iconFrame(0, delay + RailMotion.ICON_DURATION_MS)
        assertEquals(0f, start.alpha, 0f)
        assertEquals(0.92f, start.scale, 0f)
        assertEquals(10f, start.edgeOffsetDp, 0f)
        assertEquals(1f, end.alpha, 0f)
        assertEquals(1f, end.scale, 0f)
        assertEquals(0f, end.edgeOffsetDp, 0f)
    }

    @Test
    fun reducedMotionReturnsTerminalStatesImmediately() {
        assertEquals(1f, RailMotion.panelProgress(0L, expanding = true, reducedMotion = true), 0f)
        assertEquals(0f, RailMotion.panelProgress(0L, expanding = false, reducedMotion = true), 0f)
        val frame = RailMotion.iconFrame(4, 0L, reducedMotion = true)
        assertEquals(1f, frame.progress, 0f)
        assertEquals(1f, frame.alpha, 0f)
        assertEquals(1f, frame.scale, 0f)
        assertEquals(0f, frame.edgeOffsetDp, 0f)
    }
}
