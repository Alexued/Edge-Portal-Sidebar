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
    fun phoneAndTabletRowCapacityRespectTheirPreferredMaximum() {
        assertEquals(6, visibleRailRowCapacity(2200, 148, 16, 6))
        assertEquals(10, visibleRailRowCapacity(3000, 148, 16, 10))
        assertEquals(2, visibleRailRowCapacity(340, 148, 16, 10))
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
