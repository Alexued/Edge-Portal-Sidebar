package com.codex.edgeshelf.data

import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Stable identity for one launchable application instance.
 *
 * Package names are not unique on devices that expose work, clone, or XSpace
 * profiles.  The profile is represented by its user serial (which is stable
 * across process restarts), while the launcher component disambiguates apps
 * that publish more than one MAIN/LAUNCHER activity.
 */
data class AppInstanceKey(
    val packageName: String,
    val userSerial: Long,
    val componentName: String,
) {
    val isLegacy: Boolean
        get() = userSerial == LEGACY_USER_SERIAL

    val needsComponentRebind: Boolean
        get() = componentName.isBlank()

    /** A deterministic, human-independent id useful for collection keys. */
    val stableId: String
        get() = encode()

    /** Versioned, delimiter-safe representation used by DataStore. */
    fun encode(): String = encodeAppInstanceKey(this)

    fun normalized(): AppInstanceKey = copy(
        packageName = packageName.trim(),
        componentName = componentName.trim(),
    )

    companion object {
        /** A package-only entry read from pre-1.3 settings. */
        fun legacy(packageName: String): AppInstanceKey = AppInstanceKey(
            packageName = packageName.trim(),
            userSerial = LEGACY_USER_SERIAL,
            componentName = "",
        )
    }
}

/** Serial values returned by Android are non-negative; -1 is only a migration marker. */
const val LEGACY_USER_SERIAL: Long = -1L

private const val APP_INSTANCE_ENCODING_VERSION = "v1"
private const val APP_INSTANCE_SEPARATOR = '|'

/**
 * Encodes all fields independently so package/component names may contain any
 * delimiter without changing the on-disk format.
 */
fun encodeAppInstanceKey(key: AppInstanceKey): String {
    val normalized = key.normalized()
    return listOf(
        APP_INSTANCE_ENCODING_VERSION,
        normalized.userSerial.toString(),
        encodePart(normalized.packageName),
        encodePart(normalized.componentName),
    ).joinToString(APP_INSTANCE_SEPARATOR.toString())
}

/**
 * Decodes only the new versioned representation.  Use [decodeStoredAppInstanceKey]
 * for DataStore values where legacy package-only lines are expected.
 */
fun decodeAppInstanceKey(encoded: String?): AppInstanceKey? {
    val value = encoded?.trim().orEmpty()
    if (value.isEmpty()) return null
    val parts = value.split(APP_INSTANCE_SEPARATOR, limit = 4)
    if (parts.size != 4 || parts[0] != APP_INSTANCE_ENCODING_VERSION) return null
    val serial = parts[1].toLongOrNull() ?: return null
    val packageName = decodePart(parts[2])?.trim().orEmpty()
    val componentName = decodePart(parts[3])?.trim().orEmpty()
    if (packageName.isEmpty() || serial < LEGACY_USER_SERIAL) return null
    return AppInstanceKey(packageName, serial, componentName)
}

/**
 * Reads either a versioned instance key or an old package-only value.  Legacy
 * entries intentionally remain unresolved until a live launcher catalog is
 * available; this prevents guessing that a package belongs to XSpace.
 */
fun decodeStoredAppInstanceKey(
    encoded: String?,
): AppInstanceKey? {
    val value = encoded?.trim().orEmpty()
    if (value.isEmpty()) return null
    decodeAppInstanceKey(value)?.let { return it }
    if (value.contains(APP_INSTANCE_SEPARATOR)) return null
    val packageName = value.trim()
    if (packageName.isEmpty()) return null
    return AppInstanceKey.legacy(packageName)
}

/**
 * Rebinds persisted keys to currently enumerated launcher components.
 *
 * Exact identity wins.  If an app's launcher component changed after an
 * upgrade, a package+serial match is used.  Package-only legacy entries are
 * deliberately restricted to the current user's serial.
 */
fun rebindAppInstanceKey(
    stored: AppInstanceKey,
    available: Iterable<AppInstanceKey>,
    currentUserSerial: Long = 0L,
): AppInstanceKey? {
    val candidates = available.asSequence()
        .map(AppInstanceKey::normalized)
        .filter { it.packageName.isNotEmpty() }
        .toList()
    candidates.firstOrNull { it == stored.normalized() }?.let { return it }

    val normalized = stored.normalized()
    val expectedSerial = if (normalized.userSerial == LEGACY_USER_SERIAL) {
        currentUserSerial
    } else {
        normalized.userSerial
    }
    if (expectedSerial < 0L) return null
    return candidates
        .asSequence()
        .filter { it.packageName == normalized.packageName && it.userSerial == expectedSerial }
        .sortedWith(compareBy<AppInstanceKey> { it.componentName.isBlank() }.thenBy { it.componentName })
        .firstOrNull()
}

private fun encodePart(value: String): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

private fun decodePart(value: String): String? = runCatching {
    String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}.getOrNull()
