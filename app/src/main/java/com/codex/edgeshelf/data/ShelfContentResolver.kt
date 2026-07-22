package com.codex.edgeshelf.data

/** Mode-specific content kept separate so the overlay can insert section rows. */
data class ShelfContent(
    val recentApps: List<LaunchableApp> = emptyList(),
    val allApps: List<LaunchableApp> = emptyList(),
    val fixedApps: List<LaunchableApp> = emptyList(),
)

internal data class ShelfContentKeys(
    val recentKeys: List<AppInstanceKey> = emptyList(),
    val allKeys: List<AppInstanceKey> = emptyList(),
    val fixedKeys: List<AppInstanceKey> = emptyList(),
)

/**
 * Resolves persisted instance keys against one profile-aware catalog snapshot.
 * The catalog is sorted here so the all-apps section remains deterministic
 * even when a source returns profiles in a different order.
 */
internal fun resolveShelfContent(
    mode: ShelfMode,
    favorites: Iterable<AppInstanceKey>,
    systemRecents: Iterable<String>,
    localRecents: Iterable<RecentEntry>,
    catalog: Iterable<LaunchableApp>,
    currentUserSerial: Long,
    recentLimit: Int = MAX_RECENTS,
): ShelfContent {
    val orderedCatalog = catalog
        .asSequence()
        .filter { app ->
            app.key.packageName.isNotBlank() &&
                app.key.userSerial >= 0L &&
                app.key.componentName.isNotBlank()
        }
        .sortedWith(
            compareBy<LaunchableApp, String>(String.CASE_INSENSITIVE_ORDER) { app -> app.label }
                .thenBy(LaunchableApp::packageName)
                .thenBy { app -> app.key.userSerial }
                .thenBy { app -> app.key.componentName },
        )
        .distinctBy(LaunchableApp::key)
        .toList()
    val catalogByKey = orderedCatalog.associateBy(LaunchableApp::key)
    val keys = resolveShelfInstanceKeys(
        mode = mode,
        favorites = favorites,
        systemRecents = systemRecents,
        localRecents = localRecents,
        launchableKeys = orderedCatalog.map(LaunchableApp::key),
        currentUserSerial = currentUserSerial,
        recentLimit = recentLimit,
    )
    return ShelfContent(
        recentApps = keys.recentKeys.mapNotNull(catalogByKey::get),
        allApps = keys.allKeys.mapNotNull(catalogByKey::get),
        fixedApps = keys.fixedKeys.mapNotNull(catalogByKey::get),
    )
}

/** Pure key-level resolver used by JVM tests and the Android object mapper. */
internal fun resolveShelfInstanceKeys(
    mode: ShelfMode,
    favorites: Iterable<AppInstanceKey>,
    systemRecents: Iterable<String>,
    localRecents: Iterable<RecentEntry>,
    launchableKeys: Iterable<AppInstanceKey>,
    currentUserSerial: Long,
    recentLimit: Int = MAX_RECENTS,
): ShelfContentKeys {
    val available = launchableKeys
        .asSequence()
        .map(AppInstanceKey::normalized)
        .filter { key ->
            key.packageName.isNotEmpty() &&
                key.userSerial >= 0L &&
                key.componentName.isNotEmpty()
        }
        .distinct()
        .toList()
    val exactKeys = available.toHashSet()
    val keysByPackageAndSerial = available.groupBy { key -> key.packageName to key.userSerial }

    fun bind(stored: AppInstanceKey): AppInstanceKey? {
        val normalized = stored.normalized()
        if (normalized in exactKeys) return normalized
        val serial = if (normalized.userSerial == LEGACY_USER_SERIAL) {
            currentUserSerial
        } else {
            normalized.userSerial
        }
        return keysByPackageAndSerial[normalized.packageName to serial]?.firstOrNull()
    }

    if (mode == ShelfMode.FIXED) {
        return ShelfContentKeys(
            fixedKeys = favorites
                .asSequence()
                .mapNotNull(::bind)
                .distinct()
                .toList(),
        )
    }

    val cappedLimit = recentLimit.coerceIn(0, MAX_RECENTS)
    val systemKeys = systemRecents
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { packageName ->
            keysByPackageAndSerial[packageName to currentUserSerial]?.firstOrNull()
        }
    val localKeys = normalizeRecents(localRecents, limit = Int.MAX_VALUE)
        .asSequence()
        .map(RecentEntry::instanceKey)
        .mapNotNull(::bind)
    val recentKeys = sequence {
        yieldAll(systemKeys)
        yieldAll(localKeys)
    }
        .distinct()
        .take(cappedLimit)
        .toList()
    val recentSet = recentKeys.toHashSet()

    return ShelfContentKeys(
        recentKeys = recentKeys,
        allKeys = available.filterNot(recentSet::contains),
    )
}

/**
 * Package-only compatibility helper retained for older unit-test callers.
 * Production content uses [resolveShelfInstanceKeys] and is never truncated to
 * the 6/10-row viewport size.
 */
internal fun resolveShelfPackages(
    mode: ShelfMode,
    favorites: Iterable<String>,
    systemRecents: Iterable<String>,
    localRecents: Iterable<RecentEntry>,
    launchablePackages: Set<String>,
    limit: Int,
): List<String> = when (mode) {
    ShelfMode.FIXED -> favorites
        .asSequence()
        .map(String::trim)
        .filter { packageName -> packageName.isNotEmpty() && packageName in launchablePackages }
        .distinct()
        .toList()

    ShelfMode.RECENT -> {
        if (limit <= 0) {
            emptyList()
        } else {
            sequence {
                yieldAll(systemRecents)
                yieldAll(
                    normalizeRecents(localRecents, limit = Int.MAX_VALUE)
                        .map(RecentEntry::packageName),
                )
            }
                .map(String::trim)
                .filter { packageName ->
                    packageName.isNotEmpty() && packageName in launchablePackages
                }
                .distinct()
                .take(limit)
                .toList()
        }
    }
}
