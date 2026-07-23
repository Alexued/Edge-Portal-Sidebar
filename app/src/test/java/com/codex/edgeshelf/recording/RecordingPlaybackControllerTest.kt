package com.codex.edgeshelf.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPlaybackControllerTest {
    @Test
    fun differentItemAlwaysPrepares() {
        assertEquals(
            PlaybackToggleAction.PREPARE,
            playbackToggleAction(
                RecordingPlaybackState(activeId = "content://one", isPlaying = true),
                "content://two",
            ),
        )
    }

    @Test
    fun activeItemCyclesPauseAndResume() {
        assertEquals(
            PlaybackToggleAction.PAUSE,
            playbackToggleAction(
                RecordingPlaybackState(activeId = "content://one", isPlaying = true),
                "content://one",
            ),
        )
        assertEquals(
            PlaybackToggleAction.RESUME,
            playbackToggleAction(
                RecordingPlaybackState(activeId = "content://one"),
                "content://one",
            ),
        )
    }

    @Test
    fun preparingIgnoresRepeatedTap() {
        assertEquals(
            PlaybackToggleAction.IGNORE,
            playbackToggleAction(
                RecordingPlaybackState(activeId = "content://one", isPreparing = true),
                "content://one",
            ),
        )
    }
}
