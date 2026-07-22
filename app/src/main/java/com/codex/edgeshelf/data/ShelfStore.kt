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
internal const val MAX_RECENTS = 40
private const val DEFAULT_VERTICAL_FRACTION = 0.5f
private const val ENTRY_SEPARATOR = '\t'
private const val RECENT_ENCODING_VERSION = "v1"

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
        dataStore.edit { preferences -> preferences[Keys.side] = side.name }
    }

    suspend fun setVerticalFraction(verticalFraction: Float) {
        dataStore.edit { preferences ->
            preferences[Keys.verticalFraction] = normalizeVerticalFraction(verticalFraction)
        }
    }

    suspend fun setMode(mode: ShelfMode) {
        dataStore.edit { preferences -> preferences[Keys.shelfMode] = encodeShelfMode(mode) }
    }

    suspend fun setFavorites(favorites: List<AppInstanceKey>) {
        dataStore.edit { preferences ->
            val normalizedFavorites = normalizeFavorites(favorites)
            preferences[Keys.favorites] = encodeFavorites(normalizedFavorites)
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.enabled] = enabled }
    }

    suspend fun setAutoStart(autoStart: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.autoStart] = autoStart }
    }

    suspend fun setAutoHide(autoHide: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.autoHide] = autoHide }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences -> preferences[Keys.onboardingCompleted] = completed }
    }

    suspend fun recordRecent(
        instanceKey: AppInstanceKey,
        lastLaunchedEpochMs: Long = System.currentTimeMillis(),
    ) {
        val normalizedKey = instanceKey.normalized()
        if (normalizedKey.packageName.isEmpty() ||
            normalizedKey.userSerial < LEGACY_USER_SERIAL ||
            lastLaunchedEpochMs < 0L
        ) return

        dataStore.edit { preferences ->
            val updated = decodeRecents(preferences[Keys.recents]) +
                RecentEntry(normalizedKey, lastLaunchedEpochMs)
            preferences[Keys.recents] = encodeRecents(updated)
        }
    }

    suspend fun clearRecents() {
        dataStore.edit { preferences -> preferences.remove(Keys.recents) }
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

/** Normalizes new keys and migrates package-only values from older releases. */
internal fun normalizeFavorites(
    favorites: Iterable<*>,
): List<AppInstanceKey> = favorites
    .asSequence()
    .mapNotNull { value ->
        when (value) {
            is AppInstanceKey -> value.normalized()
            is String -> decodeStoredAppInstanceKey(value)
            else -> null
        }
    }
    .filter { key ->
        key.packageName.isNotEmpty() &&
            key.userSerial >= LEGACY_USER_SERIAL
    }
    .distinct()
    .toList()

internal fun normalizeRecents(
    entries: Iterable<RecentEntry>,
    limit: Int = MAX_RECENTS,
): List<RecentEntry> {
    if (limit <= 0) return emptyList()

    return entries
        .asSequence()
        .map { entry ->
            val key = entry.instanceKey.normalized()
            entry.copy(instanceKey = key)
        }
        .filter { entry ->
            entry.instanceKey.packageName.isNotEmpty() &&
                entry.instanceKey.userSerial >= LEGACY_USER_SERIAL &&
                entry.lastLaunchedEpochMs >= 0L
        }
        .groupBy(RecentEntry::instanceKey)
        .map { (_, duplicates) -> duplicates.maxBy(RecentEntry::lastLaunchedEpochMs) }
        .sortedWith(
            compareByDescending<RecentEntry>(RecentEntry::lastLaunchedEpochMs)
                .thenBy { it.instanceKey.stableId },
        )
        .take(limit)
}

internal fun decodeFavorites(
    encoded: String?,
): List<AppInstanceKey> = normalizeFavorites(
    encoded.orEmpty().lineSequence().asIterable(),
)

internal fun decodeShelfMode(encoded: String?): ShelfMode = encoded
    ?.trim()
    ?.let { storedMode -> ShelfMode.entries.firstOrNull { it.name == storedMode } }
    ?: ShelfMode.RECENT

internal fun encodeShelfMode(mode: ShelfMode): String = mode.name

internal fun decodeRecents(
    encoded: String?,
): List<RecentEntry> = encoded.orEmpty()
    .lineSequence()
    .mapNotNull { line ->
        val normalizedLine = line.replace("\\t", ENTRY_SEPARATOR.toString())
        val fields = normalizedLine.split(ENTRY_SEPARATOR, limit = 3)
        if (fields.size >= 3 && fields[0] == RECENT_ENCODING_VERSION) {
            val timestamp = fields[1].toLongOrNull() ?: return@mapNotNull null
            val key = decodeStoredAppInstanceKey(fields[2])
                ?: return@mapNotNull null
            return@mapNotNull RecentEntry(key, timestamp)
        }

        // Pre-1.3 format: <timestamp>\t<packageName>.
        if (fields.size != 2) return@mapNotNull null
        val timestamp = fields[0].toLongOrNull() ?: return@mapNotNull null
        val key = decodeStoredAppInstanceKey(fields[1])
            ?: return@mapNotNull null
        RecentEntry(key, timestamp)
    }
    .asIterable()
    .let(::normalizeRecents)

internal fun encodeFavorites(favorites: Iterable<AppInstanceKey>): String = normalizeFavorites(favorites)
    .joinToString(separator = "\n", transform = AppInstanceKey::encode)

internal fun encodeRecents(entries: Iterable<RecentEntry>): String = normalizeRecents(entries)
    .joinToString(separator = "\n") { entry ->
        "$RECENT_ENCODING_VERSION$ENTRY_SEPARATOR${entry.lastLaunchedEpochMs}$ENTRY_SEPARATOR" +
            entry.instanceKey.encode()
    }

internal fun normalizeVerticalFraction(verticalFraction: Float): Float =
    if (verticalFraction.isFinite()) {
        verticalFraction.coerceIn(0f, 1f)
    } else {
        DEFAULT_VERTICAL_FRACTION
    }
