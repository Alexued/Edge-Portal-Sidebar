package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppCatalogRepositoryTest {
    @Test
    fun repositoryExposesCurrentUserSerialWithCatalog() {
        val repository = AppCatalogRepository(
            AppCatalogSource {
                AppCatalogSnapshot(apps = emptyList(), currentUserSerial = 10L)
            },
        )

        assertEquals(10L, repository.loadCatalog().currentUserSerial)
        assertEquals(emptyList<LaunchableApp>(), repository.loadLaunchableApps())
    }

    @Test
    fun normalizeLaunchableInstances_preservesOwnerAndCloneOfSamePackage() {
        val result = normalizeLaunchableInstances(
            descriptors = listOf(
                descriptor("com.example", userSerial = 10L, component = "com.example/.Main", label = "Example"),
                descriptor("com.example", userSerial = 0L, component = "com.example/.Main", label = "Example"),
            ),
            selfPackage = "com.self",
        )

        assertEquals(listOf(0L, 10L), result.map(LaunchableInstanceDescriptor::userSerial))
    }

    @Test
    fun normalizeLaunchableInstances_selectsOneDeterministicLauncherPerProfile() {
        val result = normalizeLaunchableInstances(
            descriptors = listOf(
                descriptor("com.example", 0L, "com.example/.Secondary", "Zulu"),
                descriptor("com.example", 0L, "com.example/.Main", "Example"),
                descriptor("com.other", 0L, "com.other/.Main", "Alpha"),
            ),
            selfPackage = "com.self",
        )

        assertEquals(
            listOf("com.other/.Main", "com.example/.Main"),
            result.map(LaunchableInstanceDescriptor::componentName),
        )
    }

    @Test
    fun normalizeLaunchableInstances_filtersInvalidAndSelfEntries() {
        val result = normalizeLaunchableInstances(
            descriptors = listOf(
                descriptor("com.self", 0L, "com.self/.Main", "Self"),
                descriptor("", 0L, "invalid/.Main", "Invalid"),
                descriptor("com.invalid", -1L, "com.invalid/.Main", "Invalid"),
                descriptor("com.blank", 0L, "", "Blank"),
                descriptor(
                    "com.disabled",
                    0L,
                    "com.disabled/android.app.AppDetailsActivity",
                    "Disabled app details",
                ),
                descriptor(" com.valid ", 0L, " com.valid/.Main ", ""),
            ),
            selfPackage = "com.self",
        )

        assertEquals(
            listOf(descriptor("com.valid", 0L, "com.valid/.Main", "com.valid")),
            result,
        )
    }

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

    private fun descriptor(
        packageName: String,
        userSerial: Long,
        component: String,
        label: String,
    ) = LaunchableInstanceDescriptor(
        packageName = packageName,
        userSerial = userSerial,
        componentName = component,
        label = label,
    )
}
