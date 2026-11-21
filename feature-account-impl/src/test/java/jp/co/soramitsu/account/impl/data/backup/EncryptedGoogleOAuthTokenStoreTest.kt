package jp.co.soramitsu.account.impl.data.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedGoogleOAuthTokenStoreTest {

    private val preferences = mockk<EncryptedPreferences>(relaxed = true)
    private val store = EncryptedGoogleOAuthTokenStore(preferences)

    @Test
    fun `the session is written durably and encrypted under its own key`() {
        store.write("{\"refreshToken\":\"synthetic\"}")

        verify(exactly = 1) {
            preferences.replaceEncryptedStringsDurably(
                mapOf(EncryptedGoogleOAuthTokenStore.KEY to "{\"refreshToken\":\"synthetic\"}"),
                emptySet()
            )
        }
    }

    @Test
    fun `clearing removes only that key, durably`() {
        store.clear()

        verify(exactly = 1) {
            preferences.replaceEncryptedStringsDurably(emptyMap(), setOf(EncryptedGoogleOAuthTokenStore.KEY))
        }
    }

    @Test
    fun `reading decrypts the key, and an unreadable value reads as signed out`() {
        every { preferences.getDecryptedString(EncryptedGoogleOAuthTokenStore.KEY) } returns "stored"
        assertEquals("stored", store.read())

        every { preferences.getDecryptedString(EncryptedGoogleOAuthTokenStore.KEY) } throws IllegalStateException("broken")
        assertNull(store.read())
    }

    @Test
    fun `the key is outside every wallet-secret prefix`() {
        val walletPrefixes = (0..9).map(Int::toString) + listOf(
            "wallet_public_identity_recovery:",
            "wallet_secret_quarantine:",
            "wallet_secret_archive:v1:",
            "security_source_",
            "private_",
            "seed_",
            "entropy_",
            "derivation_",
            "pin_code",
            "TON_CONNECT_"
        )

        assertFalse(walletPrefixes.any { EncryptedGoogleOAuthTokenStore.KEY.startsWith(it) })
    }
}
