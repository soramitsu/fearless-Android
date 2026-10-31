package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretMutationJournalStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.TonKeyDerivation
import jp.co.soramitsu.fearless_utils.scale.toHexString
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** A password wallet's universal TON key derives with its BIP39 passphrase and must stay usable. */
class UniversalWalletPassphraseTonSecretTest {

    private val legacy = TonKeyDerivation.deriveAccount(MNEMONIC)
    private val protected = TonKeyDerivation.deriveAccount(MNEMONIC, PASSWORD)

    @Test
    fun `password key recovers only with the wallet passphrase while the old key needs none`() {
        val protectedSecret = secret(protected)
        assertTrue(
            WalletRootSecretValidator.validateTon(protectedSecret, protected.publicKey).assessment is RecoveryAssessment.Unverified
        )
        assertEquals(
            RecoveryAssessment.Verified,
            WalletRootSecretValidator.validateTon(protectedSecret, protected.publicKey, listOf(PASSWORD)).assessment
        )
        assertTrue(
            WalletRootSecretValidator.validateTon(protectedSecret, protected.publicKey, listOf("other")).assessment
                is RecoveryAssessment.Unverified
        )
        for (passphrases in listOf(emptyList(), listOf(PASSWORD))) {
            assertEquals(
                RecoveryAssessment.Verified,
                WalletRootSecretValidator.validateTon(secret(legacy), legacy.publicKey, passphrases).assessment
            )
        }
    }

    @Test
    fun `store hands out a password key's phrase only with the passphrase`() {
        val preferences = HashMapEncryptedPreferences()
        val store = TonSecretStore(preferences)
        store.put(META_ID, TonSecrets.read(secret(protected)))

        assertNull(store.getWithVerifiedRecovery(META_ID, protected.publicKey))
        val verified = store.getWithVerifiedRecovery(META_ID, protected.publicKey, listOf(PASSWORD))
        assertNotNull(verified)
        assertEquals(MNEMONIC, verified!![TonSecrets.Seed].decodeToString())
        // Signing never needed the phrase and keeps working either way.
        assertArrayEquals(protected.privateKey, store.get(META_ID, protected.publicKey)?.get(TonSecrets.PrivateKey))
    }

    @Test
    fun `a TON swap in flight is never quarantined while its journal is staged`() {
        val preferences = HashMapEncryptedPreferences()
        val store = TonSecretStore(preferences)
        store.put(META_ID, TonSecrets.read(secret(protected)))
        val activeKey = "$META_ID:TON_SECRETS"

        // Room still names the old key while the replacement is staged.
        preferences.putEncryptedString(WalletSecretMutationJournalStore.JOURNAL_KEY, "staged")
        assertThrows(WalletRootSecretCorruptionException::class.java) { store.get(META_ID, legacy.publicKey) }
        assertTrue(preferences.hasKey(activeKey))
        assertFalse(preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey)))

        // Without a staged mutation the same mismatch is corruption, as before.
        preferences.removeKey(WalletSecretMutationJournalStore.JOURNAL_KEY)
        assertThrows(WalletRecoveryRequiredException::class.java) { store.get(META_ID, legacy.publicKey) }
        assertTrue(preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey)))
    }

    private fun secret(account: TonKeyDerivation.TonAccount): String =
        TonSecrets(MNEMONIC.encodeToByteArray(), Keypair(account.publicKey, account.privateKey)).toHexString()

    private companion object {
        const val META_ID = 3L
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val PASSWORD = "TREZOR"
    }
}
