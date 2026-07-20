package com.codex.edgeshelf.data

import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
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
        assertEquals(ShelfMode.RECENT, settings.mode)
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
    fun normalizeRecents_ordersNewestFirstAndKeepsOnlyTen() {
        val recents = (1L..12L).map { timestamp ->
            RecentEntry("app.$timestamp", timestamp)
        }

        assertEquals(
            listOf(
                "app.12",
                "app.11",
                "app.10",
                "app.9",
                "app.8",
                "app.7",
                "app.6",
                "app.5",
                "app.4",
                "app.3",
            ),
            normalizeRecents(recents).map(RecentEntry::packageName),
        )
    }

    @Test
    fun normalizeRecents_deduplicatesByNewestLaunchAndKeepsFavorites() {
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
                RecentEntry("app.favorite", 40L),
                RecentEntry("app.old", 30L),
                RecentEntry("app.other", 20L),
            ),
            normalizeRecents(recents),
        )
    }

    @Test
    fun decodeShelfMode_defaultsForMissingAndCorruptValues() {
        assertEquals(ShelfMode.RECENT, decodeShelfMode(null))
        assertEquals(ShelfMode.RECENT, decodeShelfMode(""))
        assertEquals(ShelfMode.RECENT, decodeShelfMode("BROKEN"))
        assertEquals(ShelfMode.RECENT, decodeShelfMode("recent"))
    }

    @Test
    fun decodeShelfMode_restoresStoredModes() {
        assertEquals(ShelfMode.RECENT, decodeShelfMode("RECENT"))
        assertEquals(ShelfMode.FIXED, decodeShelfMode("FIXED"))
    }

    @Test
    fun shelfMode_storageRoundTripsEveryMode() {
        ShelfMode.entries.forEach { mode ->
            assertEquals(mode, decodeShelfMode(encodeShelfMode(mode)))
        }
    }

    @Test
    fun toShelfSettings_missingModeMigratesToRecentAndPreservesFavorites() {
        val preferences = preferencesOf(
            stringPreferencesKey("favorites") to "app.first\napp.second",
        )

        val settings = toShelfSettings(preferences)

        assertEquals(ShelfMode.RECENT, settings.mode)
        assertEquals(listOf("app.first", "app.second"), settings.favorites)
    }

    @Test
    fun toShelfSettings_corruptModeFallsBackWithoutClearingFavorites() {
        val preferences = preferencesOf(
            stringPreferencesKey("shelf_mode") to "NOT_A_MODE",
            stringPreferencesKey("favorites") to "app.favorite",
        )

        val settings = toShelfSettings(preferences)

        assertEquals(ShelfMode.RECENT, settings.mode)
        assertEquals(listOf("app.favorite"), settings.favorites)
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
