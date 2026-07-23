package com.codex.edgeshelf.recording

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The small state surface consumed by the rail.  Details about a particular MediaStore row are
 * deliberately kept inside [RecordingService] so the overlay never needs to know about Android
 * storage or recorder lifecycles.
 */
enum class RecordingUiState {
    IDLE,
    STARTING,
    RECORDING,
    STOPPING,
    ERROR,
}

enum class RecordingAction {
    START,
    STOP,
}

/**
 * Pure action policy for the recording control.  STARTING, STOPPING and ERROR are transient
 * states; ignoring taps in those states prevents duplicate recorder instances and overlapping
 * save operations.
 */
fun recordingActionFor(state: RecordingUiState): RecordingAction? = when (state) {
    RecordingUiState.IDLE -> RecordingAction.START
    RecordingUiState.RECORDING -> RecordingAction.STOP
    RecordingUiState.STARTING,
    RecordingUiState.STOPPING,
    RecordingUiState.ERROR,
    -> null
}

fun canStartRecording(state: RecordingUiState): Boolean =
    recordingActionFor(state) == RecordingAction.START

fun canStopRecording(state: RecordingUiState): Boolean =
    recordingActionFor(state) == RecordingAction.STOP

fun isRecordingCaptureActive(state: RecordingUiState): Boolean = when (state) {
    RecordingUiState.STARTING,
    RecordingUiState.RECORDING,
    RecordingUiState.STOPPING,
    -> true
    RecordingUiState.IDLE,
    RecordingUiState.ERROR,
    -> false
}

/**
 * Process-local state bus shared by the overlay and [RecordingService].  All writes happen on
 * the service's main-thread callbacks; exposing only StateFlow keeps consumers read-only.
 */
object RecordingStateStore {
    private val mutableState = MutableStateFlow(RecordingUiState.IDLE)

    val state: StateFlow<RecordingUiState> = mutableState.asStateFlow()

    internal fun publish(next: RecordingUiState) {
        mutableState.value = next
    }
}

/** A descriptive alias for callers that think of the state as an event bus. */
object RecordingStateBus {
    val state: StateFlow<RecordingUiState>
        get() = RecordingStateStore.state
}

private val recordingTimestampFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

/**
 * Creates the stable MediaStore display name used for a recording.
 * Keeping this pure makes naming independently testable and avoids locale-dependent file names.
 */
fun recordingDisplayName(timestamp: LocalDateTime): String =
    "EdgeShelf_${timestamp.format(recordingTimestampFormatter)}.m4a"
