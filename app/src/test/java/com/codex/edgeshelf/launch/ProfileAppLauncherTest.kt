package com.codex.edgeshelf.launch

import android.content.Intent
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import com.codex.edgeshelf.data.LaunchableApp
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAppLauncherTest {
    @Test
    fun xspaceSpecUsesVerifiedHyperOsExtrasOnlyForAnotherXiaomiProfile() {
        val spec = xSpaceLaunchSpec(
            manufacturer = "XIAOMI",
            targetUserIdentifier = 999,
            isCurrentUser = false,
        )

        assertEquals(999, spec?.targetUserIdentifier)
        assertEquals("android.intent.extra.xspace_cached_uid", spec?.cachedUidKey)
        assertEquals("android.intent.extra.xspace_userid_selected", spec?.userSelectedKey)
        assertEquals(null, xSpaceLaunchSpec("Google", 10, isCurrentUser = false))
        assertEquals(null, xSpaceLaunchSpec("Xiaomi", 0, isCurrentUser = true))

        val ownerSpec = xSpaceUserSelectionSpec("Xiaomi", targetUserIdentifier = 0)
        assertEquals(0, ownerSpec?.targetUserIdentifier)
        assertEquals("android.intent.extra.xspace_cached_uid", ownerSpec?.cachedUidKey)
    }

    @Test
    fun currentUserIsLeftToTheExistingOwnerLaunchPath() {
        val events = mutableListOf<String>()
        val launcher = launcher(events = events, isCurrentUser = true)

        assertFalse(launcher.launch(app(), Rect()))
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun profileFreeformSuccessStopsTheFallbackChain() {
        val events = mutableListOf<String>()
        val launcher = launcher(events = events)

        assertTrue(launcher.launch(app(), Rect()))
        assertEquals(listOf("freeform"), events)
    }

    @Test
    fun rejectedProfileFreeformFallsBackToNormalProfileLaunch() {
        val events = mutableListOf<String>()
        val launcher = launcher(
            events = events,
            freeform = {
                events += "freeform:denied"
                throw SecurityException("freeform option rejected")
            },
            normal = { events += "normal" },
        )

        assertTrue(launcher.launch(app(), Rect()))
        assertEquals(listOf("freeform:denied", "normal"), events)
    }

    @Test
    fun xspaceCompatibilityRunsOnlyAfterBothPublicAttemptsFail() {
        val events = mutableListOf<String>()
        val launcher = launcher(
            events = events,
            freeform = {
                events += "freeform:denied"
                throw SecurityException("denied")
            },
            normal = {
                events += "normal:missing"
                throw IllegalStateException("profile unavailable")
            },
            xspace = {
                events += "xspace"
                true
            },
        )

        assertTrue(launcher.launch(app(), Rect()))
        assertEquals(listOf("freeform:denied", "normal:missing", "xspace"), events)
    }

    @Test
    fun allProfileAttemptsFailWithoutClaimingLaunchSuccess() {
        val events = mutableListOf<String>()
        val launcher = launcher(
            events = events,
            freeform = { throw SecurityException("denied") },
            normal = { throw IllegalStateException("unavailable") },
            xspace = {
                events += "xspace:false"
                false
            },
        )

        assertFalse(launcher.launch(app(), Rect()))
        assertEquals(listOf("xspace:false"), events)
    }

    @Test
    fun cancellationStopsTheProfileFallbackChain() {
        val events = mutableListOf<String>()
        val launcher = launcher(
            events = events,
            freeform = {
                events += "freeform:cancel"
                throw CancellationException("newer launch requested")
            },
            normal = { events += "normal" },
            xspace = { events += "xspace"; true },
        )

        assertThrows(CancellationException::class.java) {
            launcher.launch(app(), Rect())
        }
        assertEquals(listOf("freeform:cancel"), events)
    }

    private fun launcher(
        events: MutableList<String>,
        isCurrentUser: Boolean = false,
        freeform: (LaunchableApp) -> Unit = { events += "freeform" },
        normal: (LaunchableApp) -> Unit = { events += "normal" },
        xspace: (LaunchableApp) -> Boolean = { events += "xspace"; true },
    ) = ProfileAppLauncher(
        isCurrentUser = { isCurrentUser },
        freeformStarter = { app, _ -> freeform(app) },
        normalStarter = normal,
        xSpaceFallback = { app, _ -> xspace(app) },
    )

    private fun app() = LaunchableApp(
        packageName = "com.example",
        label = "Example",
        icon = ColorDrawable(),
        launchIntent = Intent(),
    )
}
