package jp.co.soramitsu.common.data.secrets.v3

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletRootSecretStoreTest {

    @Test
    fun unexpectedValidatorFailurePreservesExactSubstrateCiphertext() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val unexpected = IllegalStateException("Injected validator bug")
        val store = SubstrateSecretStore(
            encryptedPreferences = preferences,
            walletRootSecretValidation = TestValidation(
                substrate = { _, _, _, _ -> throw unexpected }
            )
        )
        store.put(META_ID, substrateSecrets())
        val exact = preferences.getDecryptedString(activeKey)

        val thrown = assertThrows(IllegalStateException::class.java) {
            store.get(
                metaId = META_ID,
                expectedPublicKey = PUBLIC_32,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = PUBLIC_32
            )
        }

        assertSame(unexpected, thrown)
        assertEquals(exact, preferences.getDecryptedString(activeKey))
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun missingOrMalformedDurableIdentityNeverQuarantinesActiveSecrets() {
        val substratePreferences = HashMapEncryptedPreferences()
        val substrateKey = "$META_ID:SUBSTRATE_SECRETS"
        SubstrateSecretStore(substratePreferences).put(
            META_ID,
            substrateSecrets()
        )
        val substrateExact =
            substratePreferences.getDecryptedString(substrateKey)

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            SubstrateSecretStore(substratePreferences).get(
                metaId = META_ID,
                expectedPublicKey = null,
                expectedCryptoType = CryptoType.ED25519,
                expectedAccountId = PUBLIC_32
            )
        }
        assertEquals(
            substrateExact,
            substratePreferences.getDecryptedString(substrateKey)
        )
        assertFalse(
            substratePreferences.hasKey(
                WalletSecretQuarantine.keyFor(substrateKey)
            )
        )

        val ethereumPreferences = HashMapEncryptedPreferences()
        val ethereumKey = "$META_ID:ETHEREUM_SECRETS"
        EthereumSecretStore(ethereumPreferences).put(
            META_ID,
            ethereumSecrets()
        )
        val ethereumExact =
            ethereumPreferences.getDecryptedString(ethereumKey)

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            EthereumSecretStore(ethereumPreferences).get(
                metaId = META_ID,
                expectedPublicKey = PUBLIC_33,
                expectedAddress = null
            )
        }
        assertEquals(
            ethereumExact,
            ethereumPreferences.getDecryptedString(ethereumKey)
        )
        assertFalse(
            ethereumPreferences.hasKey(
                WalletSecretQuarantine.keyFor(ethereumKey)
            )
        )

        val tonPreferences = HashMapEncryptedPreferences()
        val tonKey = "$META_ID:TON_SECRETS"
        TonSecretStore(tonPreferences).put(META_ID, tonSecrets())
        val tonExact = tonPreferences.getDecryptedString(tonKey)

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            TonSecretStore(tonPreferences).get(
                metaId = META_ID,
                expectedPublicKey = null
            )
        }
        assertEquals(tonExact, tonPreferences.getDecryptedString(tonKey))
        assertFalse(
            tonPreferences.hasKey(WalletSecretQuarantine.keyFor(tonKey))
        )
    }

    @Test
    fun providerFailurePreservesExactEthereumAndTonCiphertexts() {
        val providerFailure =
            WalletSecureStorageUnavailableException("Injected provider outage")
        val validation = TestValidation(
            ethereum = { _, _, _ -> throw providerFailure },
            ton = { _, _ -> throw providerFailure }
        )

        val ethereumPreferences = HashMapEncryptedPreferences()
        val ethereumKey = "$META_ID:ETHEREUM_SECRETS"
        EthereumSecretStore(ethereumPreferences, validation).put(
            META_ID,
            ethereumSecrets()
        )
        val ethereumExact =
            ethereumPreferences.getDecryptedString(ethereumKey)
        val thrownEthereum = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            EthereumSecretStore(ethereumPreferences, validation).get(
                metaId = META_ID,
                expectedPublicKey = PUBLIC_33,
                expectedAddress = ADDRESS_20
            )
        }
        assertSame(providerFailure, thrownEthereum)
        assertEquals(
            ethereumExact,
            ethereumPreferences.getDecryptedString(ethereumKey)
        )
        assertFalse(
            ethereumPreferences.hasKey(
                WalletSecretQuarantine.keyFor(ethereumKey)
            )
        )

        val tonPreferences = HashMapEncryptedPreferences()
        val tonKey = "$META_ID:TON_SECRETS"
        TonSecretStore(tonPreferences, validation).put(META_ID, tonSecrets())
        val tonExact = tonPreferences.getDecryptedString(tonKey)
        val thrownTon = assertThrows(
            WalletSecureStorageUnavailableException::class.java
        ) {
            TonSecretStore(tonPreferences, validation).get(
                metaId = META_ID,
                expectedPublicKey = PUBLIC_32
            )
        }
        assertSame(providerFailure, thrownTon)
        assertEquals(tonExact, tonPreferences.getDecryptedString(tonKey))
        assertFalse(
            tonPreferences.hasKey(WalletSecretQuarantine.keyFor(tonKey))
        )
    }

    @Test
    fun inconsistentDurableIdentityPairPreservesExactActiveCiphertext() {
        val preferences = HashMapEncryptedPreferences()
        val activeKey = "$META_ID:SUBSTRATE_SECRETS"
        val keypair = EthereumKeypairFactory.createWithPrivateKey(PRIVATE_32)
        val store = SubstrateSecretStore(preferences)
        store.put(
            META_ID,
            SubstrateSecrets(substrateKeyPair = keypair)
        )
        val exact = preferences.getDecryptedString(activeKey)
        val wrongAccountId = keypair.publicKey.substrateAccountId().clone().also {
            it[0] = (it[0].toInt() xor 0x01).toByte()
        }

        assertThrows(WalletPublicIdentityIntegrityException::class.java) {
            store.get(
                metaId = META_ID,
                expectedPublicKey = keypair.publicKey,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = wrongAccountId
            )
        }

        assertEquals(exact, preferences.getDecryptedString(activeKey))
        assertFalse(
            preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
        )
    }

    @Test
    fun onlyTypedRecordLocalFailureMovesExactCiphertext() {
        val localFailure =
            WalletRootSecretCorruptionException("Injected payload mismatch")
        val validation = TestValidation(
            substrate = { _, _, _, _ -> throw localFailure },
            ethereum = { _, _, _ -> throw localFailure },
            ton = { _, _ -> throw localFailure }
        )
        val fixtures = listOf(
            RootStoreFixture(
                activeKey = "$META_ID:SUBSTRATE_SECRETS",
                preferences = HashMapEncryptedPreferences(),
                put = { preferences ->
                    SubstrateSecretStore(preferences, validation).put(
                        META_ID,
                        substrateSecrets()
                    )
                },
                read = { preferences ->
                    SubstrateSecretStore(preferences, validation).get(
                        metaId = META_ID,
                        expectedPublicKey = PUBLIC_32,
                        expectedCryptoType = CryptoType.ED25519,
                        expectedAccountId = PUBLIC_32
                    )
                }
            ),
            RootStoreFixture(
                activeKey = "$META_ID:ETHEREUM_SECRETS",
                preferences = HashMapEncryptedPreferences(),
                put = { preferences ->
                    EthereumSecretStore(preferences, validation).put(
                        META_ID,
                        ethereumSecrets()
                    )
                },
                read = { preferences ->
                    EthereumSecretStore(preferences, validation).get(
                        metaId = META_ID,
                        expectedPublicKey = PUBLIC_33,
                        expectedAddress = ADDRESS_20
                    )
                }
            ),
            RootStoreFixture(
                activeKey = "$META_ID:TON_SECRETS",
                preferences = HashMapEncryptedPreferences(),
                put = { preferences ->
                    TonSecretStore(preferences, validation).put(
                        META_ID,
                        tonSecrets()
                    )
                },
                read = { preferences ->
                    TonSecretStore(preferences, validation).get(
                        metaId = META_ID,
                        expectedPublicKey = PUBLIC_32
                    )
                }
            )
        )

        fixtures.forEach { fixture ->
            fixture.put(fixture.preferences)
            val exact =
                fixture.preferences.getDecryptedString(fixture.activeKey)

            assertThrows(WalletRecoveryRequiredException::class.java) {
                fixture.read(fixture.preferences)
            }

            assertFalse(fixture.preferences.hasKey(fixture.activeKey))
            assertEquals(
                exact,
                fixture.preferences.getDecryptedString(
                    WalletSecretQuarantine.keyFor(fixture.activeKey)
                )
            )
            assertTrue(
                fixture.preferences.hasKey(
                    WalletSecretQuarantine.keyFor(fixture.activeKey)
                )
            )
        }
    }

    private class TestValidation(
        private val substrate: (
            String,
            ByteArray,
            CryptoType,
            ByteArray
        ) -> String = { encoded, _, _, _ -> encoded },
        private val ethereum: (
            String,
            ByteArray,
            ByteArray
        ) -> String = { encoded, _, _ -> encoded },
        private val ton: (
            String,
            ByteArray
        ) -> String = { encoded, _ -> encoded }
    ) : WalletRootSecretValidation {

        override fun validateSubstrateAndSanitize(
            encoded: String,
            expectedPublicKey: ByteArray,
            expectedCryptoType: CryptoType,
            expectedAccountId: ByteArray
        ): String {
            return substrate(
                encoded,
                expectedPublicKey,
                expectedCryptoType,
                expectedAccountId
            )
        }

        override fun validateEthereumAndSanitize(
            encoded: String,
            expectedPublicKey: ByteArray,
            expectedAddress: ByteArray
        ): String = ethereum(encoded, expectedPublicKey, expectedAddress)

        override fun validateTonAndSanitize(
            encoded: String,
            expectedPublicKey: ByteArray
        ): String = ton(encoded, expectedPublicKey)
    }

    private data class RootStoreFixture(
        val activeKey: String,
        val preferences: HashMapEncryptedPreferences,
        val put: (HashMapEncryptedPreferences) -> Unit,
        val read: (HashMapEncryptedPreferences) -> Any?
    )

    private companion object {
        const val META_ID = 83L
        val PUBLIC_32 = ByteArray(32) { (it + 1).toByte() }
        val PUBLIC_33 = ByteArray(33) { (it + 2).toByte() }
        val ADDRESS_20 = ByteArray(20) { (it + 3).toByte() }
        val PRIVATE_32 = ByteArray(32) { (it + 4).toByte() }

        fun substrateSecrets() = SubstrateSecrets(
            substrateKeyPair = Keypair(
                publicKey = PUBLIC_32,
                privateKey = PRIVATE_32
            )
        )

        fun ethereumSecrets() = EthereumSecrets(
            ethereumKeypair = Keypair(
                publicKey = PUBLIC_33,
                privateKey = PRIVATE_32
            )
        )

        fun tonSecrets() = TonSecrets(
            seed = "invalid but structurally present seed".encodeToByteArray(),
            tonKeypair = Keypair(
                publicKey = PUBLIC_32,
                privateKey = PRIVATE_32
            )
        )
    }
}
