package jp.co.soramitsu.common.data.storage

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PreferencesImplKeyInspectionTest {

    @Test
    fun prefixInspectionMatchesNamesOnly() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.all).thenReturn(
            mapOf(
                "42:SUBSTRATE_SECRETS" to "ciphertext",
                "unrelated" to 7
            )
        )
        val preferences = PreferencesImpl(sharedPreferences)

        assertTrue(preferences.hasKeyWithPrefix("42:"))
        assertFalse(preferences.hasKeyWithPrefix("43:"))

        verify(sharedPreferences, never()).getString(any(), any())
    }

    @Test
    fun prefixInspectionRejectsEmptyPrefix() {
        val sharedPreferences = mock<SharedPreferences>()
        val preferences = PreferencesImpl(sharedPreferences)

        assertThrows(IllegalArgumentException::class.java) {
            preferences.hasKeyWithPrefix("")
        }

        verify(sharedPreferences, never()).all
    }

    @Test
    fun boundedKeyInspectionReturnsExactNamesAndSkipsOversizedNames() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.all).thenReturn(
            mapOf(
                "private_alpha" to "ciphertext-a",
                "seed_beta" to "ciphertext-b",
                "private_${"x".repeat(100)}" to "oversized",
                "unrelated" to "value"
            )
        )
        val preferences = PreferencesImpl(sharedPreferences)

        assertEquals(
            setOf("private_alpha", "seed_beta"),
            preferences.keysWithPrefixes(
                prefixes = setOf("private_", "seed_"),
                maxResultCount = 8,
                maxKeyBytes = 32,
                maxTotalKeyBytes = 128
            )
        )
        verify(sharedPreferences, never()).getString(any(), any())
    }

    @Test
    fun boundedKeyInspectionFailsInsteadOfTruncatingCountOrTotalBytes() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.all).thenReturn(
            mapOf(
                "private_a" to "a",
                "private_b" to "b"
            )
        )
        val preferences = PreferencesImpl(sharedPreferences)

        assertThrows(IllegalStateException::class.java) {
            preferences.keysWithPrefixes(
                prefixes = setOf("private_"),
                maxResultCount = 1,
                maxKeyBytes = 16,
                maxTotalKeyBytes = 64
            )
        }
        assertThrows(IllegalStateException::class.java) {
            preferences.keysWithPrefixes(
                prefixes = setOf("private_"),
                maxResultCount = 2,
                maxKeyBytes = 16,
                maxTotalKeyBytes = 16
            )
        }
    }

    @Test
    fun strictBoundedKeyInspectionFailsOnOversizedNamespaceMatch() {
        val sharedPreferences = mock<SharedPreferences>()
        whenever(sharedPreferences.all).thenReturn(
            mapOf(
                "42:${"a".repeat(200)}:ACCESS_SECRETS" to "ciphertext",
                "unrelated_${"b".repeat(200)}" to "value"
            )
        )
        val preferences = PreferencesImpl(sharedPreferences)

        assertThrows(IllegalStateException::class.java) {
            preferences.keysWithPrefixes(
                prefixes = setOf("42:"),
                maxResultCount = 8,
                maxKeyBytes = 64,
                maxTotalKeyBytes = 128,
                failOnOversizedMatch = true
            )
        }
    }
}
