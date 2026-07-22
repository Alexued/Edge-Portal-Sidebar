package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfContentResolverTest {
    @Test
    fun fixed_preservesInstanceOrderAndLetsOwnerAndCloneBeSelectedIndependently() {
        val owner = key("app.shared", 0L)
        val clone = key("app.shared", 10L)
        val other = key("app.other", 0L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.FIXED,
            favorites = listOf(clone, owner, clone, key("app.missing", 0L), other),
            systemRecents = listOf("app.other"),
            localRecents = emptyList(),
            launchableKeys = listOf(owner, clone, other),
            currentUserSerial = 0L,
        )

        assertEquals(listOf(clone, owner, other), result.fixedKeys)
        assertTrue(result.recentKeys.isEmpty())
        assertTrue(result.allKeys.isEmpty())
    }

    @Test
    fun fixed_rebindsLauncherComponentChangesWithinSameProfile() {
        val old = key("app.updated", 10L, ".OldMain")
        val updated = key("app.updated", 10L, ".NewMain")

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.FIXED,
            favorites = listOf(old),
            systemRecents = emptyList(),
            localRecents = emptyList(),
            launchableKeys = listOf(key("app.updated", 0L), updated),
            currentUserSerial = 0L,
        )

        assertEquals(listOf(updated), result.fixedKeys)
    }

    @Test
    fun fixed_legacyPackageBindsOnlyToCurrentUser() {
        val owner = key("app.shared", 7L)
        val clone = key("app.shared", 10L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.FIXED,
            favorites = listOf(AppInstanceKey.legacy("app.shared")),
            systemRecents = emptyList(),
            localRecents = emptyList(),
            launchableKeys = listOf(clone, owner),
            currentUserSerial = 7L,
        )

        assertEquals(listOf(owner), result.fixedKeys)
    }

    @Test
    fun recent_prioritizesOwnerUsageThenAddsInstanceHistory() {
        val systemOwner = key("app.system", 0L)
        val sharedOwner = key("app.shared", 0L)
        val sharedClone = key("app.shared", 10L)
        val localClone = key("app.local", 10L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf("app.system", "app.shared"),
            localRecents = listOf(
                RecentEntry(sharedClone, 1_000L),
                RecentEntry(localClone, 900L),
                RecentEntry(sharedOwner, 800L),
            ),
            launchableKeys = listOf(systemOwner, sharedOwner, sharedClone, localClone),
            currentUserSerial = 0L,
        )

        assertEquals(
            listOf(systemOwner, sharedOwner, sharedClone, localClone),
            result.recentKeys,
        )
    }

    @Test
    fun recent_usageNeverFallsAcrossProfiles() {
        val cloneOnly = key("app.clone-only", 10L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf("app.clone-only"),
            localRecents = emptyList(),
            launchableKeys = listOf(cloneOnly),
            currentUserSerial = 0L,
        )

        assertTrue(result.recentKeys.isEmpty())
        assertEquals(listOf(cloneOnly), result.allKeys)
    }

    @Test
    fun allApps_excludesOnlyTheExactRecentInstance() {
        val owner = key("app.shared", 0L)
        val clone = key("app.shared", 10L)
        val other = key("app.other", 0L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf("app.shared"),
            localRecents = emptyList(),
            launchableKeys = listOf(owner, clone, other),
            currentUserSerial = 0L,
        )

        assertEquals(listOf(owner), result.recentKeys)
        assertEquals(listOf(clone, other), result.allKeys)
    }

    @Test
    fun emptyRecentHistoryStartsWithCompleteAllAppsSection() {
        val catalog = listOf(key("app.alpha", 0L), key("app.beta", 10L))

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = emptyList(),
            localRecents = emptyList(),
            launchableKeys = catalog,
            currentUserSerial = 0L,
        )

        assertTrue(result.recentKeys.isEmpty())
        assertEquals(catalog, result.allKeys)
    }

    @Test
    fun recent_keepsFortyInsteadOfViewportRowLimit() {
        val catalog = (1..50).map { index -> key("app.$index", 0L) }

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = catalog.map(AppInstanceKey::packageName),
            localRecents = emptyList(),
            launchableKeys = catalog,
            currentUserSerial = 0L,
            recentLimit = 80,
        )

        assertEquals(catalog.take(40), result.recentKeys)
        assertEquals(catalog.drop(40), result.allKeys)
    }

    @Test
    fun missingUsageAccessFallsBackToLocalRecencyOrder() {
        val newest = key("app.newest", 10L)
        val older = key("app.older", 0L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = emptyList(),
            localRecents = listOf(RecentEntry(older, 10L), RecentEntry(newest, 20L)),
            launchableKeys = listOf(newest, older),
            currentUserSerial = 0L,
        )

        assertEquals(listOf(newest, older), result.recentKeys)
    }

    @Test
    fun unavailableProfileIsFilteredWithoutDeletingOtherInstances() {
        val owner = key("app.shared", 0L)
        val unavailableClone = key("app.shared", 10L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = emptyList(),
            localRecents = listOf(RecentEntry(unavailableClone, 20L), RecentEntry(owner, 10L)),
            launchableKeys = listOf(owner),
            currentUserSerial = 0L,
        )

        assertEquals(listOf(owner), result.recentKeys)
    }

    @Test
    fun oldPackageRecentBindsToCurrentUserInstance() {
        val owner = key("app.old", 7L)
        val clone = key("app.old", 10L)

        val result = resolveShelfInstanceKeys(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = emptyList(),
            localRecents = listOf(RecentEntry("app.old", 10L)),
            launchableKeys = listOf(clone, owner),
            currentUserSerial = 7L,
        )

        assertEquals(listOf(owner), result.recentKeys)
    }

    private fun key(
        packageName: String,
        serial: Long,
        component: String = ".Main",
    ): AppInstanceKey = AppInstanceKey(
        packageName = packageName,
        userSerial = serial,
        componentName = "$packageName/$component",
    )
}
