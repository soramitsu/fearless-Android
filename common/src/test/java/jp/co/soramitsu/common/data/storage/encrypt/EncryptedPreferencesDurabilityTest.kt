package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class EncryptedPreferencesDurabilityTest {

    @Before
    fun setUp() {
        WalletSecureStorageHealth.resetForTest()
    }

    @After
    fun tearDown() {
        WalletSecureStorageHealth.resetForTest()
    }

    @Test
    fun `ambiguous commit latches a restart-required failure`() {
        val preferences = mock(Preferences::class.java)
        val encryptionUtil = mock(EncryptionUtil::class.java)
        `when`(encryptionUtil.encrypt("secret")).thenReturn("v2:ciphertext")
        `when`(encryptionUtil.isModernCiphertext("v2:ciphertext"))
            .thenReturn(true)
        `when`(encryptionUtil.decrypt("v2:ciphertext")).thenReturn("secret")
        `when`(
            preferences.replaceStringsDurably(
                mapOf("wallet" to "v2:ciphertext"),
                emptySet()
            )
        ).thenReturn(false)
        val encryptedPreferences = EncryptedPreferencesImpl(
            preferences,
            encryptionUtil
        )

        val firstFailure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            encryptedPreferences.putEncryptedString("wallet", "secret")
        }
        val retryFailure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            encryptedPreferences.putEncryptedString("wallet", "secret")
        }

        assertEquals(
            WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED,
            firstFailure.kind
        )
        assertEquals(
            WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED,
            retryFailure.kind
        )
        verify(preferences, times(1)).replaceStringsDurably(
            mapOf("wallet" to "v2:ciphertext"),
            emptySet()
        )
    }

    @Test
    fun `ambiguous in-memory journal is unreadable in every instance`() {
        val preferences = mock(Preferences::class.java)
        val encryptionUtil = mock(EncryptionUtil::class.java)
        `when`(encryptionUtil.encrypt("secret")).thenReturn("v2:ciphertext")
        `when`(encryptionUtil.isModernCiphertext("v2:ciphertext"))
            .thenReturn(true)
        `when`(encryptionUtil.decrypt("v2:ciphertext")).thenReturn("secret")
        `when`(
            preferences.replaceStringsDurably(
                mapOf("journal" to "v2:ciphertext"),
                emptySet()
            )
        ).thenReturn(false)
        // Model SharedPreferences' process-memory view changing despite a
        // failed disk commit. A replay must never observe this value.
        `when`(preferences.getString("journal")).thenReturn("v2:ciphertext")
        `when`(preferences.contains("journal")).thenReturn(true)
        val writer = EncryptedPreferencesImpl(preferences, encryptionUtil)
        val replayReader = EncryptedPreferencesImpl(preferences, encryptionUtil)

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            writer.putEncryptedString("journal", "secret")
        }
        val readFailure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            replayReader.getDecryptedString("journal")
        }
        val inventoryFailure = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            replayReader.hasKey("journal")
        }

        assertEquals(
            WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED,
            readFailure.kind
        )
        assertEquals(
            WalletSecureStorageFailureKind.PROCESS_RESTART_REQUIRED,
            inventoryFailure.kind
        )
        verify(preferences, never()).getString("journal")
        verify(preferences, never()).contains("journal")
    }

    @Test
    fun `fresh process can read the last durable snapshot`() {
        val preferences = mock(Preferences::class.java)
        val encryptionUtil = mock(EncryptionUtil::class.java)
        `when`(encryptionUtil.encrypt("secret")).thenReturn("v2:ciphertext")
        `when`(encryptionUtil.isModernCiphertext("v2:ciphertext"))
            .thenReturn(true)
        `when`(encryptionUtil.decrypt("v2:ciphertext")).thenReturn("secret")
        `when`(
            preferences.replaceStringsDurably(
                mapOf("journal" to "v2:ciphertext"),
                emptySet()
            )
        ).thenReturn(false)
        val writer = EncryptedPreferencesImpl(preferences, encryptionUtil)

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            writer.putEncryptedString("journal", "secret")
        }
        WalletSecureStorageHealth.resetForTest()
        `when`(preferences.getString("journal")).thenReturn("v2:ciphertext")

        val freshProcessReader = EncryptedPreferencesImpl(
            preferences,
            encryptionUtil
        )
        assertEquals(
            "secret",
            freshProcessReader.getDecryptedString("journal")
        )
        assertTrue(WalletSecureStorageHealth.isHealthy())
    }
}
