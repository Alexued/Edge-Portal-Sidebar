package com.codex.edgeshelf.service

internal class ContentRefreshGate(
    private val minimumIntervalMs: Long,
) {
    private var lastSuccessfulRefreshAtMs: Long? = null
    private var refreshInFlight = false

    fun shouldRefresh(nowMs: Long, force: Boolean): Boolean {
        if (!force && refreshInFlight) return false
        val previous = lastSuccessfulRefreshAtMs
        if (previous != null && nowMs >= previous && nowMs - previous < minimumIntervalMs) {
            if (!force) return false
        }
        refreshInFlight = true
        return true
    }

    fun markSucceeded(nowMs: Long) {
        lastSuccessfulRefreshAtMs = nowMs
        refreshInFlight = false
    }

    fun markFailed() {
        refreshInFlight = false
    }

    fun reset() {
        lastSuccessfulRefreshAtMs = null
        refreshInFlight = false
    }
}
