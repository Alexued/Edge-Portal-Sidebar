package com.codex.edgeshelf.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppInstanceTest {
    @Test
    fun versionedEncoding_roundTripsEveryIdentityField() {
        val key = AppInstanceKey(
            packageName = "com.example.pipe|safe",
            userSerial = 10L,
            componentName = "com.example.pipe|safe/.Main Activity",
        )

        assertEquals(key, decodeAppInstanceKey(key.encode()))
    }

    @Test
    fun versionedEncoding_roundTripsUnresolvedLegacyComponent() {
        val legacy = AppInstanceKey.legacy("com.example.old")

        assertEquals(legacy, decodeAppInstanceKey(legacy.encode()))
    }

    @Test
    fun decoder_rejectsUnknownVersionsAndMalformedFields() {
        assertNull(decodeAppInstanceKey(null))
        assertNull(decodeAppInstanceKey(""))
        assertNull(decodeAppInstanceKey("v2|0|YQ|Yg"))
        assertNull(decodeAppInstanceKey("v1|not-a-number|YQ|Yg"))
        assertNull(decodeAppInstanceKey("v1|0|%%%|Yg"))
    }

    @Test
    fun legacyPackage_staysUnresolvedUntilCatalogIsKnown() {
        assertEquals(
            AppInstanceKey.legacy("com.example.old"),
            decodeStoredAppInstanceKey(" com.example.old "),
        )
    }

    @Test
    fun rebind_legacyPackageUsesCurrentProfileAndNeverGuessesClone() {
        val owner = key("com.example.shared", 7L, ".OwnerMain")
        val clone = key("com.example.shared", 10L, ".CloneMain")

        assertEquals(
            owner,
            rebindAppInstanceKey(
                stored = AppInstanceKey.legacy("com.example.shared"),
                available = listOf(clone, owner),
                currentUserSerial = 7L,
            ),
        )
    }

    @Test
    fun rebind_componentChangeKeepsPackageAndProfileIdentity() {
        val upgraded = key("com.example.app", 10L, ".NewMain")

        assertEquals(
            upgraded,
            rebindAppInstanceKey(
                stored = key("com.example.app", 10L, ".OldMain"),
                available = listOf(key("com.example.app", 0L, ".OwnerMain"), upgraded),
                currentUserSerial = 0L,
            ),
        )
    }

    @Test
    fun rebind_missingProfileDoesNotFallAcrossUsers() {
        assertNull(
            rebindAppInstanceKey(
                stored = key("com.example.app", 10L, ".Main"),
                available = listOf(key("com.example.app", 0L, ".Main")),
                currentUserSerial = 0L,
            ),
        )
    }

    private fun key(packageName: String, serial: Long, component: String) = AppInstanceKey(
        packageName = packageName,
        userSerial = serial,
        componentName = "$packageName/$component",
    )
}
