package com.codex.edgeshelf.screenshot

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotModelsTest {
    @Test
    fun displayName_isStableAndMillisecondPrecise() {
        assertEquals(
            "EdgeShelf_20260724_123456_078.png",
            screenshotDisplayName(LocalDateTime.of(2026, 7, 24, 12, 34, 56, 78_000_000)),
        )
    }

    @Test
    fun ownership_requiresExactDirectoryPrefixSuffixAndMimeType() {
        assertTrue(
            isOwnedScreenshot(
                SCREENSHOT_DIRECTORY,
                "EdgeShelf_20260724_123456_078.png",
                SCREENSHOT_MIME_TYPE,
            ),
        )
        assertFalse(isOwnedScreenshot("Pictures/", "EdgeShelf_x.png", SCREENSHOT_MIME_TYPE))
        assertFalse(isOwnedScreenshot(SCREENSHOT_DIRECTORY, "Other.png", SCREENSHOT_MIME_TYPE))
        assertFalse(isOwnedScreenshot(SCREENSHOT_DIRECTORY, "EdgeShelf_x.jpg", "image/jpeg"))
    }

    @Test
    fun fileSizeFormatting_usesBinaryUnits() {
        assertEquals("0 B", formatScreenshotFileSize(0))
        assertEquals("512 B", formatScreenshotFileSize(512))
        assertEquals("1.0 KB", formatScreenshotFileSize(1024))
    }
}
