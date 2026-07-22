package com.codex.edgeshelf.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

class UsageRepository(
    private val context: Context,
) {
    fun canReadUsageStats(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun loadRecentPackages(limit: Int = 80): List<String> {
        if (limit <= 0 || !canReadUsageStats()) return emptyList()
        val usageStats = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        return runCatching {
            usageStats.queryAndAggregateUsageStats(now - 7 * 24 * 60 * 60 * 1000L, now)
                .values
                .asSequence()
                .filter { it.packageName != context.packageName && it.lastTimeUsed > 0L }
                .sortedWith(compareByDescending<android.app.usage.UsageStats> { it.lastTimeUsed }
                    .thenBy { it.packageName })
                .map { it.packageName }
                .distinct()
                .take(limit)
                .toList()
        }.getOrDefault(emptyList())
    }
}

internal fun normalizeRecentPackages(
    packages: Iterable<String>,
    selfPackage: String,
    limit: Int = 80,
): List<String> = packages.asSequence()
    .map(String::trim)
    .filter { it.isNotEmpty() && it != selfPackage }
    .distinct()
    .take(limit.coerceAtLeast(0))
    .toList()
