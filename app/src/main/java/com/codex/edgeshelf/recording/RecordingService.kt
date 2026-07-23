package com.codex.edgeshelf.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.codex.edgeshelf.R
import java.io.IOException
import java.time.LocalDateTime

/**
 * Owns one microphone recording session and publishes its coarse state to the rail.
 *
 * The service is intentionally START_NOT_STICKY: a process restart must never silently resume
 * microphone capture.  A pending MediaStore row is removed on every failed or interrupted path.
 */
class RecordingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaRecorder: MediaRecorder? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var pendingUri: Uri? = null
    private var recordingStartedAtElapsedMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING,
            -> startRecording()

            ACTION_STOP_RECORDING,
            -> stopRecording(startId)

            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // A killed service cannot publish a valid recording.  Release first, then delete the
        // pending row so MediaStore never exposes a truncated file.
        if (mediaRecorder != null || pendingUri != null || outputDescriptor != null) {
            abortSession()
        }
        if (RecordingStateStore.state.value != RecordingUiState.IDLE) {
            RecordingStateStore.publish(RecordingUiState.IDLE)
        }
        mainHandler.removeCallbacksAndMessages(null)
        stopForegroundAndRemoveNotification()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRecording() {
        if (!canStartRecording(RecordingStateStore.state.value)) return

        RecordingStateStore.publish(RecordingUiState.STARTING)
        try {
            ensureMicrophonePermission()
            // Android 14+ validates the microphone FGS type at promotion time.  Promote before
            // touching MediaRecorder so a rejected start leaves no MediaStore row behind.
            startForegroundCompat(buildNotification(recording = false))

            val uri = insertPendingAudio()
            pendingUri = uri
            val descriptor = contentResolver.openFileDescriptor(uri, "w")
                ?: throw RecordingStorageException("Unable to open MediaStore output")
            outputDescriptor = descriptor

            val recorder = createMediaRecorder()
            mediaRecorder = recorder
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(AUDIO_SAMPLE_RATE_HZ)
            recorder.setAudioEncodingBitRate(AUDIO_BIT_RATE)
            recorder.setOutputFile(descriptor.fileDescriptor)
            recorder.prepare()
            recorder.start()

            recordingStartedAtElapsedMs = SystemClock.elapsedRealtime()
            RecordingStateStore.publish(RecordingUiState.RECORDING)
            // Re-post with the final elapsed-time base and the explicit Stop action.
            updateRecordingNotification()
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start recording", error)
            abortSession()
            stopForegroundAndRemoveNotification()
            publishFailure()
        }
    }

    private fun stopRecording(startId: Int) {
        if (!canStopRecording(RecordingStateStore.state.value)) {
            // A notification can outlive a short start failure.  Do not leave an idle service
            // around solely because its old notification was tapped.
            if (RecordingStateStore.state.value == RecordingUiState.IDLE) {
                stopForegroundAndRemoveNotification()
                stopSelfResult(startId)
            }
            return
        }

        RecordingStateStore.publish(RecordingUiState.STOPPING)
        val recorder = mediaRecorder
        mediaRecorder = null
        var stopped = false
        try {
            if (recorder == null) {
                throw IllegalStateException("Recorder disappeared before stop")
            }
            recorder.stop()
            stopped = true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to stop recording cleanly", error)
        } finally {
            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            closeOutputDescriptor()
        }

        val uri = pendingUri
        pendingUri = null
        if (!stopped || uri == null) {
            uri?.let(::deleteMediaStoreRow)
            stopForegroundAndRemoveNotification()
            publishFailure()
            return
        }

        val finalized = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            contentResolver.update(uri, values, null, null) > 0
        }.getOrElse { error ->
            Log.w(TAG, "Unable to finalize recording", error)
            false
        }
        if (!finalized) {
            deleteMediaStoreRow(uri)
            stopForegroundAndRemoveNotification()
            publishFailure()
            return
        }

        stopForegroundAndRemoveNotification()
        RecordingStateStore.publish(RecordingUiState.IDLE)
        stopSelfResult(startId)
    }

    private fun ensureMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("RECORD_AUDIO permission is not granted")
        }
    }

    private fun insertPendingAudio(): Uri {
        val values = ContentValues().apply {
            put(
                MediaStore.Audio.Media.DISPLAY_NAME,
                recordingDisplayName(LocalDateTime.now()),
            )
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE_AUDIO_MP4)
            put(MediaStore.Audio.Media.RELATIVE_PATH, RECORDINGS_RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw RecordingStorageException("Unable to create MediaStore output")
    }

    @Suppress("DEPRECATION")
    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

    private fun abortSession() {
        val recorder = mediaRecorder
        mediaRecorder = null
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        closeOutputDescriptor()
        pendingUri?.let(::deleteMediaStoreRow)
        pendingUri = null
        recordingStartedAtElapsedMs = 0L
    }

    private fun closeOutputDescriptor() {
        runCatching { outputDescriptor?.close() }
        outputDescriptor = null
    }

    private fun deleteMediaStoreRow(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
            .onFailure { error -> Log.w(TAG, "Unable to delete pending recording $uri", error) }
    }

    private fun publishFailure() {
        val failureState = RecordingUiState.ERROR
        RecordingStateStore.publish(failureState)
        // Keep ERROR observable long enough for the rail to communicate the failure, then release
        // the transient service and make the action available again.
        mainHandler.postDelayed({
            if (RecordingStateStore.state.value == failureState) {
                RecordingStateStore.publish(RecordingUiState.IDLE)
            }
            stopSelf()
        }, FAILURE_STATE_DURATION_MS)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            androidx.core.app.ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateRecordingNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(recording = true))
    }

    private fun buildNotification(recording: Boolean): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).setAction(ACTION_STOP)
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_PENDING_INTENT_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_notification_title))
            .setContentText(
                getString(
                    if (recording) {
                        R.string.recording_notification_active
                    } else {
                        R.string.recording_notification_starting
                    },
                ),
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    getString(R.string.recording_notification_stop),
                    stopPendingIntent,
                ).build(),
            )
        if (recording) {
            builder
                .setWhen(System.currentTimeMillis() - elapsedRealtimeDuration())
                .setUsesChronometer(true)
        } else {
            builder.setWhen(System.currentTimeMillis())
        }
        return builder.build()
    }

    private fun elapsedRealtimeDuration(): Long =
        (SystemClock.elapsedRealtime() - recordingStartedAtElapsedMs).coerceAtLeast(0L)

    private fun stopForegroundAndRemoveNotification() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.recording_notification_description)
                setShowBadge(false)
            },
        )
    }

    class RecordingStorageException(message: String) : IOException(message)

    companion object {
        const val ACTION_START_RECORDING = "com.codex.edgeshelf.recording.action.START"
        const val ACTION_STOP_RECORDING = "com.codex.edgeshelf.recording.action.STOP"

        // Short aliases make PendingIntent callers concise while retaining explicit public names.
        const val ACTION_START = ACTION_START_RECORDING
        const val ACTION_STOP = ACTION_STOP_RECORDING

        private const val TAG = "EdgeShelfRecording"
        private const val CHANNEL_ID = "edge_shelf_recording"
        private const val NOTIFICATION_ID = 1002
        private const val STOP_PENDING_INTENT_REQUEST_CODE = 1003
        private const val MIME_TYPE_AUDIO_MP4 = "audio/mp4"
        private const val RECORDINGS_RELATIVE_PATH = "Recordings/EdgeShelf"
        private const val AUDIO_SAMPLE_RATE_HZ = 44_100
        private const val AUDIO_BIT_RATE = 128_000
        private const val FAILURE_STATE_DURATION_MS = 900L

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START_RECORDING)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_STOP_RECORDING)
            context.startService(intent)
        }

        fun createStartIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_START_RECORDING)

        fun createStopIntent(context: Context): Intent =
            Intent(context, RecordingService::class.java).setAction(ACTION_STOP_RECORDING)
    }
}
