package com.codex.edgeshelf.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EdgeDistancePreviewTest {
    @After
    fun tearDown() {
        EdgeDistancePreview.clear()
    }

    @Test
    fun updateIsImmediateAndNormalized() {
        EdgeDistancePreview.update(18f)
        assertEquals(18f, EdgeDistancePreview.distanceDp.value)

        EdgeDistancePreview.update(80f)
        assertEquals(40f, EdgeDistancePreview.distanceDp.value)

        EdgeDistancePreview.update(Float.NaN)
        assertEquals(0f, EdgeDistancePreview.distanceDp.value)
    }

    @Test
    fun oldCommitCannotClearANewerPreview() {
        EdgeDistancePreview.update(20f)
        EdgeDistancePreview.update(30f)
        EdgeDistancePreview.clear(expectedDistanceDp = 20f)
        assertEquals(30f, EdgeDistancePreview.distanceDp.value)

        EdgeDistancePreview.clear(expectedDistanceDp = 30f)
        assertNull(EdgeDistancePreview.distanceDp.value)
    }
}
