package com.codex.edgeshelf.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RailScrollPhysicsTest {
    @Test
    fun scrollStartsOnlyForDominantVerticalMotionPastSlop() {
        assertTrue(shouldStartRailScroll(4f, 13f, 12f, 400f))
        assertFalse(shouldStartRailScroll(4f, 12f, 12f, 400f))
        assertFalse(shouldStartRailScroll(14f, 13f, 12f, 400f))
        assertFalse(shouldStartRailScroll(13f, 13f, 12f, 400f))
        assertFalse(shouldStartRailScroll(4f, 13f, 12f, 0f))
        assertFalse(shouldStartRailScroll(Float.NaN, 13f, 12f, 400f))
    }

    @Test
    fun dragOffsetFollowsTheFingerAndClampsAtBothEnds() {
        assertEquals(124f, railOffsetAfterDrag(100f, -24f, 500f), 0f)
        assertEquals(76f, railOffsetAfterDrag(100f, 24f, 500f), 0f)
        assertEquals(0f, railOffsetAfterDrag(10f, 24f, 500f), 0f)
        assertEquals(500f, railOffsetAfterDrag(490f, -24f, 500f), 0f)
        assertEquals(0f, railOffsetAfterDrag(Float.NaN, 20f, 500f), 0f)
        assertEquals(0f, railOffsetAfterDrag(100f, 20f, -1f), 0f)
    }

    @Test
    fun fingerVelocityBecomesBoundedContentVelocity() {
        assertEquals(2_400f, railContentFlingVelocity(-2_400f, 125f, 8_000f), 0f)
        assertEquals(-2_400f, railContentFlingVelocity(2_400f, 125f, 8_000f), 0f)
        assertEquals(0f, railContentFlingVelocity(124f, 125f, 8_000f), 0f)
        assertEquals(-125f, railContentFlingVelocity(125f, 125f, 8_000f), 0f)
        assertEquals(-8_000f, railContentFlingVelocity(12_000f, 125f, 8_000f), 0f)
        assertEquals(8_000f, railContentFlingVelocity(-12_000f, 125f, 8_000f), 0f)
        assertEquals(0f, railContentFlingVelocity(Float.NaN, 125f, 8_000f), 0f)
    }

    @Test
    fun flingOnlyRunsTowardAvailableContent() {
        assertTrue(canRailFling(0f, 500f, 1_000f))
        assertFalse(canRailFling(0f, 500f, -1_000f))
        assertTrue(canRailFling(500f, 500f, -1_000f))
        assertFalse(canRailFling(500f, 500f, 1_000f))
        assertTrue(canRailFling(250f, 500f, 1_000f))
        assertTrue(canRailFling(250f, 500f, -1_000f))
        assertFalse(canRailFling(250f, 0f, 1_000f))
        assertFalse(canRailFling(250f, 500f, 0f))
    }
}
