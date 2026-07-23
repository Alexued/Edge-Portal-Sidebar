package com.codex.edgeshelf.recording

import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingRepositoryTest {
    @Test
    fun onlyEdgeShelfM4aNamesAreAccepted() {
        assertTrue(RecordingRepository.isEdgeShelfRecordingName("EdgeShelf_20260723_120000_000.m4a"))
        assertTrue(RecordingRepository.isEdgeShelfRecordingName("EdgeShelf_test.M4A"))
        assertFalse(RecordingRepository.isEdgeShelfRecordingName("Other_20260723.m4a"))
        assertFalse(RecordingRepository.isEdgeShelfRecordingName("EdgeShelf_20260723.wav"))
    }

    @Test
    fun durationFormattingIsStable() {
        assertEquals("0:00", formatRecordingDuration(0L))
        assertEquals("1:05", formatRecordingDuration(65_900L))
        assertEquals("1:01:05", formatRecordingDuration(3_665_000L))
    }

    @Test
    fun sizeFormattingUsesReadableUnits() {
        assertEquals("0 B", formatRecordingFileSize(0L))
        assertEquals("1.5 KB", formatRecordingFileSize(1_536L))
        assertEquals("2.0 MB", formatRecordingFileSize(2L * 1_048_576L))
    }

    @Test
    fun timestampFormattingUsesTheRequestedZoneAndLocale() {
        assertEquals(
            "2026/07/23 12:00:00",
            formatRecordingTimestamp(
                epochMs = 1_784_808_000_000L,
                zoneId = ZoneOffset.UTC,
                locale = Locale.US,
            ),
        )
    }
}
