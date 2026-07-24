package com.codex.edgeshelf.screenshot

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

class ScreenshotRepository(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    suspend fun saveScreenshot(bitmap: Bitmap): ScreenshotEntry = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val displayName = screenshotDisplayName(LocalDateTime.now())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, SCREENSHOT_MIME_TYPE)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/EdgeShelf",
            )
            put(MediaStore.Images.Media.DATE_TAKEN, now)
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(collection, values)) {
            "MediaStore rejected screenshot insertion"
        }
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output) { "Unable to open screenshot output" }
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to encode screenshot"
                }
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            ScreenshotEntry(
                id = ContentUris.parseId(uri),
                uri = uri,
                displayName = displayName,
                createdAtEpochMs = now,
                widthPx = bitmap.width,
                heightPx = bitmap.height,
                sizeBytes = querySize(uri),
            )
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    suspend fun loadScreenshots(): List<ScreenshotEntry> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
        )
        resolver.query(
            collection,
            projection,
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf(SCREENSHOT_DIRECTORY, "$SCREENSHOT_FILE_PREFIX%.png"),
            "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            buildList {
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameColumn)
                    val relativePath = cursor.getString(pathColumn)
                    val mimeType = cursor.getString(mimeColumn)
                    if (!isOwnedScreenshot(relativePath, displayName, mimeType)) continue
                    val id = cursor.getLong(idColumn)
                    val taken = cursor.getLong(takenColumn)
                    val addedSeconds = cursor.getLong(addedColumn)
                    add(
                        ScreenshotEntry(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = displayName,
                            createdAtEpochMs = taken.takeIf { it > 0L }
                                ?: (addedSeconds * 1_000L),
                            widthPx = cursor.getInt(widthColumn),
                            heightPx = cursor.getInt(heightColumn),
                            sizeBytes = cursor.getLong(sizeColumn),
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    suspend fun deleteScreenshot(entry: ScreenshotEntry): Boolean = withContext(Dispatchers.IO) {
        resolver.delete(entry.uri, null, null) > 0
    }

    private fun querySize(uri: android.net.Uri): Long = resolver.query(
        uri,
        arrayOf(MediaStore.Images.Media.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    } ?: 0L
}
