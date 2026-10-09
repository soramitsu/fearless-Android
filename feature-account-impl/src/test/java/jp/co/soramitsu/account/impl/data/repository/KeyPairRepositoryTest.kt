package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferences
import jp.co.soramitsu.common.data.storage.encrypt.EncryptedPreferenceSnapshot
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

class KeyPairRepositoryTest {

    private lateinit var encryptedPreferences: RecordingEncryptedPreferences
    private lateinit var secretStoreV2: SecretStoreV2
    private lateinit var ethereumSecretStore: EthereumSecretStore
    private lateinit var substrateSecretStore: SubstrateSecretStore
    private lateinit var tonSecretStore: TonSecretStore
    private lateinit var accountRepository: AccountRepository
    private lateinit var repository: KeyPairRepository

    @Before
    fun setUp() {
        encryptedPreferences = RecordingEncryptedPreferences()
        secretStoreV2 = mock(SecretStoreV2::class.java)
        ethereumSecretStore = mock(EthereumSecretStore::class.java)
        substrateSecretStore = mock(SubstrateSecretStore::class.java)
        tonSecretStore = mock(TonSecretStore::class.java)
        accountRepository = mock(AccountRepository::class.java)

        runBlocking {
            `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(META_ACCOUNT))
        }

        repository = KeyPairRepository(
            secretStoreV2 = secretStoreV2,
            ethereumSecretStore = ethereumSecretStore,
            substrateSecretStore = substrateSecretStore,
            tonSecretStore = tonSecretStore,
            accountRepository = accountRepository,
            walletSecretAccessGuard = WalletSecretAccessGuard(encryptedPreferences)
        )
    }

    @Test
    fun `root quarantine throws typed recovery error before any secret store access`() {
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS"),
            QUARANTINED_CIPHERTEXT
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
            }
        }

        verifyNoInteractions(
            secretStoreV2,
            substrateSecretStore,
            ethereumSecretStore,
            tonSecretStore
        )
    }

    @Test
    fun `chain account quarantine throws typed recovery error before any secret store access`() {
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.chainAccountKey(META_ID, ACCOUNT_ID),
            QUARANTINED_CIPHERTEXT
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
            }
        }

        verifyNoInteractions(
            secretStoreV2,
            substrateSecretStore,
            ethereumSecretStore,
            tonSecretStore
        )
    }

    @Test
    fun `clean wallet passes guard and returns substrate keypair`() {
        val expectedKeypair = Keypair(
            publicKey = SUBSTRATE_PUBLIC_KEY,
            privateKey = KEYPAIR_PRIVATE_KEY
        )
        val substrateSecrets = SubstrateSecrets(substrateKeyPair = expectedKeypair)

        runBlocking {
            `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID))
                .thenReturn(false)
        }
        `when`(
            substrateSecretStore.get(
                metaId = META_ID,
                expectedPublicKey = SUBSTRATE_PUBLIC_KEY,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = ACCOUNT_ID
            )
        ).thenReturn(substrateSecrets)

        val actualKeypair = runBlocking {
            repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
        }

        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actualKeypair.publicKey)
        assertArrayEquals(KEYPAIR_PRIVATE_KEY, actualKeypair.privateKey)
        assertTrue(
            encryptedPreferences.checkedKeys.contains(
                WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS")
            )
        )
        assertTrue(
            encryptedPreferences.checkedKeys.contains(
                WalletSecretQuarantine.chainAccountKey(META_ID, ACCOUNT_ID)
            )
        )
        runBlocking {
            verify(secretStoreV2).hasChainSecrets(META_ID, ACCOUNT_ID)
        }
        verify(substrateSecretStore).get(
            metaId = META_ID,
            expectedPublicKey = SUBSTRATE_PUBLIC_KEY,
            expectedCryptoType = CryptoType.ECDSA,
            expectedAccountId = ACCOUNT_ID
        )
        verifyNoInteractions(ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `decoded root key for another wallet throws typed recovery error`() {
        val swappedSecrets = SubstrateSecrets(
            substrateKeyPair = Keypair(
                publicKey = SWAPPED_PUBLIC_KEY,
                privateKey = KEYPAIR_PRIVATE_KEY
            )
        )
        runBlocking {
            `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID))
                .thenReturn(false)
        }
        `when`(
            substrateSecretStore.get(
                metaId = META_ID,
                expectedPublicKey = SUBSTRATE_PUBLIC_KEY,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = ACCOUNT_ID
            )
        ).thenReturn(swappedSecrets)

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
            }
        }

        runBlocking {
            verify(secretStoreV2).hasChainSecrets(META_ID, ACCOUNT_ID)
        }
        verify(substrateSecretStore).get(
            metaId = META_ID,
            expectedPublicKey = SUBSTRATE_PUBLIC_KEY,
            expectedCryptoType = CryptoType.ECDSA,
            expectedAccountId = ACCOUNT_ID
        )
        verifyNoInteractions(ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `decoded chain account key for another account throws typed recovery error`() {
        val swappedSecrets = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = SWAPPED_PUBLIC_KEY,
                privateKey = KEYPAIR_PRIVATE_KEY
            )
        )
        runBlocking {
            `when`(accountRepository.allMetaAccounts())
                .thenReturn(listOf(CHAIN_META_ACCOUNT))
            `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID))
                .thenReturn(true)
            `when`(
                secretStoreV2.getChainAccountSecrets(
                    META_ID,
                    ACCOUNT_ID,
                    CHAIN_PUBLIC_KEY,
                    CryptoType.SR25519
                )
            )
                .thenReturn(swappedSecrets)
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
            }
        }

        runBlocking {
            verify(secretStoreV2).hasChainSecrets(META_ID, ACCOUNT_ID)
            verify(secretStoreV2).getChainAccountSecrets(
                META_ID,
                ACCOUNT_ID,
                CHAIN_PUBLIC_KEY,
                CryptoType.SR25519
            )
        }
        verifyNoInteractions(
            substrateSecretStore,
            ethereumSecretStore,
            tonSecretStore
        )
    }

    @Test
    fun `chain account same public key with wrong private key is rejected defensively`() {
        val forgedSecrets = ChainAccountSecrets(
            keyPair = Keypair(
                publicKey = ECDSA_KEYPAIR.publicKey,
                privateKey = OTHER_ECDSA_KEYPAIR.privateKey
            )
        )
        runBlocking {
            `when`(accountRepository.allMetaAccounts())
                .thenReturn(listOf(ECDSA_CHAIN_META_ACCOUNT))
            `when`(secretStoreV2.hasChainSecrets(META_ID, ECDSA_ACCOUNT_ID))
                .thenReturn(true)
            `when`(
                secretStoreV2.getChainAccountSecrets(
                    META_ID,
                    ECDSA_ACCOUNT_ID,
                    ECDSA_KEYPAIR.publicKey,
                    CryptoType.ECDSA
                )
            ).thenReturn(forgedSecrets)
        }

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ECDSA_ACCOUNT_ID)
            }
        }

        verifyNoInteractions(
            substrateSecretStore,
            ethereumSecretStore,
            tonSecretStore
        )
    }

    private class RecordingEncryptedPreferences : EncryptedPreferences {

        private val values = mutableMapOf<String, String>()
        private val quarantineVersion = MutableStateFlow(0L)
        override val walletSecretQuarantineVersion: StateFlow<Long> =
            quarantineVersion
        val checkedKeys = mutableSetOf<String>()

        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? = values[field]

        override fun hasKey(field: String): Boolean {
            checkedKeys += field
            return values.containsKey(field)
        }

        override fun removeKey(field: String) {
            values.remove(field)
        }

        override fun replaceEncryptedStringsDurably(
            valuesToPut: Map<String, String>,
            keysToRemove: Set<String>
        ) {
            values.putAll(valuesToPut)
            keysToRemove.forEach(values::remove)
        }

        override fun quarantineEncryptedStringDurably(
            sourceKey: String,
            quarantineKey: String,
            expectedSnapshot: EncryptedPreferenceSnapshot
        ): Boolean {
            val current = values[sourceKey]
            if (current == null) {
                val quarantined = values[quarantineKey]
                checkNotNull(quarantined)
                return expectedSnapshot.matchesUnencryptedStorageValue(
                    quarantined
                )
            }
            if (!expectedSnapshot.matchesUnencryptedStorageValue(current)) {
                return false
            }
            values.remove(sourceKey)
            values[quarantineKey] = current
            quarantineVersion.value += 1
            return true
        }

        override fun requireDurableStorageHealthy() = Unit
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }

        const val META_ID = 42L
        const val QUARANTINED_CIPHERTEXT = "ciphertext-preserved-for-recovery"

        val ROOT_ECDSA_KEYPAIR = EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { (it * 5 + 1).toByte() }
        )
        val ACCOUNT_ID = ROOT_ECDSA_KEYPAIR.publicKey.substrateAccountId()
        val SUBSTRATE_PUBLIC_KEY = ROOT_ECDSA_KEYPAIR.publicKey
        val CHAIN_PUBLIC_KEY = ByteArray(32) { (it + 65).toByte() }
        val SWAPPED_PUBLIC_KEY = ByteArray(32) { (it + 97).toByte() }
        val KEYPAIR_PRIVATE_KEY = ROOT_ECDSA_KEYPAIR.privateKey
        val ECDSA_KEYPAIR = EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { (it * 7 + 3).toByte() }
        )
        val OTHER_ECDSA_KEYPAIR = EthereumKeypairFactory.createWithPrivateKey(
            ByteArray(32) { (it * 11 + 5).toByte() }
        )
        val ECDSA_ACCOUNT_ID = ECDSA_KEYPAIR.publicKey.substrateAccountId()

        val META_ACCOUNT = MetaAccount(
            id = META_ID,
            chainAccounts = emptyMap(),
            favoriteChains = emptyMap(),
            substratePublicKey = SUBSTRATE_PUBLIC_KEY,
            substrateCryptoType = CryptoType.ECDSA,
            substrateAccountId = ACCOUNT_ID,
            ethereumAddress = null,
            ethereumPublicKey = null,
            tonPublicKey = null,
            isSelected = true,
            isBackedUp = true,
            googleBackupAddress = null,
            name = "Test wallet",
            initialized = true
        )

        val SUBSTRATE_CHAIN = object : IChain {
            override val id = "test-substrate"
            override val paraId: String? = null
            override val assets: List<Asset> = emptyList()
            override val nodes: List<ChainNode> = emptyList()
            override val addressPrefix = 42
            override val isEthereumBased = false
            override val parentId: String? = null
            override val ecosystem = Ecosystem.Substrate
        }

        val CHAIN_META_ACCOUNT = META_ACCOUNT.copy(
            chainAccounts = mapOf(
                SUBSTRATE_CHAIN.id to MetaAccount.ChainAccount(
                    metaId = META_ID,
                    chain = null,
                    publicKey = CHAIN_PUBLIC_KEY,
                    accountId = ACCOUNT_ID,
                    cryptoType = CryptoType.SR25519,
                    accountName = "Test chain account"
                )
            )
        )

        val ECDSA_CHAIN_META_ACCOUNT = META_ACCOUNT.copy(
            chainAccounts = mapOf(
                SUBSTRATE_CHAIN.id to MetaAccount.ChainAccount(
                    metaId = META_ID,
                    chain = null,
                    publicKey = ECDSA_KEYPAIR.publicKey,
                    accountId = ECDSA_ACCOUNT_ID,
                    cryptoType = CryptoType.ECDSA,
                    accountName = "Test ECDSA chain account"
                )
            )
        )
    }
}
