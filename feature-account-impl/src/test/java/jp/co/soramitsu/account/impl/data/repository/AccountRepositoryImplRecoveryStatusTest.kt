package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.account.impl.domain.LegacyNetworkAccountUpgrade
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.common.data.secrets.v1.Keypair
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.RecoveryAssessment
import jp.co.soramitsu.common.data.secrets.v3.RecoveryIssue
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.secrets.v3.TonSecrets
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryItem
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryKind
import jp.co.soramitsu.common.data.storage.encrypt.WalletRecoveryRequiredException
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretQuarantine
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretScope
import jp.co.soramitsu.common.data.storage.encrypt.quarantineEncryptedStringSnapshotDurably
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.DEFAULT_DERIVATION_PATH
import jp.co.soramitsu.common.utils.deriveSeed32
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.junction.BIP32JunctionDecoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as FearlessKeypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.mnemonic.MnemonicCreator
import jp.co.soramitsu.fearless_utils.encrypt.seed.ethereum.EthereumSeedFactory
import jp.co.soramitsu.fearless_utils.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.ton.api.pk.PrivateKeyEd25519
import org.ton.mnemonic.Mnemonic

/**
 * Recovery is scoped to the one key it is about (addendum U0.2, android-keys
 * K3): a quarantined Ethereum key leaves Substrate and TON signing, export
 * and backup working, a Bitcoin row never flags a wallet, and the status API
 * reports each item with its key.
 */
class AccountRepositoryImplRecoveryStatusTest {

    private val preferences = HashMapEncryptedPreferences()
    private val accountDataSource = mock<AccountDataSource>()

    @Test
    fun deviceAuthenticatedViewOnlyBlocksEverySigningScopeWithoutFlaggingOrQuarantiningKeys() = runTest {
        storeVerifiedWallet()
        val repository = repository()
        jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess.enter()
        try {
            for (scope in listOf(WalletSecretScope.Substrate, WalletSecretScope.Ethereum, WalletSecretScope.Ton)) {
                assertTrue(repository.isSigningRecoveryRequired(META_ID, scope))
            }
            assertTrue(repository.isSigningRecoveryRequired(META_ID, SUBSTRATE_CHAIN))
            assertTrue(repository.isSigningRecoveryRequired(META_ID, ETHEREUM_CHAIN))
            assertTrue(runCatching { repository.getSubstrateSecrets(META_ID) }.exceptionOrNull() is
                jp.co.soramitsu.common.data.storage.pin.WalletPinRecoveryRequiredException)
            assertTrue(runCatching { repository.getEthereumSecrets(META_ID) }.exceptionOrNull() is
                jp.co.soramitsu.common.data.storage.pin.WalletPinRecoveryRequiredException)
            assertTrue(runCatching { repository.getTonSecrets(META_ID) }.exceptionOrNull() is
                jp.co.soramitsu.common.data.storage.pin.WalletPinRecoveryRequiredException)
            assertFalse(repository.isWalletRecoveryRequired(META_ID))
            assertTrue(repository.walletRecoveryStatus(META_ID).items.isEmpty())
        } finally { jp.co.soramitsu.common.data.storage.pin.WalletViewOnlyAccess.pinResetCompleted() }
        assertFalse(repository.isSigningRecoveryRequired(META_ID, ETHEREUM_CHAIN))
        assertTrue(repository.getEthereumSecrets(META_ID) != null)
    }

