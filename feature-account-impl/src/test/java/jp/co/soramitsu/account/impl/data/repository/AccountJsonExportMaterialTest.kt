package jp.co.soramitsu.account.impl.data.repository

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.impl.data.repository.datasource.AccountDataSource
import jp.co.soramitsu.common.data.secrets.v2.ChainAccountSecrets
import jp.co.soramitsu.common.data.secrets.v2.SecretStoreV2
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecretStore
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecretStore
import jp.co.soramitsu.common.data.secrets.v3.SubstrateSecrets
import jp.co.soramitsu.common.data.secrets.v3.TonSecretStore
import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretAccessGuard
import jp.co.soramitsu.common.resources.LanguagesHolder
import jp.co.soramitsu.common.utils.ethereumAddressFromPublicKey
import jp.co.soramitsu.common.utils.substrateAccountId
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.NomisScoresDao
import jp.co.soramitsu.fearless_utils.encrypt.EncryptionType
import jp.co.soramitsu.fearless_utils.encrypt.MultiChainEncryption
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedDecoder
import jp.co.soramitsu.fearless_utils.encrypt.json.JsonSeedEncoder
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair
import jp.co.soramitsu.fearless_utils.encrypt.keypair.ethereum.EthereumKeypairFactory
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.testshared.HashMapEncryptedPreferences
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** JSON export must write the key and seed of the account it names, from one read of the wallet. */
class AccountJsonExportMaterialTest {

    private class Export(val keypair: Keypair, val seed: ByteArray?, val encryption: MultiChainEncryption)

    private val preferences = HashMapEncryptedPreferences()
    private val accounts = mock<AccountDataSource>()
    private val chains = mock<ChainsRepository>()
    private val encoder = mock<JsonSeedEncoder>()
    private val exports = mutableListOf<Export>()

    private val substrateSeed = ByteArray(32) { 21 }
    private val ethereumSeed = ByteArray(32) { 22 }
    // ECDSA keeps the test on the JVM (SR25519 needs the native library).
    private val substrate = SubstrateKeypairFactory.generate(EncryptionType.ECDSA, substrateSeed, emptyList())
    private val ethereum = EthereumKeypairFactory.createWithPrivateKey(ethereumSeed)

    private fun chain(id: String, ethereumBased: Boolean): Chain = mock<Chain>().also {
        whenever(it.id).thenReturn(id)
        whenever(it.name).thenReturn(id)
        whenever(it.isEthereumBased).thenReturn(ethereumBased)
        whenever(it.ecosystem).thenReturn(if (ethereumBased) Ecosystem.EthereumBased else Ecosystem.Substrate)
        whenever(it.addressPrefix).thenReturn(42)
    }

    private fun wallet(chainAccounts: Map<String, MetaAccount.ChainAccount> = emptyMap()) = MetaAccount(
        id = META_ID,
        chainAccounts = chainAccounts,
        favoriteChains = emptyMap(),
        substratePublicKey = substrate.publicKey,
        substrateCryptoType = CryptoType.ECDSA,
        substrateAccountId = substrate.publicKey.substrateAccountId(),
        ethereumAddress = ethereum.publicKey.ethereumAddressFromPublicKey(),
        ethereumPublicKey = ethereum.publicKey,
        tonPublicKey = null,
        isSelected = true,
        isBackedUp = true,
        googleBackupAddress = null,
        name = "Export wallet",
        initialized = true
    )

    private fun repository() = AccountRepositoryImpl(
        accountDataSource = accounts,
        metaAccountDao = mock<MetaAccountDao>(),
        legacyStoreV2 = SecretStoreV2(preferences),
        jsonSeedDecoder = mock<JsonSeedDecoder>(),
        jsonSeedEncoder = encoder,
        languagesHolder = mock<LanguagesHolder>(),
        chainsRepository = chains,
        nomisScoresDao = mock<NomisScoresDao>(),
        substrateSecretStore = SubstrateSecretStore(preferences),
        ethereumSecretStore = EthereumSecretStore(preferences),
        tonSecretStore = TonSecretStore(preferences),
        accountRepositoryDelegate = mock<AccountRepositoryDelegate>(),
        walletSecretMutationCoordinator = mock<WalletSecretMutationCoordinator>(),
        walletSecretAccessGuard = WalletSecretAccessGuard(preferences),
        dispatcher = UnconfinedTestDispatcher()
    )

