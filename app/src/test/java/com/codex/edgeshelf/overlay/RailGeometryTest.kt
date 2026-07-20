package com.codex.edgeshelf.overlay

import com.codex.edgeshelf.data.ShelfSide
import org.junit.Assert.assertEquals
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
}
