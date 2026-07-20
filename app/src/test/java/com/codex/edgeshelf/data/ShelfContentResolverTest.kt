package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfContentResolverTest {
    @Test
    fun fixed_preservesFavoritesOrderFiltersCatalogAndIgnoresLimit() {
        val result = resolveShelfPackages(
            mode = ShelfMode.FIXED,
            favorites = listOf(" app.c ", "app.missing", "app.a", "app.c", "app.b"),
            systemRecents = listOf("app.b", "app.a"),
            localRecents = listOf(RecentEntry("app.a", 100L)),
            launchablePackages = setOf("app.a", "app.b", "app.c"),
            limit = 1,
        )

        assertEquals(listOf("app.c", "app.a", "app.b"), result)
    }

    @Test
    fun recent_prioritizesSystemThenFillsFromLocalHistory() {
        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = listOf("app.favorite"),
            systemRecents = listOf("app.system", "app.shared"),
            localRecents = listOf(
                RecentEntry("app.local", 30L),
                RecentEntry("app.shared", 20L),
                RecentEntry("app.favorite", 10L),
            ),
            launchablePackages = setOf("app.system", "app.shared", "app.local", "app.favorite"),
            limit = 4,
        )

        assertEquals(
            listOf("app.system", "app.shared", "app.local", "app.favorite"),
            result,
        )
    }

    @Test
    fun recent_withoutSystemAccessFallsBackToLocalHistoryInRecencyOrder() {
        val localHistory = listOf(
            RecentEntry("app.old", 10L),
            RecentEntry("app.newest", 50L),
            RecentEntry("app.duplicate", 20L),
            RecentEntry("app.duplicate", 40L),
            RecentEntry("app.uninstalled", 60L),
            RecentEntry("", 70L),
            RecentEntry("app.invalid", -1L),
        )

        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = emptyList(),
            localRecents = localHistory,
            launchablePackages = setOf("app.newest", "app.duplicate", "app.old"),
            limit = 6,
        )

        assertEquals(listOf("app.newest", "app.duplicate", "app.old"), result)
    }

    @Test
    fun recent_invalidAndDuplicateSystemEntriesDoNotConsumeSlotsNeededForLocalFallback() {
        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf(
                "app.system",
                "app.uninstalled",
                " app.system ",
                "",
            ),
            localRecents = listOf(
                RecentEntry("app.local.one", 30L),
                RecentEntry("app.local.two", 20L),
                RecentEntry("app.local.three", 10L),
            ),
            launchablePackages = setOf(
                "app.system",
                "app.local.one",
                "app.local.two",
                "app.local.three",
            ),
            limit = 3,
        )

        assertEquals(listOf("app.system", "app.local.one", "app.local.two"), result)
    }

    @Test
    fun recent_sharedPackageKeepsSystemPositionEvenWhenLocalTimestampIsNewest() {
        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf("app.system.first", "app.shared"),
            localRecents = listOf(
                RecentEntry("app.shared", 1_000L),
                RecentEntry("app.local", 900L),
            ),
            launchablePackages = setOf("app.system.first", "app.shared", "app.local"),
            limit = 3,
        )

        assertEquals(listOf("app.system.first", "app.shared", "app.local"), result)
    }

    @Test
    fun recent_includesAppsThatAreAlsoFixedFavorites() {
        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = listOf("app.favorite"),
            systemRecents = emptyList(),
            localRecents = listOf(
                RecentEntry("app.favorite", 20L),
                RecentEntry("app.other", 10L),
            ),
            launchablePackages = setOf("app.favorite", "app.other"),
            limit = 6,
        )

        assertEquals(listOf("app.favorite", "app.other"), result)
    }

    @Test
    fun recent_filtersNonCatalogPackagesAndNormalizesDuplicates() {
        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = listOf(" app.a ", "app.uninstalled", "app.a", "", "app.b"),
            localRecents = listOf(
                RecentEntry("app.c", 30L),
                RecentEntry("app.no.launcher", 40L),
            ),
            launchablePackages = setOf("app.a", "app.b", "app.c"),
            limit = 6,
        )

        assertEquals(listOf("app.a", "app.b", "app.c"), result)
    }

    @Test
    fun recent_appliesPhoneAndTabletLimits() {
        val packages = (1..12).map { index -> "app.$index" }

        assertEquals(
            packages.take(6),
            resolveShelfPackages(
                mode = ShelfMode.RECENT,
                favorites = emptyList(),
                systemRecents = packages,
                localRecents = emptyList(),
                launchablePackages = packages.toSet(),
                limit = 6,
            ),
        )
        assertEquals(
            packages.take(10),
            resolveShelfPackages(
                mode = ShelfMode.RECENT,
                favorites = emptyList(),
                systemRecents = packages,
                localRecents = emptyList(),
                launchablePackages = packages.toSet(),
                limit = 10,
            ),
        )
    }

    @Test
    fun recent_largeMixedInputRemainsDeterministicAndFillsTabletLimit() {
        val systemPackages = (1..500).flatMap { index ->
            listOf("missing.$index", " app.system.$index ", "app.system.$index")
        }
        val localHistory = (1..500).flatMap { index ->
            listOf(
                RecentEntry("app.local.$index", index.toLong()),
                RecentEntry("app.local.$index", (1_000 + index).toLong()),
            )
        }
        val launchablePackages = buildSet {
            addAll((1..4).map { index -> "app.system.$index" })
            addAll((1..20).map { index -> "app.local.$index" })
        }

        val result = resolveShelfPackages(
            mode = ShelfMode.RECENT,
            favorites = emptyList(),
            systemRecents = systemPackages,
            localRecents = localHistory,
            launchablePackages = launchablePackages,
            limit = 10,
        )

        assertEquals(
            listOf(
                "app.system.1",
                "app.system.2",
                "app.system.3",
                "app.system.4",
                "app.local.20",
                "app.local.19",
                "app.local.18",
                "app.local.17",
                "app.local.16",
                "app.local.15",
            ),
            result,
        )
    }

    @Test
    fun recent_emptyOrInvalidLimitFallsBackToEmptyLocalResult() {
        assertEquals(
            emptyList<String>(),
            resolveShelfPackages(
                mode = ShelfMode.RECENT,
                favorites = emptyList(),
                systemRecents = emptyList(),
                localRecents = emptyList(),
                launchablePackages = setOf("app.a"),
                limit = 6,
            ),
        )
        assertEquals(
            emptyList<String>(),
            resolveShelfPackages(
                mode = ShelfMode.RECENT,
                favorites = emptyList(),
                systemRecents = listOf("app.a"),
                localRecents = emptyList(),
                launchablePackages = setOf("app.a"),
                limit = 0,
            ),
        )
    }

    @Test
    fun recent_permissionMissingAndEmptySystemQueryHaveTheSameLocalFallback() {
        val localHistory = listOf(
            RecentEntry("app.local.new", 20L),
            RecentEntry("app.local.old", 10L),
        )
        val expected = listOf("app.local.new", "app.local.old")

        listOf(
            emptyList<String>(),
            emptySequence<String>().asIterable(),
        ).forEach { unavailableSystemHistory ->
            assertEquals(
                expected,
                resolveShelfPackages(
                    mode = ShelfMode.RECENT,
                    favorites = emptyList(),
                    systemRecents = unavailableSystemHistory,
                    localRecents = localHistory,
                    launchablePackages = expected.toSet(),
                    limit = 6,
                ),
            )
        }
    }
}