    private suspend fun storeRootSecrets() {
        SubstrateSecretStore(preferences).put(META_ID, SubstrateSecrets(substrateKeyPair = substrate, seed = substrateSeed))
        EthereumSecretStore(preferences).put(META_ID, EthereumSecrets(seed = ethereum.privateKey, ethereumKeypair = ethereum))
        whenever(encoder.generate(any(), anyOrNull(), any(), any(), any(), any(), any())).thenAnswer {
            exports += Export(it.getArgument(0), it.getArgument(1), it.getArgument(4))
            "json"
        }
    }

    @Test
    fun `Ethereum export carries the Ethereum seed and never the Substrate seed`() = runTest {
        storeRootSecrets()
        val chain = chain("evm", ethereumBased = true)
        whenever(chains.getChain("evm")).thenReturn(chain)
        whenever(accounts.getMetaAccount(META_ID)).thenReturn(wallet())

        repository().generateRestoreJson(META_ID, "evm", "password")

        val export = exports.single()
        assertEquals(MultiChainEncryption.Ethereum, export.encryption)
        assertArrayEquals(ethereum.privateKey, export.keypair.privateKey)
        assertArrayEquals(ethereum.privateKey, export.seed)
        assertTrue(export.seed?.contentEquals(substrateSeed) != true)
    }

    @Test
    fun `Substrate export carries the Substrate seed`() = runTest {
        storeRootSecrets()
        val chain = chain("substrate", ethereumBased = false)
        whenever(chains.getChain("substrate")).thenReturn(chain)
        whenever(accounts.getMetaAccount(META_ID)).thenReturn(wallet())

        repository().generateRestoreJson(META_ID, "substrate", "password")

        val export = exports.single()
        assertArrayEquals(substrate.privateKey, export.keypair.privateKey)
        assertArrayEquals(substrateSeed, export.seed)
    }

    @Test
    fun `network account export uses the account of the wallet it read`() = runTest {
        storeRootSecrets()
        val chain = chain("network", ethereumBased = false)
        whenever(chains.getChain("network")).thenReturn(chain)
        val reviewed = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 31 }, emptyList())
        val replacement = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 32 }, emptyList())
        val store = SecretStoreV2(preferences)
        store.putChainAccountSecrets(META_ID, reviewed.publicKey, ChainAccountSecrets(keyPair = reviewed))
        store.putChainAccountSecrets(META_ID, replacement.publicKey, ChainAccountSecrets(keyPair = replacement))
        fun account(key: Keypair) = MetaAccount.ChainAccount(META_ID, chain, key.publicKey, key.publicKey, CryptoType.ED25519, "network")
        // A later read of the wallet would name a replaced network account.
        whenever(accounts.getMetaAccount(META_ID)).thenReturn(
            wallet(mapOf("network" to account(reviewed))),
            wallet(mapOf("network" to account(replacement)))
        )

        repository().generateRestoreJson(META_ID, "network", "password")

        val export = exports.single()
        assertArrayEquals(reviewed.privateKey, export.keypair.privateKey)
        assertNull(export.seed)
        verify(accounts, times(1)).getMetaAccount(META_ID)
    }

    @Test
    fun `network account secrets come from the requested wallet`() = runTest {
        val chain = chain("network", ethereumBased = false)
        val key = SubstrateKeypairFactory.generate(EncryptionType.ED25519, ByteArray(32) { 41 }, emptyList())
        SecretStoreV2(preferences).putChainAccountSecrets(META_ID, key.publicKey, ChainAccountSecrets(keyPair = key))
        val account = MetaAccount.ChainAccount(META_ID, chain, key.publicKey, key.publicKey, CryptoType.ED25519, "network")
        whenever(accounts.getMetaAccount(META_ID)).thenReturn(wallet(mapOf("network" to account)))

        val secrets = repository().getChainAccountSecrets(META_ID, "network")

        assertNotNull(secrets)
        assertNull(repository().getChainAccountSecrets(META_ID, "other"))
    }

    private companion object {
        const val META_ID = 7L

        @JvmStatic
        @BeforeClass
        fun enableByteBuddyForJava21TestRuntime() {
            System.setProperty("net.bytebuddy.experimental", "true")
        }
    }
}
