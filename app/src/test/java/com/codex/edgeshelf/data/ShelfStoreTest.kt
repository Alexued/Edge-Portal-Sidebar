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
    fun normalizeFavorites_preservesOrderAndFullInstanceIdentity() {
        val owner = key("app.first", 0L)
        val clone = key("app.first", 10L)
        val favorites = normalizeFavorites(
            listOf(owner, clone, owner, AppInstanceKey("", 0L, "invalid"), "   "),
        )

        assertEquals(listOf(owner, clone), favorites)
    }

    @Test
    fun normalizeFavorites_migratesOldPackagesWithoutAssumingOwnerSerial() {
        assertEquals(
            listOf(AppInstanceKey.legacy("app.first"), AppInstanceKey.legacy("app.second")),
            normalizeFavorites(listOf(" app.first ", "app.second", "app.first")),
        )
    }

    @Test
    fun normalizeRecents_ordersNewestFirstAndKeepsForty() {
        val recents = (1L..45L).map { timestamp ->
            RecentEntry(key("app.$timestamp", 0L), timestamp)
        }

        assertEquals(
            (45L downTo 6L).map { timestamp -> "app.$timestamp" },
            normalizeRecents(recents).map(RecentEntry::packageName),
        )
    }

    @Test
    fun normalizeRecents_deduplicatesByInstanceButKeepsClone() {
        val owner = key("app.shared", 0L)
        val clone = key("app.shared", 10L)
        val entries = listOf(
            RecentEntry(owner, 10L),
            RecentEntry(clone, 40L),
            RecentEntry(owner, 30L),
            RecentEntry(AppInstanceKey("", 0L, "invalid"), 50L),
            RecentEntry(key("app.invalid", 0L), -1L),
        )

        assertEquals(
            listOf(RecentEntry(clone, 40L), RecentEntry(owner, 30L)),
            normalizeRecents(entries),
        )
    }

    @Test
    fun normalizeRecents_zeroLimitReturnsEmpty() {
        assertTrue(normalizeRecents(listOf(RecentEntry(key("app.a", 0L), 1L)), limit = 0).isEmpty())
    }

    @Test
    fun favoritesEncoding_roundTripsVersionedKeys() {
        val favorites = listOf(key("app.owner", 0L), key("app.clone", 10L))

        assertEquals(favorites, decodeFavorites(encodeFavorites(favorites)))
    }

    @Test
    fun recentsEncoding_roundTripsVersionedKeys() {
        val entries = listOf(
            RecentEntry(key("app.owner", 0L), 30L),
            RecentEntry(key("app.clone", 10L), 20L),
        )

        assertEquals(entries, decodeRecents(encodeRecents(entries)))
    }

    @Test
    fun decodeShelfMode_defaultsForMissingAndCorruptValues() {
        assertEquals(ShelfMode.RECENT, decodeShelfMode(null))
        assertEquals(ShelfMode.RECENT, decodeShelfMode(""))
        assertEquals(ShelfMode.RECENT, decodeShelfMode("BROKEN"))
        assertEquals(ShelfMode.RECENT, decodeShelfMode("recent"))
    }

    @Test
    fun shelfMode_storageRoundTripsEveryMode() {
        ShelfMode.entries.forEach { mode ->
            assertEquals(mode, decodeShelfMode(encodeShelfMode(mode)))
        }
    }

    @Test
    fun toShelfSettings_oldDataMigratesWithoutGuessingCloneProfile() {
        val preferences = preferencesOf(
            stringPreferencesKey("favorites") to "app.first\napp.second",
            stringPreferencesKey("recents") to "30\\tapp.first",
        )

        val settings = toShelfSettings(preferences)

        assertEquals(ShelfMode.RECENT, settings.mode)
        assertEquals(
            listOf(AppInstanceKey.legacy("app.first"), AppInstanceKey.legacy("app.second")),
            settings.favorites,
        )
        assertEquals(
            listOf(RecentEntry(AppInstanceKey.legacy("app.first"), 30L)),
            settings.recents,
        )
    }

    @Test
    fun toShelfSettings_corruptModeFallsBackWithoutClearingVersionedFavorites() {
        val favorite = key("app.favorite", 10L)
        val preferences = preferencesOf(
            stringPreferencesKey("shelf_mode") to "NOT_A_MODE",
            stringPreferencesKey("favorites") to encodeFavorites(listOf(favorite)),
        )

        val settings = toShelfSettings(preferences)

        assertEquals(ShelfMode.RECENT, settings.mode)
        assertEquals(listOf(favorite), settings.favorites)
    }

    @Test
    fun decodeRecents_ignoresMalformedEntriesAndReadsOldRows() {
        val encoded = """
            not-a-recent
            bad-time\tapp.bad
            -1\tapp.negative
            30\tapp.valid
            20\t
        """.trimIndent()

        assertEquals(
            listOf(RecentEntry(AppInstanceKey.legacy("app.valid"), 30L)),
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

    private fun key(packageName: String, serial: Long): AppInstanceKey = AppInstanceKey(
        packageName = packageName,
        userSerial = serial,
        componentName = "$packageName/.Main",
    )
}
