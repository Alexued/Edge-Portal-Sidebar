package com.codex.edgeshelf.screenshot

import android.net.Uri
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SCREENSHOT_DIRECTORY = "Pictures/EdgeShelf/"
const val SCREENSHOT_FILE_PREFIX = "EdgeShelf_"
const val SCREENSHOT_MIME_TYPE = "image/png"

data class ScreenshotEntry(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val createdAtEpochMs: Long,
    val widthPx: Int,
    val heightPx: Int,
    val sizeBytes: Long,
) {
    val stableId: String
        get() = uri.toString()
}

sealed interface ScreenshotCaptureResult {
    data class Saved(val entryUri: Uri) : ScreenshotCaptureResult
    data object Unsupported : ScreenshotCaptureResult
    data object ServiceUnavailable : ScreenshotCaptureResult
    data object Busy : ScreenshotCaptureResult
    data class Failed(val errorCode: Int? = null) : ScreenshotCaptureResult
}

private val screenshotTimestampFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS", Locale.US)

fun screenshotDisplayName(timestamp: LocalDateTime): String =
    "$SCREENSHOT_FILE_PREFIX${timestamp.format(screenshotTimestampFormatter)}.png"

internal fun isOwnedScreenshot(
    relativePath: String?,
    displayName: String?,
    mimeType: String?,
): Boolean = relativePath == SCREENSHOT_DIRECTORY &&
    displayName?.startsWith(SCREENSHOT_FILE_PREFIX) == true &&
    displayName.endsWith(".png", ignoreCase = true) &&
    mimeType.equals(SCREENSHOT_MIME_TYPE, ignoreCase = true)

fun formatScreenshotResolution(entry: ScreenshotEntry): String =
    if (entry.widthPx > 0 && entry.heightPx > 0) {
        "${entry.widthPx} × ${entry.heightPx}"
    } else {
        "—"
    }

fun formatScreenshotFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "$bytes ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}
