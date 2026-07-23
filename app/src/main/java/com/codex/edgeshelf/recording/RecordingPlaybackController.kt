package com.codex.edgeshelf.recording

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class RecordingPlaybackState(
    val activeId: String? = null,
    val isPreparing: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val hasError: Boolean = false,
    val errorId: String? = null,
)

internal enum class PlaybackToggleAction {
    IGNORE,
    PREPARE,
    PAUSE,
    RESUME,
}

internal fun playbackToggleAction(
    state: RecordingPlaybackState,
    requestedId: String,
): PlaybackToggleAction = when {
    state.activeId != requestedId -> PlaybackToggleAction.PREPARE
    state.isPreparing -> PlaybackToggleAction.IGNORE
    state.isPlaying -> PlaybackToggleAction.PAUSE
    else -> PlaybackToggleAction.RESUME
}

/** Owns one local MediaPlayer and exposes a small immutable state surface to Compose. */
class RecordingPlaybackController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        runOnMain {
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                -> if (mutableState.value.isPlaying) pauseOnMain()
            }
        }
    }
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(audioFocusListener, mainHandler)
        .build()
    private val mutableState = MutableStateFlow(RecordingPlaybackState())
    private var player: MediaPlayer? = null
    private var hasAudioFocus = false
    private var noisyReceiverRegistered = false

    val state: StateFlow<RecordingPlaybackState> = mutableState.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                runOnMain {
                    if (mutableState.value.isPlaying) pauseOnMain()
                }
            }
        }
    }

    private val clearError = Runnable {
        if (mutableState.value.hasError) mutableState.value = RecordingPlaybackState()
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val currentPlayer = player
            val currentState = mutableState.value
            if (currentPlayer == null || !currentState.isPlaying) return
            val position = runCatching { currentPlayer.currentPosition.toLong() }.getOrDefault(0L)
            mutableState.value = currentState.copy(positionMs = position)
            mainHandler.postDelayed(this, PROGRESS_TICK_MS)
        }
    }

    fun toggle(entry: RecordingEntry) {
        runOnMain { toggleOnMain(entry) }
    }

    fun release() {
        runOnMain { releaseOnMain() }
    }

    private fun toggleOnMain(entry: RecordingEntry) {
        when (playbackToggleAction(mutableState.value, entry.stableId)) {
            PlaybackToggleAction.IGNORE -> Unit
            PlaybackToggleAction.PREPARE -> prepareOnMain(entry)
            PlaybackToggleAction.PAUSE -> pauseOnMain()
            PlaybackToggleAction.RESUME -> resumeOnMain()
        }
    }

    private fun prepareOnMain(entry: RecordingEntry) {
        mainHandler.removeCallbacks(clearError)
        releasePlayerOnly()
        val candidate = MediaPlayer()
        player = candidate
        mutableState.value = RecordingPlaybackState(
            activeId = entry.stableId,
            isPreparing = true,
            durationMs = entry.durationMs.coerceAtLeast(0L),
        )

        registerNoisyReceiver()
        candidate.setAudioAttributes(audioAttributes)
        candidate.setOnPreparedListener { preparedPlayer ->
            if (player !== preparedPlayer) {
                runCatching { preparedPlayer.release() }
                return@setOnPreparedListener
            }
            val preparedDuration = runCatching { preparedPlayer.duration.toLong() }
                .getOrDefault(0L)
                .takeIf { it > 0L }
                ?: entry.durationMs.coerceAtLeast(0L)
            mutableState.value = mutableState.value.copy(
                isPreparing = false,
                durationMs = preparedDuration,
                hasError = false,
            )
            resumeOnMain()
        }
        candidate.setOnCompletionListener { completedPlayer ->
            if (player !== completedPlayer) return@setOnCompletionListener
            releasePlayerOnly()
            mutableState.value = RecordingPlaybackState()
        }
        candidate.setOnErrorListener { failedPlayer, _, _ ->
            if (player === failedPlayer) failOnMain()
            true
        }

        runCatching {
            candidate.setDataSource(appContext, entry.uri)
            candidate.prepareAsync()
        }.onFailure {
            if (player === candidate) failOnMain()
            else runCatching { candidate.release() }
        }
    }

    private fun pauseOnMain() {
        val currentPlayer = player ?: return
        runCatching { currentPlayer.pause() }
            .onFailure { failOnMain() }
            .onSuccess {
                stopProgressTicker()
                abandonAudioFocus()
                mutableState.value = mutableState.value.copy(
                    isPlaying = false,
                    positionMs = runCatching { currentPlayer.currentPosition.toLong() }
                        .getOrDefault(mutableState.value.positionMs),
                )
            }
    }

    private fun resumeOnMain() {
        val currentPlayer = player ?: return
        if (mutableState.value.isPreparing) return
        if (!requestAudioFocus()) {
            failOnMain()
            return
        }
        runCatching { currentPlayer.start() }
            .onFailure { failOnMain() }
            .onSuccess {
                mutableState.value = mutableState.value.copy(
                    isPlaying = true,
                    hasError = false,
                )
                startProgressTicker()
            }
    }

    private fun failOnMain() {
        val failedId = mutableState.value.activeId
        releasePlayerOnly()
        mutableState.value = RecordingPlaybackState(
            hasError = true,
            errorId = failedId,
        )
        mainHandler.removeCallbacks(clearError)
        mainHandler.postDelayed(clearError, ERROR_DISPLAY_MS)
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        mainHandler.post(progressTicker)
    }

    private fun stopProgressTicker() {
        mainHandler.removeCallbacks(progressTicker)
    }

    private fun releaseOnMain() {
        mainHandler.removeCallbacks(clearError)
        releasePlayerOnly()
        mutableState.value = RecordingPlaybackState()
    }

    private fun releasePlayerOnly() {
        stopProgressTicker()
        player?.let { currentPlayer -> runCatching { currentPlayer.release() } }
        player = null
        abandonAudioFocus()
        unregisterNoisyReceiver()
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val manager = audioManager ?: return true
        hasAudioFocus = manager.requestAudioFocus(audioFocusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocusRequest(audioFocusRequest)
        hasAudioFocus = false
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        runCatching {
            ContextCompat.registerReceiver(
                appContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onSuccess {
            noisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        runCatching { appContext.unregisterReceiver(noisyReceiver) }
        noisyReceiverRegistered = false
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private companion object {
        const val PROGRESS_TICK_MS = 250L
        const val ERROR_DISPLAY_MS = 2_000L
    }
}
