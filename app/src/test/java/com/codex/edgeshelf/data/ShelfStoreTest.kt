package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfStoreTest {
    @Test
    fun shelfSettings_defaultsAreSafe() {
        val settings = ShelfSettings()

        assertEquals(ShelfSide.RIGHT, settings.side)
        assertEquals(0.5f, settings.verticalFraction)
        assertTrue(settings.favorites.isEmpty())
        assertTrue(settings.recents.isEmpty())
        assertFalse(settings.enabled)
        assertFalse(settings.autoStart)
        assertTrue(settings.autoHide)
        assertFalse(settings.onboardingCompleted)
    }

    @Test
    fun normalizeFavorites_preservesOrderAndRemovesInvalidDuplicates() {
        val favorites = normalizeFavorites(
            listOf(" app.first ", "", "app.second", "app.first", "   "),
        )

        assertEquals(listOf("app.first", "app.second"), favorites)
    }

    @Test
    fun normalizeRecents_ordersNewestFirstAndKeepsOnlySix() {
        val recents = (1L..8L).map { timestamp ->
            RecentEntry("app.$timestamp", timestamp)
        }

        assertEquals(
            listOf("app.8", "app.7", "app.6", "app.5", "app.4", "app.3"),
            normalizeRecents(recents).map(RecentEntry::packageName),
        )
    }

    @Test
    fun normalizeRecents_deduplicatesByNewestLaunchAndExcludesFavorites() {
        val recents = listOf(
            RecentEntry("app.old", 10L),
            RecentEntry("app.favorite", 40L),
            RecentEntry("app.old", 30L),
            RecentEntry(" app.other ", 20L),
            RecentEntry("", 50L),
            RecentEntry("app.invalid", -1L),
        )

        assertEquals(
            listOf(
                RecentEntry("app.old", 30L),
                RecentEntry("app.other", 20L),
            ),
            normalizeRecents(recents, favorites = listOf("app.favorite")),
        )
    }

    @Test
    fun decodeRecents_ignoresMalformedEntries() {
        val encoded = """
            not-a-recent
            bad-time\tapp.bad
            -1\tapp.negative
            30\tapp.valid
            20\t
        """.trimIndent()

        assertEquals(
            listOf(RecentEntry("app.valid", 30L)),
            decodeRecents(encoded),
        )
    }

    @Test
    fun normalizeVerticalFraction_clampsAndFallsBackForNonFiniteValues() {
        assertEquals(0f, normalizeVerticalFraction(-0.2f))
        assertEquals(1f, normalizeVerticalFraction(1.2f))
        assertEquals(0.5f, normalizeVerticalFraction(Float.NaN))
        assertEquals(0.5f, normalizeVerticalFraction(Float.POSITIVE_INFINITY))
    }
}