    @Test
    fun quarantinedEthereumKeyLeavesSubstrateAndTonUsable() = runTest {
        val wallet = storeVerifiedWallet()
        // What the 76 -> 77 integrity pass of internal build 230 did with a
        // key it rejected: the exact ciphertext moved to the quarantine.
        quarantine("$META_ID:ETHEREUM_SECRETS")
        val repository = repository()

        assertArrayEquals(ENTROPY, repository.getSubstrateSecrets(META_ID)?.get(SubstrateSecrets.Entropy))
        assertArrayEquals(
            wallet.tonPhrase.encodeToByteArray(),
            repository.getTonSecrets(META_ID)?.get(TonSecrets.Seed)
        )
        assertThrows(WalletRecoveryRequiredException::class.java) {
            kotlinx.coroutines.runBlocking { repository.getEthereumSecrets(META_ID) }
        }

        assertFalse(repository.isSigningRecoveryRequired(META_ID, WalletSecretScope.Substrate))
        assertFalse(repository.isSigningRecoveryRequired(META_ID, WalletSecretScope.Ton))
        assertTrue(repository.isSigningRecoveryRequired(META_ID, WalletSecretScope.Ethereum))
        assertFalse(repository.isSigningRecoveryRequired(META_ID, SUBSTRATE_CHAIN))
        assertTrue(repository.isSigningRecoveryRequired(META_ID, ETHEREUM_CHAIN))

        val status = repository.walletRecoveryStatus(META_ID)
        assertEquals(
            listOf(
                WalletRecoveryItem(
                    metaId = META_ID,
                    scope = WalletSecretScope.Ethereum,
                    kind = WalletRecoveryKind.QUARANTINED,
                    sourceKey = "$META_ID:ETHEREUM_SECRETS"
                )
            ),
            status.items
        )
        assertTrue(repository.isWalletRecoveryRequired(META_ID))

        // Backup still offers what the Substrate key supports.
        assertEquals(
            setOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED, BackupAccountType.JSON),
            repository.getSupportedBackupTypes(META_ID)
        )
        assertEquals(
            BackupAccountType.PASSPHRASE,
            repository.getBestBackupType(META_ID, WalletEcosystem.Substrate)
        )
        assertNull(repository.getBestBackupType(META_ID, WalletEcosystem.Ethereum))
        assertTrue(repository.isGoogleBackupSupported(META_ID))
        assertEquals(
            RecoveryAssessment.Verified,
            repository.recoveryAssessment(META_ID, WalletEcosystem.Substrate)
        )
        assertNull(repository.recoveryAssessment(META_ID, WalletEcosystem.Ethereum))
    }

    @Test
    fun missingEthereumKeyIsReportedWithoutBlockingTheWallet() = runTest {
        storeVerifiedWallet()
        preferences.removeKey("$META_ID:ETHEREUM_SECRETS")
        val repository = repository()

        assertEquals(
            listOf(
                WalletRecoveryItem(
                    metaId = META_ID,
                    scope = WalletSecretScope.Ethereum,
                    kind = WalletRecoveryKind.MISSING_KEY,
                    sourceKey = "$META_ID:ETHEREUM_SECRETS"
                )
            ),
            repository.walletRecoveryStatus(META_ID).items
        )
        assertFalse(repository.isWalletRecoveryRequired(META_ID))
        assertFalse(repository.isSigningRecoveryRequired(META_ID, WalletSecretScope.Substrate))
    }

    @Test
    fun unverifiedRecoveryMaterialOffersOnlyJsonBackup() = runTest {
        val substrate = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = ByteArray(32) { 11 },
            junctions = emptyList()
        )
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(
            wallet(substrate.publicKey, ethereumPublicKey = null, tonPublicKey = null)
        )
        // Entropy that does not re-derive the stored key (audit AND-D7).
        SubstrateSecretStore(preferences).put(
            META_ID,
            SubstrateSecrets(substrateKeyPair = substrate, entropy = ByteArray(16) { 13 })
        )
        val repository = repository()

        val assessment = repository.recoveryAssessment(META_ID, WalletEcosystem.Substrate)
        assertTrue(assessment is RecoveryAssessment.Unverified)
        assertTrue(
            RecoveryIssue.ENTROPY_MISMATCH in (assessment as RecoveryAssessment.Unverified).issues
        )
        assertEquals(setOf(BackupAccountType.JSON), repository.getSupportedBackupTypes(META_ID))
        assertNull(repository.getBestBackupType(META_ID, WalletEcosystem.Substrate))
        assertFalse(repository.isGoogleBackupSupported(META_ID))
        assertFalse(repository.isWalletRecoveryRequired(META_ID))
    }

    @Test
    fun unverifiedEntropyNeverDerivesNewNetworkAccounts() = runTest {
        val substrate = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = ByteArray(32) { 11 },
            junctions = emptyList()
        )
        val wallet = wallet(substrate.publicKey, ethereumPublicKey = null, tonPublicKey = null)
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(wallet)
        whenever(accountDataSource.allMetaAccounts()).thenReturn(listOf(wallet))
        SubstrateSecretStore(preferences).put(
            META_ID,
            SubstrateSecrets(substrateKeyPair = substrate, entropy = ByteArray(16) { 13 })
        )
        val dao = mock<MetaAccountDao>()
        whenever(dao.getMetaAccount(META_ID)).thenReturn(MetaAccountLocal(
            wallet.substratePublicKey, wallet.substrateCryptoType, wallet.substrateAccountId,
            wallet.ethereumPublicKey, wallet.ethereumAddress, wallet.tonPublicKey,
            wallet.name, wallet.isSelected, 0, wallet.isBackedUp, wallet.googleBackupAddress, wallet.initialized
        ).apply { id = META_ID })
        val coordinator = mock<WalletSecretMutationCoordinator>()

        LegacyNetworkAccountUpgrade(repository(), dao, coordinator) {
            setOf(
                UniversalWalletRegistry.bitcoinMainnet.chainId,
                UniversalWalletRegistry.bitcoinTestnet.chainId,
                UniversalWalletRegistry.solanaMainnet.chainId,
                UniversalWalletRegistry.taira.chainId
            )
        }.upgrade()

        // A phrase that does not restore this wallet must not seed new
        // Bitcoin, Solana, Iroha or TON addresses.
        verify(dao, never()).insertDerivedChainAccountsIfAbsent(any())
        verifyNoInteractions(coordinator)
    }

    @Test
    fun bitcoinRowNeverMakesTheWalletRecoveryRequired() = runTest {
        val stored = storeVerifiedWallet()
        // LegacyNetworkAccountUpgrade records the 33-byte compressed public
        // key as the Bitcoin account id (android-keys A6).
        val bitcoinPublicKey = ByteArray(33) { if (it == 0) 2 else it.toByte() }
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(
            stored.metaAccount.copy(
                chainAccounts = mapOf(
                    UniversalWalletRegistry.bitcoinMainnet.chainId to MetaAccount.ChainAccount(
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
        val repository = repository()

        assertTrue(repository.walletRecoveryStatus(META_ID).items.isEmpty())
        assertFalse(repository.isWalletRecoveryRequired(META_ID))
        assertArrayEquals(ENTROPY, repository.getSubstrateSecrets(META_ID)?.get(SubstrateSecrets.Entropy))
    }

    private fun quarantine(activeKey: String) {
        val snapshot = checkNotNull(preferences.getDecryptedStringSnapshot(activeKey))
        preferences.quarantineEncryptedStringSnapshotDurably(
            sourceKey = activeKey,
            quarantineKey = WalletSecretQuarantine.keyFor(activeKey),
            expectedSnapshot = snapshot
        )
    }

    private suspend fun storeVerifiedWallet(): StoredWallet {
        val mnemonic = MnemonicCreator.fromEntropy(ENTROPY)
        val substrateSeed = SubstrateSeedFactory.deriveSeed32(mnemonic.words, null).seed
        val substrate = SubstrateKeypairFactory.generate(
            encryptionType = EncryptionType.ECDSA,
            seed = substrateSeed,
            junctions = emptyList()
        )
        val ethereumPath = BIP32JunctionDecoder.decode(BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH)
        val ethereum = EthereumKeypairFactory.generate(
            seed = EthereumSeedFactory.deriveSeed32(mnemonic.words, ethereumPath.password).seed,
            junctions = ethereumPath.junctions
        )
        val ton = tonKeypair(mnemonic.words)
        val metaAccount = wallet(substrate.publicKey, ethereum.publicKey, ton.publicKey)
        whenever(accountDataSource.getMetaAccount(META_ID)).thenReturn(metaAccount)
        SubstrateSecretStore(preferences).put(
            META_ID,
            SubstrateSecrets(substrateKeyPair = substrate, entropy = mnemonic.entropy, seed = substrateSeed)
        )
        EthereumSecretStore(preferences).put(
            META_ID,
            EthereumSecrets(
                entropy = mnemonic.entropy,
                seed = ethereum.privateKey,
                ethereumKeypair = ethereum,
                ethereumDerivationPath = BIP32JunctionDecoder.DEFAULT_DERIVATION_PATH
            )
        )
        TonSecretStore(preferences).put(
            META_ID,
            TonSecrets(seed = mnemonic.words.encodeToByteArray(), tonKeypair = ton)
        )
        return StoredWallet(metaAccount, mnemonic.words)
    }

    private class StoredWallet(val metaAccount: MetaAccount, val tonPhrase: String)

    private fun repository() = AccountRepositoryImpl(
        accountDataSource = accountDataSource,
        metaAccountDao = mock<MetaAccountDao>(),
        legacyStoreV2 = SecretStoreV2(preferences),
        jsonSeedDecoder = mock<JsonSeedDecoder>(),
        jsonSeedEncoder = mock<JsonSeedEncoder>(),
        languagesHolder = mock<LanguagesHolder>(),
        chainsRepository = mock<ChainsRepository>(),
        nomisScoresDao = mock<NomisScoresDao>(),
        substrateSecretStore = SubstrateSecretStore(preferences),
        ethereumSecretStore = EthereumSecretStore(preferences),
        tonSecretStore = TonSecretStore(preferences),
        accountRepositoryDelegate = mock<AccountRepositoryDelegate>(),
        walletSecretMutationCoordinator = mock<WalletSecretMutationCoordinator>(),
        walletSecretAccessGuard = WalletSecretAccessGuard(preferences),
        dispatcher = UnconfinedTestDispatcher()
    )

    private fun wallet(
        substratePublicKey: ByteArray,
        ethereumPublicKey: ByteArray?,
        tonPublicKey: ByteArray?
    ) = MetaAccount(
        id = META_ID,
        chainAccounts = emptyMap(),
        favoriteChains = emptyMap(),
        substratePublicKey = substratePublicKey,
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = substratePublicKey.substrateAccountId(),
        ethereumAddress = ethereumPublicKey?.ethereumAddressFromPublicKey(),
        ethereumPublicKey = ethereumPublicKey,
        tonPublicKey = tonPublicKey,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Scoped recovery wallet",
        initialized = true
    )

    private fun tonKeypair(typedText: String): FearlessKeypair {
        // Exactly how 4.2.0 TonAccountRepository.create derived the key.
        val privateKey = PrivateKeyEd25519(Mnemonic.toSeed(typedText.split(" ")))
        return Keypair(
            publicKey = privateKey.publicKey().key.toByteArray(),
            privateKey = privateKey.key.toByteArray()
        )
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }

        const val META_ID = 7L
        val ENTROPY = ByteArray(16) { 21 }

        val SUBSTRATE_CHAIN = testChain("scoped-substrate", Ecosystem.Substrate, ethereumBased = false)
        val ETHEREUM_CHAIN = testChain("scoped-ethereum", Ecosystem.EthereumBased, ethereumBased = true)

        fun testChain(chainId: String, chainEcosystem: Ecosystem, ethereumBased: Boolean) = object : IChain {
            override val id = chainId
            override val paraId: String? = null
            override val assets: List<Asset> = emptyList()
            override val nodes: List<ChainNode> = emptyList()
            override val addressPrefix = 42
            override val isEthereumBased = ethereumBased
            override val parentId: String? = null
            override val ecosystem = chainEcosystem
        }
    }
}
