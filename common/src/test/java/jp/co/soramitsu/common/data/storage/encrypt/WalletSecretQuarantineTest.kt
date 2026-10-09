package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretCorruptionException
import jp.co.soramitsu.common.data.secrets.v3.WalletRootSecretValidation
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WalletSecretQuarantineTest {

    @Test
    fun `guard blocks root legacy and chain account quarantine markers`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        val publicKey = ByteArray(32) { it.toByte() }
        val accountId = ByteArray(32) { (it + 7).toByte() }

        assertFalse(guard.isRecoveryRequired(META_ID, publicKey, accountId))

        preferences.putEncryptedString(
            WalletSecretQuarantine.legacyV1KeyForMetaId(META_ID, publicKey),
            CORRUPT_CIPHERTEXT
        )
        assertTrue(guard.isRecoveryRequired(META_ID, publicKey))
        assertThrows(WalletRecoveryRequiredException::class.java) {
            guard.requireAccess(META_ID, publicKey)
        }

        preferences.removeKey(
            WalletSecretQuarantine.legacyV1KeyForMetaId(META_ID, publicKey)
        )
        preferences.putEncryptedString(
            WalletSecretQuarantine.chainAccountKey(META_ID, accountId),
            CORRUPT_CIPHERTEXT
        )
        assertTrue(guard.isRecoveryRequired(META_ID, publicKey, accountId))
        assertFalse(guard.isRecoveryRequired(META_ID, publicKey))
    }

    @Test
    fun `guard fails closed for empty and malformed chain account identities`() {
        val guard = WalletSecretAccessGuard(HashMapEncryptedPreferences())

        listOf(
            ByteArray(0),
            ByteArray(31) { it.toByte() },
            ByteArray(33) { it.toByte() }
        ).forEach { malformedAccountId ->
            assertTrue(
                guard.isRecoveryRequired(
                    metaId = META_ID,
                    chainAccountId = malformedAccountId
                )
            )
            assertThrows(WalletRecoveryRequiredException::class.java) {
                guard.requireAccess(
                    metaId = META_ID,
                    chainAccountId = malformedAccountId
                )
            }
        }
    }

    @Test
    fun `guard recognizes public identity recovery without moving active secret`() {
        val preferences = HashMapEncryptedPreferences()
        val guard = WalletSecretAccessGuard(preferences)
        val activeKey = "$META_ID:ETHEREUM_SECRETS"
        preferences.putEncryptedString(activeKey, "exact-active-secret")
        preferences.putEncryptedString(
            WalletPublicIdentityRecovery.keyFor(META_ID, activeKey),
            WalletPublicIdentityRecovery.MARKER_VALUE
        )

        assertTrue(guard.isRecoveryRequired(META_ID))
        assertEquals(
            "exact-active-secret",
            preferences.getDecryptedString(activeKey)
        )
        assertThrows(WalletRecoveryRequiredException::class.java) {
            guard.requireAccess(META_ID)
        }
    }

    @Test
    fun `malformed current secret is quarantined exactly and becomes recovery only`() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        preferences.putEncryptedString(activeKey, CORRUPT_CIPHERTEXT)

        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }

        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            CORRUPT_CIPHERTEXT,
            preferences.getDecryptedString(quarantineKey)
        )
        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }
    }

    @Test
    fun `oversized decoded payload is quarantined before scale parsing`() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val oversized = "0".repeat(MAX_WALLET_SECRET_PLAINTEXT_CHARS + 1)
        preferences.putEncryptedString(activeKey, oversized)

        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }

        assertEquals(
            oversized,
            preferences.getDecryptedString(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun `missing current secret remains a normal absent secret`() {
        assertNull(
            SubstrateSecretStore(HashMapEncryptedPreferences()).get(META_ID)
        )
    }

    @Test
    fun `global secure storage failure never creates a wallet quarantine`() {
        val preferences = mock<EncryptedPreferences>()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        whenever(preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey)))
            .thenReturn(false)
        whenever(preferences.hasKey(activeKey)).thenReturn(true)
        whenever(preferences.getDecryptedStringSnapshot(activeKey)).thenThrow(
            WalletSecureStorageUnavailableException("unavailable")
        )

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }

        verify(preferences, never())
            .quarantineEncryptedStringDurably(any(), any(), any())
    }

    @Test
    fun `preexisting quarantine marker wins without overwriting either value`() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        preferences.putEncryptedString(activeKey, "new-active-value")
        preferences.putEncryptedString(quarantineKey, "older-quarantine-value")

        assertThrows(WalletRecoveryRequiredException::class.java) {
            SubstrateSecretStore(preferences).get(META_ID)
        }

        assertEquals("new-active-value", preferences.getDecryptedString(activeKey))
        assertEquals(
            "older-quarantine-value",
            preferences.getDecryptedString(quarantineKey)
        )
    }

    @Test
    fun `valid decoded substrate key swap is quarantined exactly`() {
        val preferences = HashMapEncryptedPreferences()
        val store = SubstrateSecretStore(
            preferences,
            IdentityOnlyValidation
        )
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        store.put(
            META_ID,
            SubstrateSecrets(
                substrateKeyPair = Keypair(
                    publicKey = SWAPPED_PUBLIC_KEY,
                    privateKey = PRIVATE_KEY
                )
            )
        )
        val exactStoredPayload = checkNotNull(preferences.getDecryptedString(activeKey))

        assertThrows(WalletRecoveryRequiredException::class.java) {
            store.get(
                metaId = META_ID,
                expectedPublicKey = EXPECTED_PUBLIC_KEY,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = CHAIN_ACCOUNT_ID
            )
        }

        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            exactStoredPayload,
            preferences.getDecryptedString(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun `matching substrate public key remains usable`() {
        val preferences = HashMapEncryptedPreferences()
        val store = SubstrateSecretStore(
            preferences,
            IdentityOnlyValidation
        )
        store.put(
            META_ID,
            SubstrateSecrets(
                substrateKeyPair = Keypair(
                    publicKey = EXPECTED_PUBLIC_KEY,
                    privateKey = PRIVATE_KEY
                )
            )
        )

        val secrets = checkNotNull(
            store.get(
                metaId = META_ID,
                expectedPublicKey = EXPECTED_PUBLIC_KEY,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = CHAIN_ACCOUNT_ID
            )
        )

        assertArrayEquals(
            EXPECTED_PUBLIC_KEY,
            secrets[SubstrateSecrets.SubstrateKeypair]
                [jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PublicKey]
        )
    }

    @Test
    fun `valid decoded ethereum and ton key swaps are quarantined`() {
        val ethereumPreferences = HashMapEncryptedPreferences()
        val ethereumStore = EthereumSecretStore(
            ethereumPreferences,
            IdentityOnlyValidation
        )
        ethereumStore.put(
            META_ID,
            EthereumSecrets(
                ethereumKeypair = Keypair(
                    publicKey = SWAPPED_PUBLIC_KEY,
                    privateKey = PRIVATE_KEY
                )
            )
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            ethereumStore.get(
                metaId = META_ID,
                expectedPublicKey = EXPECTED_PUBLIC_KEY,
                expectedAddress = EXPECTED_ETHEREUM_ADDRESS
            )
        }
        assertTrue(
            ethereumPreferences.hasKey(
                WalletSecretQuarantine.keyFor("$META_ID:ETHEREUM_SECRETS")
            )
        )

        val tonPreferences = HashMapEncryptedPreferences()
        val tonStore = TonSecretStore(
            tonPreferences,
            IdentityOnlyValidation
        )
        tonStore.put(
            META_ID,
            TonSecrets(
                seed = ByteArray(32) { 3 },
                tonKeypair = Keypair(
                    publicKey = SWAPPED_PUBLIC_KEY,
                    privateKey = PRIVATE_KEY
                )
            )
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            tonStore.get(META_ID, EXPECTED_PUBLIC_KEY)
        }
        assertTrue(
            tonPreferences.hasKey(
                WalletSecretQuarantine.keyFor("$META_ID:TON_SECRETS")
            )
        )
    }

    @Test
    fun `valid decoded chain account key swap is quarantined`() = runBlocking {
        val preferences = HashMapEncryptedPreferences()
        val store = SecretStoreV2(preferences)
        store.putChainAccountSecrets(
            metaId = META_ID,
            accountId = EXPECTED_PUBLIC_KEY,
            secrets = ChainAccountSecrets(
                keyPair = Keypair(
                    publicKey = SWAPPED_PUBLIC_KEY,
                    privateKey = PRIVATE_KEY
                )
            )
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                store.getChainAccountSecrets(
                    metaId = META_ID,
                    accountId = EXPECTED_PUBLIC_KEY,
                    expectedPublicKey = EXPECTED_PUBLIC_KEY,
                    expectedCryptoType = CryptoType.ED25519
                )
            }
        }

        assertTrue(
            preferences.hasKey(
                WalletSecretQuarantine.chainAccountKey(
                    META_ID,
                    EXPECTED_PUBLIC_KEY
                )
            )
        )
    }

    private companion object {
        const val META_ID = 42L
        const val CORRUPT_CIPHERTEXT = "not-scale-\u0000-\uD83D\uDD10"
        val EXPECTED_PUBLIC_KEY = ByteArray(32) { (it + 1).toByte() }
        val SWAPPED_PUBLIC_KEY = ByteArray(32) { (it + 33).toByte() }
        val PRIVATE_KEY = ByteArray(32) { (it + 65).toByte() }
        val CHAIN_ACCOUNT_ID = ByteArray(32) { (it + 97).toByte() }
        val EXPECTED_ETHEREUM_ADDRESS =
            ByteArray(20) { (it + 113).toByte() }

        object IdentityOnlyValidation : WalletRootSecretValidation {
            override fun validateSubstrateAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedCryptoType: CryptoType,
                expectedAccountId: ByteArray
            ): String {
                val decoded = SubstrateSecrets.read(encoded)
                val keypair = decoded[SubstrateSecrets.SubstrateKeypair]
                val actual =
                    keypair[jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PublicKey]
                requireIdentity(actual.contentEquals(expectedPublicKey))
                return encoded
            }

            override fun validateEthereumAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray,
                expectedAddress: ByteArray
            ): String {
                val decoded = EthereumSecrets.read(encoded)
                val keypair = decoded[EthereumSecrets.EthereumKeypair]
                val actual =
                    keypair[jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PublicKey]
                requireIdentity(actual.contentEquals(expectedPublicKey))
                return encoded
            }

            override fun validateTonAndSanitize(
                encoded: String,
                expectedPublicKey: ByteArray
            ): String {
                val actual = TonSecrets.read(encoded)[TonSecrets.PublicKey]
                requireIdentity(actual.contentEquals(expectedPublicKey))
                return encoded
            }

            private fun requireIdentity(matches: Boolean) {
                if (!matches) {
                    throw WalletRootSecretCorruptionException(
                        "Injected identity mismatch"
                    )
                }
            }
        }
    }
}
