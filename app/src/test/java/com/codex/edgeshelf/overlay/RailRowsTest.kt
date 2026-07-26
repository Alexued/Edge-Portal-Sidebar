package com.codex.edgeshelf.overlay

import android.content.ComponentName
import android.content.Intent
import com.codex.edgeshelf.data.AppInstanceKey
import com.codex.edgeshelf.data.LaunchableApp
import com.codex.edgeshelf.data.ShelfMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RailRowsTest {
    @Test
    fun fixedModeAlwaysKeepsTheAddRow() {
        val rows = buildRailRows(
            mode = ShelfMode.FIXED,
            contentLoaded = false,
        )

        assertEquals(listOf(AddRow), rows)
    }

    @Test
    fun recentModeRepresentsLoadingAndTrueEmptyStatesExplicitly() {
        assertEquals(
            listOf(LoadingRow),
            buildRailRows(mode = ShelfMode.RECENT, contentLoaded = false),
        )
        assertEquals(
            listOf(EmptyRow),
            buildRailRows(mode = ShelfMode.RECENT, contentLoaded = true),
        )
    }

    @Test
    fun sectionRowsCarryTheLocalizedAllAppsTitle() {
        assertEquals(ALL_APPS_SECTION_TITLE, SectionRow(ALL_APPS_SECTION_TITLE).title)
    }

    @Test
    fun interactionIdentityDistinguishesOwnerAndCloneOfTheSamePackage() {
        val owner = app("com.shared", userSerial = 0L)
        val clone = app("com.shared", userSerial = 10L)

        assertEquals(false, AppRow(owner).interactionIdentity() == AppRow(clone).interactionIdentity())
        assertEquals("add", AddRow.interactionIdentity())
    }

    @Test
    fun headerOrder_isToolsThenAtMostThreePinnedInstances() {
        val owner = app("com.shared", userSerial = 0L)
        val clone = app("com.shared", userSerial = 10L)
        val third = app("com.third", userSerial = 0L)
        val fourth = app("com.fourth", userSerial = 0L)

        assertEquals(
            listOf(
                RecordingToolItem,
                ScreenshotToolItem,
                MainAppToolItem,
                PinnedAppItem(owner),
                PinnedAppItem(clone),
                PinnedAppItem(third),
            ),
            buildRailHeaderItems(
                recordingEnabled = true,
                pinnedApps = listOf(owner, clone, owner, third, fourth),
            ),
        )
    }

    @Test
    fun disablingRecording_removesItsSlotWithoutRemovingAlwaysAvailableTools() {
        assertEquals(
            listOf(ScreenshotToolItem, MainAppToolItem),
            buildRailHeaderItems(recordingEnabled = false, pinnedApps = emptyList()),
        )
    }

    @Test
    fun mainAppTool_hasStableInteractionIdentity() {
        assertEquals("tool:main-app", MainAppToolItem.interactionIdentity())
    }

    private fun app(packageName: String, userSerial: Long): LaunchableApp {
        val component = ComponentName(packageName, "$packageName.MainActivity")
        return LaunchableApp(
            key = AppInstanceKey(packageName, userSerial, "$packageName/$packageName.MainActivity"),
            label = packageName,
            icon = null,
            componentName = component,
            userHandle = null,
            launchIntent = Intent(),
        )
    }
}
