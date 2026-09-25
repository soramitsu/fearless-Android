package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletSecretAccessGuardTest {

    @Test
    fun `startup inventory finds quarantine and public identity markers`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        assertFalse(guard.hasAnyRecoveryState())

        val quarantineKey = WalletSecretQuarantine.keyFor(
            "1:ACCESS_SECRETS"
        )
        preferences.putEncryptedString(quarantineKey, "ciphertext")
        assertTrue(guard.hasAnyRecoveryState())

        preferences.removeKey(quarantineKey)
        preferences.putEncryptedString(
            WalletPublicIdentityRecovery.keyFor(
                metaId = 1L,
                activeSecretKey = "1:ETHEREUM_SECRETS"
            ),
            WalletPublicIdentityRecovery.MARKER_VALUE
        )
        assertTrue(guard.hasAnyRecoveryState())
    }

    @Test
    fun `startup inventory ignores oversized keys outside recovery namespaces`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(
            "unrelated:" + "x".repeat(2_048),
            "value"
        )

        assertFalse(guard.hasAnyRecoveryState())
    }

    @Test
    fun `oversized recovery key fails closed`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        preferences.putEncryptedString(
            WalletSecretQuarantine.KEY_PREFIX + "x".repeat(1_024),
            "ciphertext"
        )

        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            guard.hasAnyRecoveryState()
        }
    }

    @Test
    fun `attacker expanded recovery inventory fails closed`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        repeat(8_193) { index ->
            preferences.putEncryptedString(
                "${WalletPublicIdentityRecovery.KEY_PREFIX}$index",
                WalletPublicIdentityRecovery.MARKER_VALUE
            )
        }

        assertThrows(WalletRecoveryStateIntegrityException::class.java) {
            guard.hasAnyRecoveryState()
        }
    }
}
