package com.codex.edgeshelf.launch

import android.content.ActivityNotFoundException
import android.content.Intent
import com.codex.edgeshelf.data.LaunchableApp
import kotlinx.coroutines.CancellationException

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
            try {
                attempt(Intent(intent))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                false
            }
        }
        if (!started) {
            val fallback = try {
                normalStarter(intent)
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: ActivityNotFoundException) {
                false
            } catch (_: Throwable) {
                false
            }
            if (!fallback) return false
        }
        recordRecent(app.packageName)
        return true
    }
}
