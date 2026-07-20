package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogRepositoryTest {
    @Test
    fun normalizeLaunchablePackages_filtersSelfAndSortsDeterministically() {
        val result = normalizeLaunchablePackages(
            listOf("com.z" to "Zulu", "com.self" to "Self", " com.a " to "Alpha", "com.z" to "Duplicate"),
            selfPackage = "com.self",
        )

        assertEquals(listOf("com.a" to "Alpha", "com.z" to "Zulu"), result)
    }

    @Test
    fun normalizeRecentPackages_filtersSelfAndLimits() {
        val result = normalizeRecentPackages(
            listOf(" com.a ", "com.self", "com.a", "com.b", "com.c"),
            selfPackage = "com.self",
            limit = 2,
        )

        assertEquals(listOf("com.a", "com.b"), result)
    }
}
