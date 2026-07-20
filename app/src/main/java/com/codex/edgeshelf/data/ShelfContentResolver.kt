package com.codex.edgeshelf.data

internal fun resolveShelfContent(
    mode: ShelfMode,
    favorites: Iterable<String>,
    systemRecents: Iterable<String>,
    localRecents: Iterable<RecentEntry>,
    catalog: Iterable<LaunchableApp>,
    limit: Int,
): List<LaunchableApp> {
    val catalogByPackage = catalog
        .associateBy(LaunchableApp::packageName)
    return resolveShelfPackages(
        mode = mode,
        favorites = favorites,
        systemRecents = systemRecents,
        localRecents = localRecents,
        launchablePackages = catalogByPackage.keys,
        limit = limit,
    ).mapNotNull(catalogByPackage::get)
}

internal fun resolveShelfPackages(
    mode: ShelfMode,
    favorites: Iterable<String>,
    systemRecents: Iterable<String>,
    localRecents: Iterable<RecentEntry>,
    launchablePackages: Set<String>,
    limit: Int,
): List<String> = when (mode) {
    ShelfMode.FIXED -> normalizeFavorites(favorites)
        .filter(launchablePackages::contains)

    ShelfMode.RECENT -> {
        if (limit <= 0) {
            emptyList()
        } else {
            sequence {
                yieldAll(systemRecents)
                yieldAll(normalizeRecents(localRecents, limit = Int.MAX_VALUE).map(RecentEntry::packageName))
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
