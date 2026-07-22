package com.codex.edgeshelf.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderLayoutTest {
    @Test
    fun metadataStaysInlineWhenThereIsRoom() {
        assertFalse(shouldStackHeaderMetadata(maxWidthDp = 420f, fontScale = 1f))
    }

    @Test
    fun metadataStacksForNarrowHeaders() {
        assertTrue(shouldStackHeaderMetadata(maxWidthDp = 359.9f, fontScale = 1f))
    }

    @Test
    fun metadataStacksForLargeText() {
        assertTrue(shouldStackHeaderMetadata(maxWidthDp = 420f, fontScale = 1.21f))
    }
}
