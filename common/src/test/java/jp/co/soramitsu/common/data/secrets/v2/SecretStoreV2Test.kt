package jp.co.soramitsu.common.data.secrets.v2

import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.KeyPairSchema.PrivateKey
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets.SubstrateDerivationPath
import jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets.SubstrateKeypair
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityIntegrityException
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecureStorageUnavailableException
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import java.security.ProviderException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val META_ID = 1L
private val CHAIN_KEYPAIR = EthereumKeypairFactory.createWithPrivateKey(
    ByteArray(32) { index -> (index * 7 + 3).toByte() }
)
private val ACCOUNT_ID = CHAIN_KEYPAIR.publicKey.substrateAccountId()

@RunWith(JUnit4::class)
class SecretStoreV2Test {

    private val preferences = HashMapEncryptedPreferences()
    private val secretStore = SecretStoreV2(preferences)

    @Test
    fun `should save and retrieve meta account secrets`() = runBlocking {

        val secrets = createMetaSecrets()

        secretStore.putMetaAccountSecrets(META_ID, secrets)

        val secretsFromStore = secretStore.getMetaAccountSecrets(META_ID)

        requireNotNull(secretsFromStore)
        assertArrayEquals(secrets[SubstrateKeypair][PrivateKey], secretsFromStore[SubstrateKeypair][PrivateKey])
    }

    @Test
    fun `should save and retrieve chain account secrets`() = runBlocking {
        val secrets = createChainSecrets()

        secretStore.putChainAccountSecrets(META_ID, ACCOUNT_ID, secrets)

        val secretsFromStore = secretStore.getChainAccountSecrets(META_ID, ACCOUNT_ID)

        requireNotNull(secretsFromStore)
        assertArrayEquals(secrets[ChainAccountSecrets.Keypair][PrivateKey], secretsFromStore[ChainAccountSecrets.Keypair][PrivateKey])

        val metaSecrets = secretStore.getMetaAccountSecrets(META_ID)

        assertNull("Chain secrets should not overwrite meta account secrets", metaSecrets)
    }

    @Test
    fun `chain secrets should not overwrite meta secrets`() = runBlocking {
        val metaSecrets = createMetaSecrets(derivationPath = "/1")
        val chainSecrets = createChainSecrets(derivationPath = "/2")

        secretStore.putMetaAccountSecrets(metaId = 11, metaSecrets)
        secretStore.putChainAccountSecrets(metaId = 1, accountId= ACCOUNT_ID, chainSecrets)

        val secretsFromStore = secretStore.getMetaAccountSecrets(11)

        requireNotNull(secretsFromStore)
        assertEquals( metaSecrets[SubstrateDerivationPath], secretsFromStore[SubstrateDerivationPath])
    }

    @Test
    fun `should delete secrets`() = runBlocking {
        val metaSecrets = createMetaSecrets()
        val chainSecrets = createChainSecrets()

        secretStore.putMetaAccountSecrets(metaId = META_ID, metaSecrets)
        secretStore.putChainAccountSecrets(metaId = META_ID, accountId = ACCOUNT_ID, chainSecrets)

        secretStore.clearSecrets(META_ID, chainAccountIds = listOf(ACCOUNT_ID))

        val metaSecretsLocal = secretStore.getMetaAccountSecrets(META_ID)
        assertNull(metaSecretsLocal)

        val chainSecretsLocal = secretStore.getChainAccountSecrets(META_ID, ACCOUNT_ID)
        assertNull(chainSecretsLocal)
    }

