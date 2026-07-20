package com.codex.edgeshelf.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRefreshGateTest {
    @Test
    fun inFlightRefreshBlocksAnotherNonForcedRefresh() {
        val gate = ContentRefreshGate(minimumIntervalMs = 3_000L)

        assertTrue(gate.shouldRefresh(nowMs = 10_000L, force = false))
        assertFalse(gate.shouldRefresh(nowMs = 20_000L, force = false))
    }

    @Test
    fun successfulRefreshStartsThrottleAtCompletion() {
        val gate = ContentRefreshGate(minimumIntervalMs = 3_000L)

        assertTrue(gate.shouldRefresh(nowMs = 10_000L, force = false))
        gate.markSucceeded(nowMs = 12_000L)

        assertFalse(gate.shouldRefresh(nowMs = 14_999L, force = false))
        assertTrue(gate.shouldRefresh(nowMs = 15_000L, force = false))
    }

    @Test
    fun failedRefreshCanBeRetriedImmediately() {
        val gate = ContentRefreshGate(minimumIntervalMs = 3_000L)

        assertTrue(gate.shouldRefresh(nowMs = 10_000L, force = false))
        gate.markFailed()

        assertTrue(gate.shouldRefresh(nowMs = 10_001L, force = false))
    }

    @Test
    fun forcedRefreshAlwaysRunsAndRestartsInterval() {
        val gate = ContentRefreshGate(minimumIntervalMs = 3_000L)

        assertTrue(gate.shouldRefresh(nowMs = 10_000L, force = false))
        gate.markSucceeded(nowMs = 10_050L)
        assertTrue(gate.shouldRefresh(nowMs = 10_100L, force = true))
        gate.markSucceeded(nowMs = 10_200L)
        assertFalse(gate.shouldRefresh(nowMs = 13_099L, force = false))
    }

    @Test
    fun resetAndClockRollbackAllowRefresh() {
        val gate = ContentRefreshGate(minimumIntervalMs = 3_000L)

        assertTrue(gate.shouldRefresh(nowMs = 10_000L, force = false))
        gate.markSucceeded(nowMs = 10_000L)
        assertTrue(gate.shouldRefresh(nowMs = 9_000L, force = false))
        gate.reset()
        assertTrue(gate.shouldRefresh(nowMs = 9_100L, force = false))
    }
}
