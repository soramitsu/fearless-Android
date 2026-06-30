package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.TonSyncDataRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class BitcoinBalanceLoaderTest {

    @Test
    fun `loads native bitcoin balance from validated mainnet address`() = runBlocking {
        val chain = bitcoinChain(isTestNet = false)
        val account = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Mainnet)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = ACCOUNT_ID)
        val client = FakeBitcoinIndexerClient(
            addressResponse = BitcoinEsploraAddress(
                address = MAINNET_ADDRESS,
                chainStats = BitcoinEsploraStats(1, 1_500, 1, 300, 2),
                mempoolStats = BitcoinEsploraStats(1, 200, 1, 50, 2)
            )
        )

        val updates = BitcoinBalanceLoader(chain, client).loadBalance(setOf(metaAccount))

        assertEquals(listOf(MAINNET_ADDRESS), client.receivedAddresses)
        assertEquals(listOf(BitcoinIndexerRoutes.Network.Mainnet), client.receivedNetworks)
        assertEquals(listOf(UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL), client.receivedBaseUrls)
        assertEquals(1, updates.size)
        assertEquals(chain.id, updates.single().chainId)
        assertEquals("BTC", updates.single().id)
        assertTrue(ACCOUNT_ID.contentEquals(updates.single().accountId))
        assertEquals(BigInteger.valueOf(1_350), updates.single().freeInPlanks)
    }

    @Test
    fun `rejects malformed bitcoin public key before balance fetch`() = runBlocking {
        val chain = bitcoinChain(isTestNet = false)
        val metaAccount = metaAccount(chain, publicKey = ByteArray(32), accountId = ACCOUNT_ID)
        val client = FakeBitcoinIndexerClient()

        val updates = BitcoinBalanceLoader(chain, client).loadBalance(setOf(metaAccount))

        assertTrue(updates.isEmpty())
        assertTrue(client.receivedAddresses.isEmpty())
    }

    @Test
    fun `rejects negative bitcoin indexer balances`() = runBlocking {
        val chain = bitcoinChain(isTestNet = false)
        val account = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Mainnet)
        val metaAccount = metaAccount(chain, publicKey = account.publicKey, accountId = ACCOUNT_ID)
        val client = FakeBitcoinIndexerClient(
            addressResponse = BitcoinEsploraAddress(
                address = MAINNET_ADDRESS,
                chainStats = BitcoinEsploraStats(1, 100, 1, 200, 2),
                mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
            )
        )

        val updates = BitcoinBalanceLoader(chain, client).loadBalance(setOf(metaAccount))

        assertTrue(updates.isEmpty())
        assertEquals(listOf(MAINNET_ADDRESS), client.receivedAddresses)
    }

    @Test
    fun `provider routes universal wallet bitcoin chains to bitcoin balance loader`() {
        val provider = BalanceLoaderProvider(
            chainRegistry = mock(ChainRegistry::class.java),
            remoteStorageSource = mock(RemoteStorageSource::class.java),
            ethereumRemoteSource = mock(EthereumRemoteSource::class.java),
            substrateSource = mock(SubstrateRemoteSource::class.java),
            operationDao = NoopOperationDao(),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            chainsRepository = mock(ChainsRepository::class.java),
            tonSyncDataRepository = mock(TonSyncDataRepository::class.java),
            bitcoinIndexerClient = FakeBitcoinIndexerClient(),
            solanaBalanceSync = SolanaBalanceSync(mock(SolanaIndexerClient::class.java)),
            irohaToriiClient = mock(IrohaToriiClient::class.java)
        )

        assertTrue(provider.invoke(bitcoinChain(isTestNet = false)) is BitcoinBalanceLoader)
    }

    private class FakeBitcoinIndexerClient(
        private val addressResponse: BitcoinEsploraAddress = BitcoinEsploraAddress(
            address = MAINNET_ADDRESS,
            chainStats = BitcoinEsploraStats(0, 0, 0, 0, 0),
            mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
        )
    ) : BitcoinIndexerClient {
        val receivedAddresses = mutableListOf<String>()
        val receivedNetworks = mutableListOf<BitcoinIndexerRoutes.Network>()
        val receivedBaseUrls = mutableListOf<String?>()

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            receivedAddresses += address
            receivedNetworks += network
            receivedBaseUrls += baseUrl

            return addressResponse
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> = error("Unexpected UTXO call")

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> = error("Unexpected transactions call")

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> = error("Unexpected fee estimates call")

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String = error("Unexpected broadcast call")
    }

    private class NoopOperationDao : OperationDao() {
        override suspend fun insert(operation: OperationLocal) = Unit

        override suspend fun insertAll(operations: List<OperationLocal>) = Unit

        override fun observe(
            address: String,
            chainId: String,
            chainAssetId: String,
            statusUp: OperationLocal.Status
        ): Flow<List<OperationLocal>> = flowOf(emptyList())

        override suspend fun getOperation(hash: String): OperationLocal? = null

        override suspend fun getOperations(): List<OperationLocal> = emptyList()

        override fun observeOperations(): Flow<List<OperationLocal>> = flowOf(emptyList())

        override fun observeOperations(chainId: String): Flow<List<OperationLocal>> = flowOf(emptyList())

        override fun observeOperationAddresses(chainId: String, address: String, limit: Int): Flow<List<String>> = flowOf(emptyList())

        override fun getOperationAddresses(chainId: String, address: String, limit: Int): List<String> = emptyList()

        override suspend fun clearBySource(
            address: String,
            chainId: String,
            chainAssetId: String,
            source: OperationLocal.Source
        ): Int = 0

        override suspend fun clearOld(
            address: String,
            chainId: String,
            chainAssetId: String,
            minTime: Long
        ): Int = 0

        override suspend fun clearByHashes(
            address: String,
            chainId: String,
            chainAssetId: String,
            hashes: Set<String>
        ): Int = 0
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        val ACCOUNT_ID = ByteArray(32) { 9 }

        fun metaAccount(
            chain: Chain,
            publicKey: ByteArray,
            accountId: ByteArray
        ): MetaAccount {
            return MetaAccount(
                id = 1,
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1,
                        chain = chain,
                        publicKey = publicKey,
                        accountId = accountId,
                        cryptoType = CryptoType.ECDSA,
                        accountName = "Bitcoin"
                    )
                ),
                favoriteChains = emptyMap(),
                substratePublicKey = null,
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = null,
                ethereumAddress = null,
                ethereumPublicKey = null,
                tonPublicKey = null,
                isSelected = true,
                isBackedUp = true,
                googleBackupAddress = null,
                name = "Wallet",
                initialized = true
            )
        }

        fun bitcoinChain(isTestNet: Boolean): Chain {
            val network = if (isTestNet) UniversalWalletRegistry.bitcoinTestnet else UniversalWalletRegistry.bitcoinMainnet

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.name,
                minSupportedVersion = null,
                assets = listOf(bitcoinAsset(network.id, isTestNet)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(
                        Chain.ExternalApi.Section.Type.BITCOIN,
                        network.indexerBaseUrl
                    ),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = isTestNet,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun bitcoinAsset(chainId: String, isTestNet: Boolean): Asset {
            return Asset(
                id = "BTC",
                name = "Bitcoin",
                symbol = "BTC",
                iconUrl = "",
                chainId = chainId,
                chainName = "Bitcoin",
                chainIcon = null,
                isTestNet = isTestNet,
                priceId = null,
                precision = 8,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }
    }
}
