package com.codex.edgeshelf.launch

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import com.codex.edgeshelf.data.LaunchableApp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchCoordinatorTest {
    @Test
    fun freeformStrategiesRunInOrderUntilOneSucceeds() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            freeformAttempts = listOf(
                { events += "freeform:first"; false },
                { events += "freeform:second"; true },
                { events += "freeform:third"; true },
            ),
        )

        assertTrue(coordinator.launch(app()))
        assertEquals(
            listOf(
                "collapse",
                "freeform:first",
                "freeform:second",
                "recent:com.example",
            ),
            events,
        )
    }

    @Test
    fun throwingFreeformStrategyDoesNotPreventLaterAttempt() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            freeformAttempts = listOf(
                {
                    events += "freeform:throw"
                    error("unsupported strategy")
                },
                { events += "freeform:success"; true },
            ),
        )

        assertTrue(coordinator.launch(app()))
        assertEquals(
            listOf(
                "collapse",
                "freeform:throw",
                "freeform:success",
                "recent:com.example",
            ),
            events,
        )
    }

    @Test
    fun allFreeformStrategiesFailThenNormalLaunchRuns() = runBlocking {
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            events = events,
            freeformAttempts = listOf(
                { events += "freeform:false"; false },
                {
                    events += "freeform:throw"
                    error("OEM API unavailable")
                },
            ),
        )

        assertTrue(coordinator.launch(app()))
        assertEquals(
            listOf(
                "collapse",
                "freeform:false",
                "freeform:throw",
                "normal",
                "recent:com.example",
            ),
            events,
        )
    }

    @Test
    fun successfulFreeformStrategyNeverUsesNormalLaunch() = runBlocking {
        val events = mutableListOf<String>()
        var normalStarted = false
        val coordinator = LaunchCoordinator(
            collapse = { events += "collapse" },
            recordRecent = { events += "recent:$it" },
            freeformAttempts = listOf({ events += "freeform"; true }),
            normalStarter = { normalStarted = true },
        )

        assertTrue(coordinator.launch(app()))
        assertFalse(normalStarted)
        assertEquals(listOf("collapse", "freeform", "recent:com.example"), events)
    }

    private fun coordinator(
        events: MutableList<String>,
        freeformAttempts: List<(Intent) -> Boolean>,
    ) = LaunchCoordinator(
        collapse = { events += "collapse" },
        recordRecent = { events += "recent:$it" },
        freeformAttempts = freeformAttempts,
        normalStarter = { events += "normal" },
    )

    private fun app() = LaunchableApp(
        packageName = "com.example",
        label = "Example",
        icon = ColorDrawable(),
        launchIntent = Intent(),
    )
}
