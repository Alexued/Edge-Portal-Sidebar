package com.codex.edgeshelf.launch

import android.content.ActivityNotFoundException
import android.content.Intent
import com.codex.edgeshelf.data.LaunchableApp

class LaunchCoordinator(
    private val collapse: () -> Unit,
    private val recordRecent: suspend (String) -> Unit,
    private val freeformAttempts: List<(Intent) -> Boolean>,
    private val normalStarter: (Intent) -> Unit,
) {
    suspend fun launch(app: LaunchableApp): Boolean {
        collapse()
        val intent = runCatching {
            Intent(app.launchIntent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
        }.getOrElse { app.launchIntent }
        val started = freeformAttempts.any { attempt ->
            runCatching { attempt(Intent(intent)) }.getOrDefault(false)
        }
        if (!started) {
            val fallback = runCatching {
                normalStarter(intent)
                true
            }.getOrElse { error ->
                if (error is ActivityNotFoundException) false else false
            }
            if (!fallback) return false
        }
        recordRecent(app.packageName)
        return true
    }
}
