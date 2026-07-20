package com.codex.edgeshelf.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val DATA_STORE_NAME = "shelf_settings"
private const val MAX_RECENTS = 10
private const val DEFAULT_VERTICAL_FRACTION = 0.5f
private const val ENTRY_SEPARATOR = '\t'

private val Context.shelfDataStore by preferencesDataStore(name = DATA_STORE_NAME)

private object Keys {
    val side = stringPreferencesKey("side")
    val verticalFraction = floatPreferencesKey("vertical_fraction")
    val shelfMode = stringPreferencesKey("shelf_mode")
    val favorites = stringPreferencesKey("favorites")
    val recents = stringPreferencesKey("recents")
    val enabled = booleanPreferencesKey("enabled")
    val autoStart = booleanPreferencesKey("auto_start")
    val autoHide = booleanPreferencesKey("auto_hide")
    val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
}

class ShelfStore(context: Context) {
    private val dataStore = context.applicationContext.shelfDataStore

    val settings: Flow<ShelfSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(::toShelfSettings)

    suspend fun setSide(side: ShelfSide) {
        dataStore.edit { preferences ->
            preferences[Keys.side] = side.name
        }
    }

    suspend fun setVerticalFraction(verticalFraction: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.verticalFraction] = normalizeVerticalFraction(verticalFraction)
        }
    }

    suspend fun setMode(mode: ShelfMode) {
        dataStore.edit { preferences ->
            preferences[Keys.shelfMode] = encodeShelfMode(mode)
        }
    }

    suspend fun setFavorites(favorites: List<String>) {
        dataStore.edit { preferences ->
            val normalizedFavorites = normalizeFavorites(favorites)
            preferences[Keys.favorites] = encodeFavorites(normalizedFavorites)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.enabled] = enabled
        }
    }

    suspend fun setAutoStart(autoStart: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.autoStart] = autoStart
        }
    }

    suspend fun setAutoHide(autoHide: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.autoHide] = autoHide
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.onboardingCompleted] = completed
        }
    }

    suspend fun recordRecent(
        packageName: String,
        lastLaunchedEpochMs: Long = System.currentTimeMillis(),
    ) {
        val normalizedPackageName = packageName.trim()
        if (normalizedPackageName.isEmpty() || lastLaunchedEpochMs < 0L) return

        dataStore.edit { preferences ->
            val updated = decodeRecents(preferences[Keys.recents]) +
                RecentEntry(normalizedPackageName, lastLaunchedEpochMs)
            preferences[Keys.recents] = encodeRecents(updated)
        }
    }

    suspend fun clearRecents() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.recents)
        }
    }

}

internal fun toShelfSettings(preferences: Preferences): ShelfSettings {
    val favorites = decodeFavorites(preferences[Keys.favorites])
    return ShelfSettings(
        side = preferences[Keys.side]
            ?.let { storedSide -> ShelfSide.entries.firstOrNull { it.name == storedSide } }
            ?: ShelfSide.RIGHT,
        verticalFraction = normalizeVerticalFraction(
            preferences[Keys.verticalFraction] ?: DEFAULT_VERTICAL_FRACTION,
        ),
        mode = decodeShelfMode(preferences[Keys.shelfMode]),
        favorites = favorites,
        recents = normalizeRecents(decodeRecents(preferences[Keys.recents])),
        enabled = preferences[Keys.enabled] ?: false,
        autoStart = preferences[Keys.autoStart] ?: false,
        autoHide = preferences[Keys.autoHide] ?: true,
        onboardingCompleted = preferences[Keys.onboardingCompleted] ?: false,
    )
}

internal fun normalizeFavorites(favorites: Iterable<String>): List<String> =
    favorites
        .map { packageName -> packageName.trim() }
        .filter { packageName -> packageName.isNotEmpty() }
        .distinct()

internal fun normalizeRecents(
    entries: Iterable<RecentEntry>,
    limit: Int = MAX_RECENTS,
): List<RecentEntry> {
    if (limit <= 0) return emptyList()

    return entries
        .asSequence()
        .map { entry -> entry.copy(packageName = entry.packageName.trim()) }
        .filter { entry ->
            entry.packageName.isNotEmpty() &&
                entry.lastLaunchedEpochMs >= 0L
        }
        .groupBy(RecentEntry::packageName)
        .map { (_, duplicates) -> duplicates.maxBy(RecentEntry::lastLaunchedEpochMs) }
        .sortedWith(
            compareByDescending<RecentEntry>(RecentEntry::lastLaunchedEpochMs)
                .thenBy(RecentEntry::packageName),
        )
        .take(limit)
}

internal fun decodeFavorites(encoded: String?): List<String> =
    normalizeFavorites(encoded.orEmpty().lineSequence().asIterable())

internal fun decodeShelfMode(encoded: String?): ShelfMode =
    encoded
        ?.trim()
        ?.let { storedMode -> ShelfMode.entries.firstOrNull { it.name == storedMode } }
        ?: ShelfMode.RECENT

internal fun encodeShelfMode(mode: ShelfMode): String = mode.name

internal fun decodeRecents(encoded: String?): List<RecentEntry> =
    encoded.orEmpty()
        .lineSequence()
        .mapNotNull { line ->
            val normalizedLine = line.replace("\\t", ENTRY_SEPARATOR.toString())
            val separatorIndex = normalizedLine.indexOf(ENTRY_SEPARATOR)
            if (separatorIndex <= 0 || separatorIndex == normalizedLine.lastIndex) return@mapNotNull null

            val timestamp = normalizedLine.substring(0, separatorIndex).toLongOrNull()
                ?: return@mapNotNull null
            RecentEntry(
                packageName = normalizedLine.substring(separatorIndex + 1),
                lastLaunchedEpochMs = timestamp,
            )
        }
        .asIterable()
        .let(::normalizeRecents)

private fun encodeFavorites(favorites: Iterable<String>): String =
    normalizeFavorites(favorites).joinToString(separator = "\n")

private fun encodeRecents(
    entries: Iterable<RecentEntry>,
): String = normalizeRecents(entries).joinToString(separator = "\n") { entry ->
        "${entry.lastLaunchedEpochMs}$ENTRY_SEPARATOR${entry.packageName}"
    }

internal fun normalizeVerticalFraction(verticalFraction: Float): Float =
    if (verticalFraction.isFinite()) {
        verticalFraction.coerceIn(0f, 1f)
    } else {
        DEFAULT_VERTICAL_FRACTION
    }
