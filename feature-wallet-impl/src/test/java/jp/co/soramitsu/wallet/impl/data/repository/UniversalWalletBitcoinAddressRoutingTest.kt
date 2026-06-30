package jp.co.soramitsu.wallet.impl.data.repository

import com.google.gson.JsonPrimitive
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.chainAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransactionInput
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransactionOutput
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistorySync
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.BitcoinKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.historySource.HistorySourceProvider
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.data.storage.TransferCursorStorage
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.math.BigInteger

class UniversalWalletBitcoinAddressRoutingTest {

    @Test
    fun `meta account derives bitcoin mainnet address from chain account public key`() {
        val chain = bitcoinChain(isTestNet = false)
        val bitcoin = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Mainnet)
        val metaAccount = metaAccount(chain, publicKey = bitcoin.publicKey, accountId = ByteArray(32) { 7 })

        assertEquals(MAINNET_ADDRESS, metaAccount.address(chain))
        assertEquals(MAINNET_ADDRESS, metaAccount.chainAddress(chain))
    }

    @Test
    fun `meta account derives bitcoin testnet address from chain account public key`() {
        val chain = bitcoinChain(isTestNet = true)
        val bitcoin = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Testnet)
        val metaAccount = metaAccount(chain, publicKey = bitcoin.publicKey, accountId = ByteArray(32) { 7 })

        assertEquals(TESTNET_ADDRESS, metaAccount.address(chain))
        assertEquals(TESTNET_ADDRESS, metaAccount.chainAddress(chain))
    }

    @Test
    fun `bitcoin address resolution does not fall back to substrate account id`() {
        val chain = bitcoinChain(isTestNet = false)
        val metaAccount = metaAccount(chainAccounts = emptyMap(), substrateAccountId = ByteArray(32))

        assertNull(metaAccount.address(chain))
        assertNull(metaAccount.chainAddress(chain))
    }

    @Test
    fun `bitcoin address resolution fails closed for malformed public key`() {
        val chain = bitcoinChain(isTestNet = false)
        val metaAccount = metaAccount(chain, publicKey = ByteArray(32), accountId = ByteArray(32))

        assertNull(metaAccount.address(chain))
        assertNull(metaAccount.chainAddress(chain))
    }

    @Test
    fun `history repository uses supplied bitcoin address instead of deriving substrate address`() = runBlocking {
        val client = FakeBitcoinIndexerClient(transactions = listOf(incomingTransaction(MAINNET_ADDRESS)))
        val repository = historyRepository(client)

        val page = repository.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = ByteArray(32) { 7 },
            chain = bitcoinChain(isTestNet = false),
            chainAsset = bitcoinAsset(isTestNet = false),
            accountAddress = MAINNET_ADDRESS
        )

        assertEquals(listOf(MAINNET_ADDRESS), client.receivedAddresses)
        assertEquals(1, page.items.size)
        assertEquals(MAINNET_ADDRESS, page.items.single().address)
    }

    @Test
    fun `history repository rejects wrong-network bitcoin address before network calls`() = runBlocking {
        val client = FakeBitcoinIndexerClient(transactions = listOf(incomingTransaction(MAINNET_ADDRESS)))
        val repository = historyRepository(client)

        val page = repository.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = ByteArray(32) { 7 },
            chain = bitcoinChain(isTestNet = false),
            chainAsset = bitcoinAsset(isTestNet = false),
            accountAddress = TESTNET_ADDRESS
        )

        assertTrue(page.items.isEmpty())
        assertTrue(client.receivedAddresses.isEmpty())
    }

    @Test
    fun `history repository can derive bitcoin address from public key fallback`() = runBlocking {
        val bitcoin = BitcoinKeyDerivation.deriveAccount(MNEMONIC, network = BitcoinKeyDerivation.Network.Mainnet)
        val client = FakeBitcoinIndexerClient(transactions = listOf(incomingTransaction(MAINNET_ADDRESS)))
        val repository = historyRepository(client)

        val page = repository.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = bitcoin.publicKey,
            chain = bitcoinChain(isTestNet = false),
            chainAsset = bitcoinAsset(isTestNet = false)
        )

        assertEquals(listOf(MAINNET_ADDRESS), client.receivedAddresses)
        assertEquals(1, page.items.size)
    }

    private fun historyRepository(client: FakeBitcoinIndexerClient): HistoryRepository {
        val provider = HistorySourceProvider(
            walletOperationsApi = mock(OperationsHistoryApi::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            historyInfoRemoteLoader = mock(HistoryInfoRemoteLoader::class.java),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            bitcoinTransactionHistorySync = BitcoinTransactionHistorySync(client),
            solanaTransactionHistorySync = SolanaTransactionHistorySync(mock(SolanaIndexerClient::class.java)),
            irohaToriiClient = mock(IrohaToriiClient::class.java)
        )

        return HistoryRepository(
            historySourceProvider = provider,
            operationDao = NoopOperationDao(),
            cursorStorage = mock(TransferCursorStorage::class.java),
            currentAccountAddress = mock(CurrentAccountAddressUseCase::class.java)
        )
    }

    private class FakeBitcoinIndexerClient(
        private val transactions: List<BitcoinEsploraTransaction>
    ) : BitcoinIndexerClient {
        val receivedAddresses = mutableListOf<String>()

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            return BitcoinEsploraAddress(
                address = address,
                chainStats = BitcoinEsploraStats(0, 0, 0, 0, 0),
                mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
            )
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> = emptyList()

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> {
            receivedAddresses += address

            return transactions
        }

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> = emptyMap()

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String = TXID
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
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        val TXID = "11".repeat(32)

        fun metaAccount(
            chain: Chain,
            publicKey: ByteArray,
            accountId: ByteArray
        ): MetaAccount {
            return metaAccount(
                chainAccounts = mapOf(
                    chain.id to MetaAccount.ChainAccount(
                        metaId = 1,
                        chain = chain,
                        publicKey = publicKey,
                        accountId = accountId,
                        cryptoType = CryptoType.ECDSA,
                        accountName = "Bitcoin"
                    )
                )
            )
        }

        fun metaAccount(
            chainAccounts: Map<String, MetaAccount.ChainAccount>,
            substrateAccountId: ByteArray? = null
        ): MetaAccount {
            return MetaAccount(
                id = 1,
                chainAccounts = chainAccounts,
                favoriteChains = emptyMap(),
                substratePublicKey = substrateAccountId,
                substrateCryptoType = CryptoType.SR25519,
                substrateAccountId = substrateAccountId,
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
                assets = listOf(bitcoinAsset(isTestNet)),
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

        fun bitcoinAsset(isTestNet: Boolean): Asset {
            return Asset(
                id = "BTC",
                name = "Bitcoin",
                symbol = "BTC",
                iconUrl = "",
                chainId = if (isTestNet) UniversalWalletRegistry.bitcoinTestnet.id else UniversalWalletRegistry.bitcoinMainnet.id,
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

        fun incomingTransaction(address: String): BitcoinEsploraTransaction {
            return BitcoinEsploraTransaction(
                txid = TXID,
                status = BitcoinEsploraTxStatus(confirmed = true, blockTime = 1_710_000_000),
                vin = listOf(input("bc1q6rz28mcfaxtmd6v789l9rrlrusdprr9pkv76kj", 50_000)),
                vout = listOf(output(address, 50_000))
            )
        }

        fun input(address: String, valueSats: Long): BitcoinEsploraTransactionInput {
            return BitcoinEsploraTransactionInput(output(address, valueSats))
        }

        fun output(address: String, valueSats: Long): BitcoinEsploraTransactionOutput {
            return BitcoinEsploraTransactionOutput(address, JsonPrimitive(valueSats))
        }
    }
}
