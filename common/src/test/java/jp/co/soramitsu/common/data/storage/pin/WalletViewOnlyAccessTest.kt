package jp.co.soramitsu.common.data.storage.pin

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class WalletViewOnlyAccessTest {
    @After fun tearDown() { WalletViewOnlyAccess.pinResetCompleted() }

    @Test fun deviceCredentialSessionBlocksEvenHealthySigningKeysUntilPinReset() {
        WalletViewOnlyAccess.enter()
        assertTrue(WalletViewOnlyAccess.isEnabled())
        assertTrue(WalletViewOnlyAccess.enabled.value)
        val guard = WalletSecretAccessGuard(HashMapEncryptedPreferences())
        for (scope in listOf(WalletSecretScope.Substrate, WalletSecretScope.Ethereum, WalletSecretScope.Ton,
            WalletSecretScope.ChainAccount("11".repeat(32)))) {
            assertThrows(WalletPinRecoveryRequiredException::class.java) { guard.requireAccess(1, scope) }
            assertFalse(guard.isRecoveryRequired(1, scope)) // Merely viewing public key status remains possible.
        }
        WalletViewOnlyAccess.pinResetCompleted()
        assertFalse(WalletViewOnlyAccess.enabled.value)
        WalletViewOnlyAccess.requireSigningAccess()
        guard.requireAccess(1, WalletSecretScope.Ethereum)
    }
}
