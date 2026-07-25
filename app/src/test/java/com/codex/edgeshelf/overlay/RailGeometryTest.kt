package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RailGeometryTest {
    @Test
    fun verticalTopClampsToSafeArea() {
        assertEquals(24, verticalTop(-1f, 1000, 100, 24, 24))
        assertEquals(876, verticalTop(2f, 1000, 100, 24, 24))
    }

    @Test
    fun boundsMirrorAcrossSides() {
        assertEquals(RailBounds(900, 100, 1000, 200), railBounds(ShelfSide.RIGHT, 1000, 100, 100))
        assertEquals(RailBounds(0, 100, 100, 200), railBounds(ShelfSide.LEFT, 1000, 100, 100))
    }

    @Test
    fun gestureExclusionOnlyClaimsTheEdgeHandle() {
        assertEquals(
            RailBounds(110, 0, 140, 116),
            railGestureExclusionBounds(
                side = ShelfSide.RIGHT,
                viewWidth = 140,
                viewHeight = 116,
                maximumWidth = 30,
                maximumHeight = 200,
                enabled = true,
            ),
        )
        assertEquals(
            RailBounds(0, 0, 30, 116),
            railGestureExclusionBounds(
                side = ShelfSide.LEFT,
                viewWidth = 140,
                viewHeight = 116,
                maximumWidth = 30,
                maximumHeight = 200,
                enabled = true,
            ),
        )
    }

    @Test
    fun gestureExclusionIsCappedAndRejectsInvalidRequests() {
        assertEquals(
            RailBounds(20, 0, 120, 200),
            railGestureExclusionBounds(
                side = ShelfSide.RIGHT,
                viewWidth = 120,
                viewHeight = 500,
                maximumWidth = 100,
                maximumHeight = 200,
                enabled = true,
            ),
        )
        assertEquals(
            null,
            railGestureExclusionBounds(ShelfSide.RIGHT, 120, 116, 30, 200, enabled = false),
        )
        assertEquals(
            null,
            railGestureExclusionBounds(ShelfSide.RIGHT, 0, 116, 30, 200, enabled = true),
        )
        assertEquals(
            null,
            railGestureExclusionBounds(ShelfSide.RIGHT, 120, 116, 0, 200, enabled = true),
        )
    }

    @Test
    fun requestedEdgeOffsetIsAuthoritativeAndSettlesToTheEdge() {
        assertEquals(0, effectiveRailEdgeOffset(0))
        assertEquals(20, effectiveRailEdgeOffset(20))
        assertEquals(0, effectiveRailEdgeOffset(-10))

        assertEquals(20, railEdgeOffset(20, 0f))
        assertEquals(40, railEdgeOffset(80, 0.5f))
        assertEquals(0, railEdgeOffset(80, 1f))
        assertEquals(0, railEdgeOffset(80, 2f))
        assertEquals(80, railEdgeOffset(80, Float.NaN))
        assertEquals(0, railEdgeOffset(-10, 0f))
    }

    @Test
    fun affectedGestureNavigationKeepsCompactGeometryAtThePhysicalEdge() {
        assertTrue(
            usesCompactCollapsedRail(
                affectedGestureNavigation = true,
                requestedEdgeDistance = 0,
            ),
        )
        assertTrue(
            usesCompactCollapsedRail(
                affectedGestureNavigation = false,
                requestedEdgeDistance = 1,
            ),
        )
        assertEquals(
            false,
            usesCompactCollapsedRail(
                affectedGestureNavigation = false,
                requestedEdgeDistance = 0,
            ),
        )
    }

    @Test
    fun gestureSafeGripBoundsMirrorTheCompactWindow() {
        assertEquals(
            RailBounds(88, 80, 98, 120),
            gestureSafeGripBounds(
                side = ShelfSide.RIGHT,
                viewWidth = 100,
                viewHeight = 200,
                gripWidth = 10,
                gripHeight = 40,
                edgeMargin = 2,
            ),
        )
        assertEquals(
            RailBounds(2, 80, 12, 120),
            gestureSafeGripBounds(
                side = ShelfSide.LEFT,
                viewWidth = 100,
                viewHeight = 200,
                gripWidth = 10,
                gripHeight = 40,
                edgeMargin = 2,
            ),
        )
    }

    @Test
    fun gestureSafeGripBoundsClipAndRejectInvalidGeometry() {
        assertEquals(
            RailBounds(10, 0, 20, 20),
            gestureSafeGripBounds(
                side = ShelfSide.RIGHT,
                viewWidth = 20,
                viewHeight = 20,
                gripWidth = 10,
                gripHeight = 40,
                edgeMargin = 0,
            ),
        )
        assertEquals(
            null,
            gestureSafeGripBounds(ShelfSide.RIGHT, 0, 20, 4, 10, 0),
        )
        assertEquals(
            null,
            gestureSafeGripBounds(ShelfSide.LEFT, 20, 20, 4, 10, 25),
        )
    }

    @Test
    fun phoneAndTabletRowCapacityRespectTheirPreferredMaximum() {
        assertEquals(6, visibleRailRowCapacity(2200, 148, 16, 6))
        assertEquals(10, visibleRailRowCapacity(3000, 148, 16, 10))
        assertEquals(2, visibleRailRowCapacity(340, 148, 16, 10))
        assertEquals(4, visibleRailRowCapacity(900, 148, 16, 10, reservedHeight = 148))
    }

    @Test
    fun recordingHeaderHitTestingDoesNotLeakIntoAdjacentRows() {
        assertTrue(railHeaderContains(10f, 20f, 0f, 0f, 68f, 54f))
        assertEquals(false, railHeaderContains(10f, 54f, 0f, 0f, 68f, 54f))
        assertEquals(false, railHeaderContains(Float.NaN, 20f, 0f, 0f, 68f, 54f))
    }

    @Test
    fun contentStaysHiddenUntilPanelIsMeaningfullyOpen() {
        assertEquals(0, railContentAlpha(0.39f))
        assertEquals(0, railContentAlpha(0.4f))
        assertEquals(127, railContentAlpha(0.7f))
        assertEquals(255, railContentAlpha(1f))
    }

    @Test
    fun scrollOffsetUsesTheFixedViewportRatherThanGrowingWithContent() {
        assertEquals(0f, maxRailScrollOffset(6, 6, 54f), 0f)
        assertEquals(54f * 6f, maxRailScrollOffset(12, 6, 54f), 0f)
        assertEquals(54f * 3f, maxRailScrollOffset(13, 10, 54f), 0f)
    }

    @Test
    fun refreshedContentClampsAnOldOffsetToItsNewEnd() {
        assertEquals(216f, clampRailScrollOffset(500f, 216f), 0f)
        assertEquals(0f, clampRailScrollOffset(-20f, 216f), 0f)
        assertEquals(0f, clampRailScrollOffset(Float.NaN, 216f), 0f)
        assertEquals(0f, clampRailScrollOffset(40f, 0f), 0f)
    }

    @Test
    fun rowHitTestingIncludesScrollAndRejectsViewportEdges() {
        assertEquals(0, railRowIndexAt(0f, 324f, 0f, 54f, 12))
        assertEquals(5, railRowIndexAt(323.9f, 324f, 0f, 54f, 12))
        assertEquals(6, railRowIndexAt(323.9f, 324f, 54f, 54f, 12))
        assertEquals(-1, railRowIndexAt(-0.1f, 324f, 0f, 54f, 12))
        assertEquals(-1, railRowIndexAt(324f, 324f, 0f, 54f, 12))
    }

    @Test
    fun visibleRangeOnlyContainsRowsThatIntersectTheViewport() {
        assertEquals(0..5, visibleRailRowRange(0f, 324f, 54f, 20))
        assertEquals(0..6, visibleRailRowRange(1f, 324f, 54f, 20))
        assertEquals(10..15, visibleRailRowRange(540f, 324f, 54f, 20))
        assertTrue(visibleRailRowRange(0f, 324f, 54f, 0).isEmpty())
    }
}
