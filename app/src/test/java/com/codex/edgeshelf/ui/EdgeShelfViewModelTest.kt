package com.codex.edgeshelf.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeShelfViewModelTest {
    @Test
    fun mergeFavoriteSelection_preservesExistingOrderAndAppendsNewApps() {
        val result = mergeFavoriteSelection(
            existing = listOf("app.c", "app.a", "missing.app"),
            catalogOrder = listOf("app.a", "app.b", "app.c", "app.d"),
            selected = setOf("app.a", "app.b", "app.c", "missing.app"),
        )

        assertEquals(listOf("app.c", "app.a", "missing.app", "app.b"), result)
    }

    @Test
    fun mergeFavoriteSelection_allowsClearingTheShelf() {
        val result = mergeFavoriteSelection(
            existing = listOf("app.a", "app.b"),
            catalogOrder = listOf("app.a", "app.b"),
            selected = emptySet(),
        )

        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun mergeFavoriteSelection_keepsSelectedFavoritesWhenCatalogIsPartial() {
        val result = mergeFavoriteSelection(
            existing = listOf("work.profile.app", "app.a"),
            catalogOrder = listOf("app.a"),
            selected = setOf("work.profile.app", "app.a"),
        )

        assertEquals(listOf("work.profile.app", "app.a"), result)
    }
}
