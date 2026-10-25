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
import jp.co.soramitsu.common.data.storage.encrypt.WalletPublicIdentityRecovery
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard
import jp.co.soramitsu.core.models.Asset
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    fun `reviewed wallet crypto is not taken from an earlier equal account`() = runBlocking {
        val earlier = META_ACCOUNT.copy(id = META_ID - 1, isSelected = false,
            substrateCryptoType = CryptoType.SR25519, substratePublicKey = ACCOUNT_ID)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(earlier, META_ACCOUNT))
        `when`(accountRepository.findMetaAccount(ACCOUNT_ID)).thenReturn(earlier)
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(META_ACCOUNT)
        assertEquals(CryptoType.ECDSA, reviewedWalletProvider().getCryptoTypeFor(SUBSTRATE_CHAIN, ACCOUNT_ID))
        verifyNoInteractions(secretStoreV2, substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `an earlier quarantined duplicate cannot block the healthy reviewed wallet`() = runBlocking {
        configureDuplicateWallets()
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("${META_ID - 1}:SUBSTRATE_SECRETS"), QUARANTINED_CIPHERTEXT)
        val actual = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actual.publicKey)
        verify(substrateSecretStore).getAuthorized(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        Unit
    }

    @Test
    fun `an earlier healthy duplicate cannot bypass the reviewed wallets quarantine`() = runBlocking {
        configureDuplicateWallets()
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS"), QUARANTINED_CIPHERTEXT)
        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent) }
        }
        verifyNoInteractions(substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `equal healthy accounts read only the reviewed wallet namespace`() = runBlocking {
        configureDuplicateWallets()
        val actual = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actual.publicKey)
        verify(substrateSecretStore).getAuthorized(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        org.mockito.Mockito.verifyNoMoreInteractions(substrateSecretStore)
    }

    @Test
    fun `unique healthy reviewed wallet retains its exact public key`() = runBlocking {
        configureDuplicateWallets()
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(META_ACCOUNT))
        val actual = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actual.publicKey)
        verify(substrateSecretStore).getAuthorized(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        Unit
    }

    @Test
    fun `bound presence checks only the reviewed wallet without decryption`() = runBlocking {
        configureDuplicateWallets()
        `when`(substrateSecretStore.hasSecret(META_ID)).thenReturn(true)
        assertTrue(reviewedWalletProvider().hasLocalSubstrateKey(SUBSTRATE_CHAIN, ACCOUNT_ID))
        verify(substrateSecretStore).hasSecret(META_ID)
        org.mockito.Mockito.verifyNoMoreInteractions(substrateSecretStore)
        verifyNoInteractions(ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `bound ordinary read uses the reviewed wallet namespace`() = runBlocking {
        configureDuplicateWallets()
        stubRootSubstrateKey()
        val actual = reviewedWalletProvider().getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actual.publicKey)
        verify(substrateSecretStore).get(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID)
        org.mockito.Mockito.verifyNoMoreInteractions(substrateSecretStore)
    }

    @Test
    fun `removed reviewed wallet never falls back to a duplicate`() = runBlocking {
        configureDuplicateWallets()
        `when`(accountRepository.getMetaAccount(META_ID)).thenThrow(IllegalStateException("Removed local wallet"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent) }
        }
        verifyNoInteractions(secretStoreV2, substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `wrong wallet id changed account and empty account fail before secret access`() = runBlocking {
        for ((wallet, account) in listOf(
            META_ACCOUNT.copy(id = META_ID - 1) to ACCOUNT_ID,
            META_ACCOUNT.copy(substrateAccountId = ByteArray(32) { 9 }) to ACCOUNT_ID,
            META_ACCOUNT.copy(substrateAccountId = byteArrayOf()) to byteArrayOf()
        )) {
            `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(wallet)
            assertThrows(IllegalStateException::class.java) {
                runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, account, namespaceGuard, namespaceIntent) }
            }
        }
        verifyNoInteractions(secretStoreV2, substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `foreign chain account metadata cannot redirect a bound read`() = runBlocking {
        val foreign = MetaAccount.ChainAccount(META_ID - 1, null, SUBSTRATE_PUBLIC_KEY, ACCOUNT_ID, CryptoType.ECDSA, "Foreign")
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(
            META_ACCOUNT.copy(chainAccounts = mapOf(SUBSTRATE_CHAIN.id to foreign)))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent) }
        }
        verifyNoInteractions(secretStoreV2, substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `revocation during exact wallet lookup prevents physical root reads`() = runBlocking {
        `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        var allowed = true
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                check(allowed)
                return operation()
            }
        }
        whenever(accountRepository.getMetaAccount(META_ID)).doSuspendableAnswer { allowed = false; META_ACCOUNT }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, guard, namespaceIntent) }
        }
        verifyNoInteractions(substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `bound chain key reads exact namespace despite root and duplicate quarantine`() = runBlocking {
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(ECDSA_CHAIN_META_ACCOUNT.copy(id = META_ID - 1), ECDSA_CHAIN_META_ACCOUNT))
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(ECDSA_CHAIN_META_ACCOUNT)
        `when`(secretStoreV2.hasChainSecrets(META_ID, ECDSA_ACCOUNT_ID)).thenReturn(true)
        `when`(secretStoreV2.getAuthorizedChainAccountKeypair(META_ID, ECDSA_ACCOUNT_ID, ECDSA_KEYPAIR.publicKey,
            CryptoType.ECDSA, namespaceGuard, namespaceIntent)).thenReturn(ECDSA_KEYPAIR)
        encryptedPreferences.putEncryptedString(WalletSecretQuarantine.chainAccountKey(META_ID - 1, ECDSA_ACCOUNT_ID), QUARANTINED_CIPHERTEXT)
        encryptedPreferences.putEncryptedString(WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS"), QUARANTINED_CIPHERTEXT)
        assertEquals(CryptoType.ECDSA, reviewedWalletProvider().getCryptoTypeFor(SUBSTRATE_CHAIN, ECDSA_ACCOUNT_ID))
        val key = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ECDSA_ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(ECDSA_KEYPAIR.publicKey, key.publicKey)
        verify(secretStoreV2).hasChainSecrets(META_ID, ECDSA_ACCOUNT_ID)
        verify(secretStoreV2).getAuthorizedChainAccountKeypair(META_ID, ECDSA_ACCOUNT_ID, ECDSA_KEYPAIR.publicKey,
            CryptoType.ECDSA, namespaceGuard, namespaceIntent)
        org.mockito.Mockito.verifyNoMoreInteractions(secretStoreV2)
        verifyNoInteractions(substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `bound chain quarantine cannot be bypassed by an equal healthy account`() = runBlocking {
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(ECDSA_CHAIN_META_ACCOUNT.copy(id = META_ID - 1), ECDSA_CHAIN_META_ACCOUNT))
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(ECDSA_CHAIN_META_ACCOUNT)
        encryptedPreferences.putEncryptedString(WalletSecretQuarantine.chainAccountKey(META_ID, ECDSA_ACCOUNT_ID), QUARANTINED_CIPHERTEXT)
        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ECDSA_ACCOUNT_ID, namespaceGuard, namespaceIntent) }
        }
        verifyNoInteractions(secretStoreV2, substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `real root store decodes only the reviewed namespace and leaves both wallets unchanged`() = runBlocking {
        installRealStores()
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(META_ACCOUNT.copy(id = META_ID - 1), META_ACCOUNT))
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(META_ACCOUNT)
        encryptedPreferences.putEncryptedString("${META_ID - 1}:SUBSTRATE_SECRETS", "local-malformed-fixture")
        substrateSecretStore.put(META_ID, SubstrateSecrets(substrateKeyPair = Keypair(SUBSTRATE_PUBLIC_KEY, KEYPAIR_PRIVATE_KEY)))
        val before = encryptedPreferences.snapshot()
        val key = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, key.publicKey)
        assertTrue(encryptedPreferences.decryptedKeys.contains("$META_ID:SUBSTRATE_SECRETS"))
        assertTrue(encryptedPreferences.decryptedKeys.none { it.startsWith("${META_ID - 1}:") })
        assertEquals(before, encryptedPreferences.snapshot())
    }

    @Test
    fun `real chain store decodes the reviewed key while root and duplicate remain quarantined`() = runBlocking {
        installRealStores()
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(ECDSA_CHAIN_META_ACCOUNT.copy(id = META_ID - 1), ECDSA_CHAIN_META_ACCOUNT))
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(ECDSA_CHAIN_META_ACCOUNT)
        secretStoreV2.putChainAccountSecrets(META_ID, ECDSA_ACCOUNT_ID,
            ChainAccountSecrets(keyPair = Keypair(ECDSA_KEYPAIR.publicKey, ECDSA_KEYPAIR.privateKey)))
        encryptedPreferences.putEncryptedString(WalletSecretQuarantine.chainAccountKey(META_ID - 1, ECDSA_ACCOUNT_ID), QUARANTINED_CIPHERTEXT)
        encryptedPreferences.putEncryptedString(WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS"), QUARANTINED_CIPHERTEXT)
        val before = encryptedPreferences.snapshot()
        val key = reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ECDSA_ACCOUNT_ID, namespaceGuard, namespaceIntent)
        assertArrayEquals(ECDSA_KEYPAIR.publicKey, key.publicKey)
        assertTrue(encryptedPreferences.decryptedKeys.any { it.startsWith("$META_ID:") && it.endsWith(":ACCESS_SECRETS") })
        assertTrue(encryptedPreferences.decryptedKeys.none { it.startsWith("${META_ID - 1}:") || it == "$META_ID:SUBSTRATE_SECRETS" })
        assertEquals(before, encryptedPreferences.snapshot())
    }

    @Test
    fun `revoked exact lookup cannot reach real root or chain preference reads`() = runBlocking {
        for (chainKey in listOf(false, true)) {
            setUp()
            installRealStores()
            val wallet = if (chainKey) ECDSA_CHAIN_META_ACCOUNT else META_ACCOUNT
            val account = if (chainKey) ECDSA_ACCOUNT_ID else ACCOUNT_ID
            if (chainKey) secretStoreV2.putChainAccountSecrets(META_ID, account,
                ChainAccountSecrets(keyPair = Keypair(ECDSA_KEYPAIR.publicKey, ECDSA_KEYPAIR.privateKey)))
            else substrateSecretStore.put(META_ID, SubstrateSecrets(substrateKeyPair = Keypair(SUBSTRATE_PUBLIC_KEY, KEYPAIR_PRIVATE_KEY)))
            var allowed = true
            val guard = object : MutationExecutionGuard {
                override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                    check(allowed)
                    return operation()
                }
            }
            whenever(accountRepository.getMetaAccount(META_ID)).doSuspendableAnswer { allowed = false; wallet }
            val before = encryptedPreferences.snapshot()
            assertThrows(IllegalStateException::class.java) {
                runBlocking { reviewedWalletProvider().getAuthorizedKeypairFor(SUBSTRATE_CHAIN, account, guard, namespaceIntent) }
            }
            assertTrue(encryptedPreferences.decryptedKeys.isEmpty())
            assertEquals(before, encryptedPreferences.snapshot())
        }
    }

    private fun installRealStores() {
        secretStoreV2 = SecretStoreV2(encryptedPreferences)
        substrateSecretStore = SubstrateSecretStore(encryptedPreferences)
        ethereumSecretStore = EthereumSecretStore(encryptedPreferences)
        tonSecretStore = TonSecretStore(encryptedPreferences)
        repository = KeyPairRepository(secretStoreV2, ethereumSecretStore, substrateSecretStore, tonSecretStore,
            accountRepository, WalletSecretAccessGuard(encryptedPreferences))
    }

    // The reviewed id stays fixed even when another wallet has the same public account.
    private fun reviewedWalletProvider(): jp.co.soramitsu.core.extrinsic.keypair_provider.GuardedKeypairProvider = repository.forWallet(META_ID)
    private val namespaceIntent = "b".repeat(64)
    private val namespaceGuard = object : MutationExecutionGuard {
        override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
            check(intentSha256 == namespaceIntent)
            return operation()
        }
    }
    private suspend fun configureDuplicateWallets() {
        val earlier = META_ACCOUNT.copy(id = META_ID - 1, isSelected = false)
        `when`(accountRepository.allMetaAccounts()).thenReturn(listOf(earlier, META_ACCOUNT))
        `when`(accountRepository.getMetaAccount(META_ID)).thenReturn(META_ACCOUNT)
        for (id in listOf(META_ID - 1, META_ID)) {
            `when`(secretStoreV2.hasChainSecrets(id, ACCOUNT_ID)).thenReturn(false)
            `when`(substrateSecretStore.getAuthorized(id, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, namespaceGuard, namespaceIntent))
                .thenReturn(SubstrateSecrets(substrateKeyPair = Keypair(SUBSTRATE_PUBLIC_KEY, KEYPAIR_PRIVATE_KEY)))
        }
    }

    @Test
    fun `revocation during account lookup prevents physical secret reads`() = runBlocking {
        `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        var allowed = true
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                check(allowed)
                return operation()
            }
        }
        whenever(accountRepository.allMetaAccounts()).doSuspendableAnswer {
            allowed = false
            listOf(META_ACCOUNT)
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, guard, "a".repeat(64)) }
        }
        verifyNoInteractions(substrateSecretStore, ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `local substrate capability checks only secret presence`() = runBlocking {
        `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        `when`(substrateSecretStore.hasSecret(META_ID)).thenReturn(true)
        assertTrue(repository.hasLocalSubstrateKey(SUBSTRATE_CHAIN, ACCOUNT_ID))
        org.mockito.Mockito.verify(substrateSecretStore).hasSecret(META_ID)
        org.mockito.Mockito.verifyNoMoreInteractions(substrateSecretStore)
        verifyNoInteractions(ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `authorized root read preserves validated key identity within the lease`() = runBlocking {
        `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        val expected = Keypair(SUBSTRATE_PUBLIC_KEY, KEYPAIR_PRIVATE_KEY)
        var checks = 0
        val guard = object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T {
                assertEquals("a".repeat(64), intentSha256)
                checks++
                return operation()
            }
        }
        `when`(substrateSecretStore.getAuthorized(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, guard, "a".repeat(64)))
            .thenReturn(SubstrateSecrets(substrateKeyPair = expected))
        val actual = repository.getAuthorizedKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID, guard, "a".repeat(64))
        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, actual.publicKey)
        assertArrayEquals(KEYPAIR_PRIVATE_KEY, actual.privateKey)
        assertEquals(2, checks) // Entry and post-lookup check; concrete storage rechecks at decryption.
        verify(substrateSecretStore).getAuthorized(META_ID, SUBSTRATE_PUBLIC_KEY, CryptoType.ECDSA, ACCOUNT_ID, guard, "a".repeat(64))
        verifyNoInteractions(ethereumSecretStore, tonSecretStore)
    }

    @Test
    fun `root quarantine throws typed recovery error before any secret store read`() {
        runBlocking {
            `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        }
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.keyFor("$META_ID:SUBSTRATE_SECRETS"),
            QUARANTINED_CIPHERTEXT
        )

        assertThrows(WalletRecoveryRequiredException::class.java) {
            runBlocking {
                repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID)
            }
        }

        // Only the presence check that decides which key signs.
        runBlocking { verify(secretStoreV2).hasChainSecrets(META_ID, ACCOUNT_ID) }
        org.mockito.Mockito.verifyNoMoreInteractions(secretStoreV2)
        verifyNoInteractions(
            substrateSecretStore,
            ethereumSecretStore,
            tonSecretStore
        )
    }

    @Test
    fun `quarantined ethereum or ton key never blocks substrate signing`() {
        listOf("ETHEREUM_SECRETS", "TON_SECRETS", "ETHEREUM_SECRETS:marker").forEach { flagged ->
            setUp()
            if (flagged.endsWith(":marker")) {
                encryptedPreferences.putEncryptedString(
                    WalletPublicIdentityRecovery.keyFor(META_ID, "$META_ID:ETHEREUM_SECRETS"),
                    WalletPublicIdentityRecovery.MARKER_VALUE
                )
            } else {
                encryptedPreferences.putEncryptedString(
                    WalletSecretQuarantine.keyFor("$META_ID:$flagged"),
                    QUARANTINED_CIPHERTEXT
                )
            }
            stubRootSubstrateKey()

            val keypair = runBlocking { repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID) }

            assertArrayEquals(flagged, SUBSTRATE_PUBLIC_KEY, keypair.publicKey)
            assertTrue(runBlocking { repository.hasLocalSubstrateKey(SUBSTRATE_CHAIN, ACCOUNT_ID) })
        }
    }

    @Test
    fun `quarantined key of another network account never blocks root signing`() {
        val otherAccountId = ByteArray(32) { (it + 3).toByte() }
        runBlocking {
            `when`(accountRepository.allMetaAccounts()).thenReturn(
                listOf(
                    META_ACCOUNT.copy(
                        chainAccounts = mapOf(
                            "other-chain" to MetaAccount.ChainAccount(
                                metaId = META_ID,
                                chain = null,
                                publicKey = CHAIN_PUBLIC_KEY,
                                accountId = otherAccountId,
                                cryptoType = CryptoType.SR25519,
                                accountName = "Other network"
                            )
                        )
                    )
                )
            )
        }
        encryptedPreferences.putEncryptedString(
            WalletSecretQuarantine.chainAccountKey(META_ID, otherAccountId),
            QUARANTINED_CIPHERTEXT
        )
        stubRootSubstrateKey()

        val keypair = runBlocking { repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID) }

        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, keypair.publicKey)
    }

    @Test
    fun `bitcoin row never blocks substrate signing`() {
        // LegacyNetworkAccountUpgrade records the 33-byte compressed public
        // key as the Bitcoin account id; it has no key of its own (A6).
        val bitcoinPublicKey = ByteArray(33) { if (it == 0) 2 else it.toByte() }
        runBlocking {
            `when`(accountRepository.allMetaAccounts()).thenReturn(
                listOf(
                    META_ACCOUNT.copy(
                        chainAccounts = mapOf(
                            "bitcoin" to MetaAccount.ChainAccount(
                                metaId = META_ID,
                                chain = null,
                                publicKey = bitcoinPublicKey,
                                accountId = bitcoinPublicKey.clone(),
                                cryptoType = CryptoType.ECDSA,
                                accountName = "Bitcoin"
                            )
                        )
                    )
                )
            )
        }
        stubRootSubstrateKey()

        val keypair = runBlocking { repository.getKeypairFor(SUBSTRATE_CHAIN, ACCOUNT_ID) }

        assertArrayEquals(SUBSTRATE_PUBLIC_KEY, keypair.publicKey)
    }

    private fun stubRootSubstrateKey() {
        runBlocking {
            `when`(secretStoreV2.hasChainSecrets(META_ID, ACCOUNT_ID)).thenReturn(false)
        }
        `when`(
            substrateSecretStore.get(
                metaId = META_ID,
                expectedPublicKey = SUBSTRATE_PUBLIC_KEY,
                expectedCryptoType = CryptoType.ECDSA,
                expectedAccountId = ACCOUNT_ID
            )
        ).thenReturn(
            SubstrateSecrets(substrateKeyPair = Keypair(SUBSTRATE_PUBLIC_KEY, KEYPAIR_PRIVATE_KEY))
        )
        `when`(substrateSecretStore.hasSecret(META_ID)).thenReturn(true)
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
        val decryptedKeys = java.util.concurrent.CopyOnWriteArrayList<String>()

        fun snapshot(): Map<String, String> = values.toMap()

        override fun putEncryptedString(field: String, value: String) {
            values[field] = value
        }

        override fun getDecryptedString(field: String): String? {
            decryptedKeys += field
            return values[field]
        }

        // In-memory fixture seam; this exercises real decoders, not Android encrypted preferences.
        override fun getAuthorizedDecryptedStringSnapshot(
            field: String, guard: MutationExecutionGuard, intentSha256: String
        ): EncryptedPreferenceSnapshot? = guard.runIfAuthorized(intentSha256) { getDecryptedStringSnapshot(field) }

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
