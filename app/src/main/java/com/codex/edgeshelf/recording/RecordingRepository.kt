package com.codex.edgeshelf.recording

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A finalized recording created by Edge Shelf. */
@Immutable
data class RecordingEntry(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val sizeBytes: Long,
) {
    val stableId: String
        get() = uri.toString()
}

/** Reads only the app's completed recordings from MediaStore. */
class RecordingRepository(context: Context) {
    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun loadRecordings(): List<RecordingEntry> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.IS_PENDING,
        )
        val selection = "${MediaStore.Audio.Media.IS_PENDING} = 0 AND " +
            "${MediaStore.Audio.Media.RELATIVE_PATH} IN (?, ?)"
        val selectionArgs = arrayOf(RECORDINGS_PATH, "$RECORDINGS_PATH/")

        return resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC, ${MediaStore.Audio.Media._ID} DESC",
        )?.use { cursor ->
            readEntries(cursor)
        } ?: throw RecordingQueryException("MediaStore returned no cursor")
    }

    private fun readEntries(cursor: Cursor): List<RecordingEntry> {
        val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
        val nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
        val addedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        val modifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
        val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
        val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
        val pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
        val pendingColumn = cursor.getColumnIndex(MediaStore.Audio.Media.IS_PENDING)
        if (idColumn < 0 || nameColumn < 0 || pathColumn < 0) {
            throw RecordingQueryException("MediaStore recording columns are unavailable")
        }

        return buildList {
            while (cursor.moveToNext()) {
                if (pendingColumn >= 0 && cursor.getInt(pendingColumn) != 0) continue
                val name = cursor.getString(nameColumn) ?: continue
                val path = cursor.getString(pathColumn).orEmpty().trimEnd('/')
                if (path != RECORDINGS_PATH || !isEdgeShelfRecordingName(name)) continue

                val id = cursor.getLong(idColumn)
                val dateAddedSeconds = cursor.longOrZero(addedColumn)
                val dateModifiedSeconds = cursor.longOrZero(modifiedColumn)
                val createdSeconds = dateAddedSeconds.takeIf { it > 0L }
                    ?: dateModifiedSeconds
                val duration = cursor.longOrZero(durationColumn).coerceAtLeast(0L)
                val size = cursor.longOrZero(sizeColumn).coerceAtLeast(0L)
                add(
                    RecordingEntry(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ),
                        displayName = name,
                        createdAtEpochMs = createdSeconds.saturatedSecondsToMillis(),
                        durationMs = duration,
                        sizeBytes = size,
                    ),
                )
            }
        }.sortedWith(
            compareByDescending<RecordingEntry> { it.createdAtEpochMs }
                .thenByDescending { it.id },
        )
    }

    private fun Cursor.longOrZero(column: Int): Long =
        if (column >= 0 && !isNull(column)) getLong(column) else 0L

    companion object {
        const val RECORDINGS_PATH = "Recordings/EdgeShelf"
        private const val NAME_PREFIX = "EdgeShelf_"

        fun isEdgeShelfRecordingName(name: String): Boolean =
            name.startsWith(NAME_PREFIX) && name.endsWith(".m4a", ignoreCase = true)
    }

    class RecordingQueryException(message: String) : IOException(message)
}

private fun Long.saturatedSecondsToMillis(): Long = when {
    this <= 0L -> 0L
    this > Long.MAX_VALUE / 1_000L -> Long.MAX_VALUE
    else -> this * 1_000L
}

fun formatRecordingTimestamp(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    if (epochMs <= 0L) return "--"
    return runCatching {
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss", locale)
            .withZone(zoneId)
            .format(Instant.ofEpochMilli(epochMs))
    }.getOrDefault("--")
}

fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val seconds = totalSeconds % 60L
    val minutes = (totalSeconds / 60L) % 60L
    val hours = totalSeconds / 3_600L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}

fun formatRecordingFileSize(sizeBytes: Long): String {
    val safeSize = sizeBytes.coerceAtLeast(0L).toDouble()
    val (value, suffix) = when {
        safeSize >= 1_073_741_824.0 -> safeSize / 1_073_741_824.0 to "GB"
        safeSize >= 1_048_576.0 -> safeSize / 1_048_576.0 to "MB"
        safeSize >= 1_024.0 -> safeSize / 1_024.0 to "KB"
        else -> safeSize to "B"
    }
    return if (suffix == "B") {
        "${value.toLong()} $suffix"
    } else {
        "%.1f %s".format(Locale.US, value, suffix)
    }
}
