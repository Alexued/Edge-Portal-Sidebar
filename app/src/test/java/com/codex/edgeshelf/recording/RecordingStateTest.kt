package com.codex.edgeshelf.recording

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStateTest {
    @Test
    fun idleOffersStartAndRecordingOffersStop() {
        assertEquals(RecordingAction.START, recordingActionFor(RecordingUiState.IDLE))
        assertEquals(RecordingAction.STOP, recordingActionFor(RecordingUiState.RECORDING))
        assertTrue(canStartRecording(RecordingUiState.IDLE))
        assertTrue(canStopRecording(RecordingUiState.RECORDING))
    }

    @Test
    fun transitionalAndErrorStatesIgnoreActions() {
        listOf(
            RecordingUiState.STARTING,
            RecordingUiState.STOPPING,
            RecordingUiState.ERROR,
        ).forEach { state ->
            assertNull(recordingActionFor(state))
            assertEquals(false, canStartRecording(state))
            assertEquals(false, canStopRecording(state))
        }
    }

    @Test
    fun displayNameUsesStableMachineSortableFormat() {
        val name = recordingDisplayName(LocalDateTime.of(2026, 7, 23, 9, 8, 7, 654_000_000))

        assertEquals("EdgeShelf_20260723_090807_654.m4a", name)
        assertTrue(name.matches(Regex("EdgeShelf_[0-9]{8}_[0-9]{6}_[0-9]{3}\\.m4a")))
    }
}