    @Test
    fun `same public chain key with wrong private key is quarantined exactly`() = runBlocking {
        val other = EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { index -> (index * 13 + 9).toByte() }
        )
        val forged = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = CHAIN_KEYPAIR.publicKey,
                privateKey = other.privateKey
            )
        )
        secretStore.putChainAccountSecrets(META_ID, ACCOUNT_ID, forged)
        val activeKey = "$META_ID:${ACCOUNT_ID.toHexString()}:ACCESS_SECRETS"
        val exactPayload = preferences.getDecryptedString(activeKey)

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                secretStore.getChainAccountSecrets(
                    metaId = META_ID,
                    accountId = ACCOUNT_ID,
                    expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                    expectedCryptoType = CryptoType.ECDSA
                )
            }
        }

        assertFalse(preferences.hasKey(activeKey))
        assertEquals(
            exactPayload,
            preferences.getDecryptedString(
                WalletSecretQuarantine.keyFor(activeKey)
            )
        )
    }

    @Test
    fun `generic chain secret provider read failure propagates without quarantine`() {
        val encryptedPreferences = mock<EncryptedPreferences>()
        val activeKey = "$META_ID:${ACCOUNT_ID.toHexString()}:ACCESS_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        whenever(encryptedPreferences.hasKey(quarantineKey)).thenReturn(false)
        whenever(encryptedPreferences.hasKey(activeKey)).thenReturn(true)
        whenever(encryptedPreferences.getDecryptedStringSnapshot(activeKey))
            .thenThrow(RuntimeException("provider failed"))
        val store = SecretStoreV2(encryptedPreferences)

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                store.getChainAccountSecrets(
                    metaId = META_ID,
                    accountId = ACCOUNT_ID,
                    expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                    expectedCryptoType = CryptoType.ECDSA
                )
            }
        }

        verify(encryptedPreferences, never())
            .quarantineEncryptedStringDurably(any(), any(), any())
    }

    @Test
    fun `secure chain secret provider outage propagates without quarantine`() {
        val encryptedPreferences = mock<EncryptedPreferences>()
        val activeKey = "$META_ID:${ACCOUNT_ID.toHexString()}:ACCESS_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        whenever(encryptedPreferences.hasKey(quarantineKey)).thenReturn(false)
        whenever(encryptedPreferences.hasKey(activeKey)).thenReturn(true)
        whenever(
            encryptedPreferences.getDecryptedStringSnapshot(activeKey)
        ).thenThrow(
            WalletSecureStorageUnavailableException("secure storage unavailable")
        )
        val store = SecretStoreV2(encryptedPreferences)

        assertThrows(WalletSecureStorageUnavailableException::class.java) {
            runBlocking {
                store.getChainAccountSecrets(
                    metaId = META_ID,
                    accountId = ACCOUNT_ID,
                    expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                    expectedCryptoType = CryptoType.ECDSA
                )
            }
        }

        verify(encryptedPreferences, never())
            .quarantineEncryptedStringDurably(any(), any(), any())
    }

    @Test
    fun `malformed durable chain identity preserves explicitly keyed active record`() =
        runBlocking {
            val malformedAccountId = ACCOUNT_ID.copyOf(31)
            val activeKey =
                "$META_ID:${malformedAccountId.toHexString()}:ACCESS_SECRETS"
            secretStore.putChainAccountSecrets(
                metaId = META_ID,
                accountId = malformedAccountId,
                secrets = ChainAccountSecrets(keyPair = CHAIN_KEYPAIR)
            )
            val exactPayload = preferences.getDecryptedString(activeKey)

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                runBlocking {
                    secretStore.getChainAccountSecrets(
                        metaId = META_ID,
                        accountId = malformedAccountId,
                        expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                        expectedCryptoType = CryptoType.ECDSA
                    )
                }
            }

            assertEquals(exactPayload, preferences.getDecryptedString(activeKey))
            assertFalse(
                preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
            )
        }

    @Test
    fun `inconsistent durable chain identity preserves active record`() =
        runBlocking {
            val wrongAccountId = ACCOUNT_ID.clone().also {
                it[0] = (it[0].toInt() xor 0x01).toByte()
            }
            val activeKey =
                "$META_ID:${wrongAccountId.toHexString()}:ACCESS_SECRETS"
            secretStore.putChainAccountSecrets(
                metaId = META_ID,
                accountId = wrongAccountId,
                secrets = ChainAccountSecrets(keyPair = CHAIN_KEYPAIR)
            )
            val exactPayload = preferences.getDecryptedString(activeKey)

            assertThrows(WalletPublicIdentityIntegrityException::class.java) {
                runBlocking {
                    secretStore.getChainAccountSecrets(
                        metaId = META_ID,
                        accountId = wrongAccountId,
                        expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                        expectedCryptoType = CryptoType.ECDSA
                    )
                }
            }

            assertEquals(exactPayload, preferences.getDecryptedString(activeKey))
            assertFalse(
                preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
            )
        }

    @Test
    fun `durable quarantine failure propagates instead of becoming recovery`() {
        val encryptedPreferences = mock<EncryptedPreferences>()
        val activeKey = "$META_ID:${ACCOUNT_ID.toHexString()}:ACCESS_SECRETS"
        val quarantineKey = WalletSecretQuarantine.keyFor(activeKey)
        val malformedSnapshot = EncryptedPreferenceSnapshot.unencrypted(
            "malformed-scale-payload"
        )
        whenever(encryptedPreferences.hasKey(quarantineKey)).thenReturn(false)
        whenever(encryptedPreferences.hasKey(activeKey)).thenReturn(true)
        whenever(encryptedPreferences.getDecryptedStringSnapshot(activeKey))
            .thenReturn(malformedSnapshot)
        doThrow(RuntimeException("durable commit failed"))
            .whenever(encryptedPreferences)
            .quarantineEncryptedStringDurably(
                activeKey,
                quarantineKey,
                malformedSnapshot
            )
        val store = SecretStoreV2(encryptedPreferences)

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                store.getChainAccountSecrets(
                    metaId = META_ID,
                    accountId = ACCOUNT_ID,
                    expectedPublicKey = CHAIN_KEYPAIR.publicKey,
                    expectedCryptoType = CryptoType.ECDSA
                )
            }
        }

        verify(encryptedPreferences)
            .quarantineEncryptedStringDurably(
                activeKey,
                quarantineKey,
                malformedSnapshot
            )
    }

    @Test
    fun `sr ed and ecdsa provider failures preserve active secret for retry`() =
        runBlocking {
            val fixtures = listOf(
                RuntimeFixture(
                    CryptoType.SR25519,
                    Keypair(
                        publicKey = ByteArray(32) { (it + 1).toByte() },
                        privateKey = ByteArray(32) { (it + 2).toByte() },
                        nonce = ByteArray(32) { (it + 3).toByte() }
                    )
                ),
                RuntimeFixture(
                    CryptoType.ED25519,
                    Keypair(
                        publicKey = ByteArray(32) { (it + 4).toByte() },
                        privateKey = ByteArray(32) { (it + 5).toByte() }
                    )
                ),
                RuntimeFixture(
                    CryptoType.ECDSA,
                    EthereumKeypairFactory.createWithPrivateKey(
                        ByteArray(32) { (it + 7).toByte() }
                    )
                )
            )

            fixtures.forEachIndexed { index, fixture ->
                val fixtureMetaId = META_ID + index + 1
                val accountId = fixture.keypair.publicKey.substrateAccountId()
                val activeKey =
                    "$fixtureMetaId:${accountId.toHexString()}:ACCESS_SECRETS"
                secretStore.putChainAccountSecrets(
                    metaId = fixtureMetaId,
                    accountId = accountId,
                    secrets = ChainAccountSecrets(keyPair = fixture.keypair)
                )
                val exactPayload = preferences.getDecryptedString(activeKey)
                val providerCause = ProviderException(
                    "Injected ${fixture.cryptoType} provider failure"
                )
                val providerFailure = WalletSecureStorageUnavailableException(
                    "Chain-account cryptography is unavailable",
                    providerCause
                )
                val failingStore = SecretStoreV2(
                    encryptedPreferences = preferences,
                    chainAccountSecretValidation =
                        ChainAccountSecretValidation { _, _, _, _ ->
                            throw providerFailure
                        }
                )

                val thrown = assertThrows(
                    WalletSecureStorageUnavailableException::class.java
                ) {
                    runBlocking {
                        failingStore.getChainAccountSecrets(
                            metaId = fixtureMetaId,
                            accountId = accountId,
                            expectedPublicKey = fixture.keypair.publicKey,
                            expectedCryptoType = fixture.cryptoType
                        )
                    }
                }

                assertEquals(providerFailure.message, thrown.message)
                assertSame(providerCause, thrown.rootCause())
                assertEquals(exactPayload, preferences.getDecryptedString(activeKey))
                assertFalse(
                    preferences.hasKey(WalletSecretQuarantine.keyFor(activeKey))
                )

                val retryStore = SecretStoreV2(
                    encryptedPreferences = preferences,
                    chainAccountSecretValidation =
                        ChainAccountSecretValidation { encoded, _, _, _ -> encoded }
                )
                val retried = retryStore.getChainAccountSecrets(
                    metaId = fixtureMetaId,
                    accountId = accountId,
                    expectedPublicKey = fixture.keypair.publicKey,
                    expectedCryptoType = fixture.cryptoType
                )

                assertEquals(exactPayload, preferences.getDecryptedString(activeKey))
                assertArrayEquals(
                    fixture.keypair.privateKey,
                    retried!![ChainAccountSecrets.Keypair][PrivateKey]
                )
            }
        }

    private fun createMetaSecrets(
        derivationPath: String? = null,
    ): EncodableStruct<MetaAccountSecrets> {
        return MetaAccountSecrets(
            substrateDerivationPath = derivationPath,
            substrateKeyPair = Keypair(
                privateKey = byteArrayOf(),
                publicKey = byteArrayOf()
            )
        )
    }

    private fun createChainSecrets(
        derivationPath: String? = null,
    ): EncodableStruct<ChainAccountSecrets> {
        return ChainAccountSecrets(
            derivationPath = derivationPath,
            keyPair = CHAIN_KEYPAIR
        )
    }

    private data class RuntimeFixture(
        val cryptoType: CryptoType,
        val keypair: FearlessKeypair
    )

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while (current.cause != null && current.cause !== current) {
            current = checkNotNull(current.cause)
        }
        return current
    }
}
