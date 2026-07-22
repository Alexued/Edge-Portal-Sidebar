package com.codex.edgeshelf.ui

import com.codex.edgeshelf.data.AppInstanceKey
import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeShelfViewModelTest {
    @Test
    fun mergeFavoriteSelection_preservesExistingOrderAndAppendsNewApps() {
        val appA = key("app.a")
        val appB = key("app.b")
        val appC = key("app.c")
        val appD = key("app.d")
        val missing = key("missing.app")
        val result = mergeFavoriteSelection(
            existing = listOf(appC, appA, missing),
            catalogOrder = listOf(appA, appB, appC, appD),
            selected = setOf(appA, appB, appC, missing),
        )

        assertEquals(listOf(appC, appA, missing, appB), result)
    }

    @Test
    fun mergeFavoriteSelection_allowsClearingTheShelf() {
        val appA = key("app.a")
        val appB = key("app.b")
        val result = mergeFavoriteSelection(
            existing = listOf(appA, appB),
            catalogOrder = listOf(appA, appB),
            selected = emptySet(),
        )

        assertEquals(emptyList<AppInstanceKey>(), result)
    }

    @Test
    fun mergeFavoriteSelection_keepsSelectedFavoritesWhenCatalogIsPartial() {
        val profileApp = key("work.profile.app", userSerial = 10L)
        val appA = key("app.a")
        val result = mergeFavoriteSelection(
            existing = listOf(profileApp, appA),
            catalogOrder = listOf(appA),
            selected = setOf(profileApp, appA),
        )

        assertEquals(listOf(profileApp, appA), result)
    }

    @Test
    fun mergeFavoriteSelection_treatsOwnerAndCloneAsIndependentApps() {
        val owner = key("app.shared", userSerial = 0L)
        val clone = key("app.shared", userSerial = 10L)

        val result = mergeFavoriteSelection(
            existing = listOf(owner),
            catalogOrder = listOf(owner, clone),
            selected = setOf(owner, clone),
        )

        assertEquals(listOf(owner, clone), result)
    }

    private fun key(packageName: String, userSerial: Long = 0L) = AppInstanceKey(
        packageName = packageName,
        userSerial = userSerial,
        componentName = "$packageName/.MainActivity",
    )
}
