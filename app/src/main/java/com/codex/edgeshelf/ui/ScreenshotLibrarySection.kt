package com.codex.edgeshelf.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codex.edgeshelf.R
import com.codex.edgeshelf.recording.formatRecordingTimestamp
import com.codex.edgeshelf.screenshot.ScreenshotEntry
import com.codex.edgeshelf.screenshot.formatScreenshotFileSize
import com.codex.edgeshelf.screenshot.formatScreenshotResolution
import com.codex.edgeshelf.ui.theme.Jade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ScreenshotLibrarySection(
    state: ScreenshotLibraryUiState,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onClearDeleteError: () -> Unit,
) {
    var visibleCount by remember { mutableIntStateOf(INITIAL_SCREENSHOT_ROWS) }
    var previewEntry by remember { mutableStateOf<ScreenshotEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<ScreenshotEntry?>(null) }
    LaunchedEffect(state.entries, state.deletingId, state.deleteFailedId) {
        val pending = deleteEntry ?: return@LaunchedEffect
        if (state.deletingId == null &&
            state.deleteFailedId != pending.stableId &&
            state.entries.none { it.stableId == pending.stableId }
        ) {
            deleteEntry = null
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.screenshots_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.screenshots_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.screenshots_refresh),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        when {
            state.isLoading && state.entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }

            state.loadFailed && state.entries.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.screenshots_load_failed))
                OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }

            state.entries.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    stringResource(R.string.screenshots_empty),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.screenshots_empty_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            else -> Column {
                state.entries.take(visibleCount).forEachIndexed { index, entry ->
                    ScreenshotRow(
                        entry = entry,
                        deleting = state.deletingId == entry.stableId,
                        onPreview = { previewEntry = entry },
                        onDelete = {
                            onClearDeleteError()
                            deleteEntry = entry
                        },
                    )
                    if (index < state.entries.take(visibleCount).lastIndex) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                if (state.entries.size > visibleCount) {
                    OutlinedButton(
                        onClick = {
                            visibleCount = (visibleCount + SCREENSHOT_PAGE_SIZE)
                                .coerceAtMost(state.entries.size)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(14.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.screenshots_show_more,
                                state.entries.size - visibleCount,
                            ),
                        )
                    }
                }
            }
        }
    }

    previewEntry?.let { entry ->
        ScreenshotPreviewDialog(entry = entry, onDismiss = { previewEntry = null })
    }
    deleteEntry?.let { entry ->
        ScreenshotDeleteDialog(
            entry = entry,
            deleting = state.deletingId == entry.stableId,
            failed = state.deleteFailedId == entry.stableId,
            onConfirm = { onDelete(entry.stableId) },
            onDismiss = {
                if (state.deletingId == null) {
                    onClearDeleteError()
                    deleteEntry = null
                }
            },
        )
    }
}

@Composable
private fun ScreenshotRow(
    entry: ScreenshotEntry,
    deleting: Boolean,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
) {
    val timestamp = formatRecordingTimestamp(entry.createdAtEpochMs)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        ScreenshotImage(
            uri = entry.uri,
            targetSizePx = 360,
            modifier = Modifier
                .size(width = 68.dp, height = 104.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .clickable(onClick = onPreview),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = timestamp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatScreenshotResolution(entry)} | " +
                    formatScreenshotFileSize(entry.sizeBytes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onPreview) { Text(stringResource(R.string.preview)) }
        }
        if (deleting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.screenshot_delete_action, timestamp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ScreenshotPreviewDialog(entry: ScreenshotEntry, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ScreenshotImage(
                uri = entry.uri,
                targetSizePx = 2200,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentScale = ContentScale.Fit,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text(stringResource(R.string.screenshot_close_preview)) }
        }
    }
}

@Composable
private fun ScreenshotDeleteDialog(
    entry: ScreenshotEntry,
    deleting: Boolean,
    failed: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.screenshot_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.screenshot_delete_message,
                        formatRecordingTimestamp(entry.createdAtEpochMs),
                        formatScreenshotResolution(entry),
                        formatScreenshotFileSize(entry.sizeBytes),
                    ),
                )
                if (failed) {
                    Text(
                        stringResource(R.string.screenshot_delete_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !deleting) {
                if (deleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.screenshot_delete_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ScreenshotImage(
    uri: Uri,
    targetSizePx: Int,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val context = LocalContext.current
    var image by remember(uri, targetSizePx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri, targetSizePx) {
        image = withContext(Dispatchers.IO) {
            decodeScreenshot(context.contentResolver, uri, targetSizePx)?.asImageBitmap()
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        image?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } ?: CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Jade,
            strokeWidth = 2.dp,
        )
    }
}

private fun decodeScreenshot(
    resolver: android.content.ContentResolver,
    uri: Uri,
    targetSizePx: Int,
): Bitmap? = runCatching {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        val longest = maxOf(width, height)
        if (longest > targetSizePx) {
            val scale = targetSizePx.toFloat() / longest
            decoder.setTargetSize(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
}.getOrNull()

private const val INITIAL_SCREENSHOT_ROWS = 8
private const val SCREENSHOT_PAGE_SIZE = 8
