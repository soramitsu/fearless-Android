package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.JsonPrimitive
import java.math.BigInteger
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
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class BitcoinHistorySourceTest {

    @Test
    fun `maps bitcoin history entries to transfer operations and preserves page cursor`() = runBlocking {
        val transactions = listOf(
            outgoingTransaction(txid = TXID_1, amountSats = 60_000, feeSats = 141, confirmed = true),
            incomingTransaction(txid = TXID_2, amountSats = 25_000, confirmed = false)
        )
        val client = FakeBitcoinIndexerClient(transactions)
        val source = BitcoinHistorySource(BitcoinTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 1,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = bitcoinChain(),
            chainAsset = bitcoinAsset(),
            accountAddress = MAINNET_ADDRESS
        )

        assertEquals(INDEXER_URL, client.lastBaseUrl)
        assertEquals(listOf(null), client.lastSeenTxids)
        assertEquals(TXID_1, page.nextCursor)
        assertEquals(1, page.items.size)

        val operation = page.items.single()
        assertEquals(TXID_1, operation.id)
        assertEquals(MAINNET_ADDRESS, operation.address)
        assertEquals(1_710_000_000_000L, operation.time)
        val transfer = operation.type as Operation.Type.Transfer
        assertEquals(TXID_1, transfer.hash)
        assertEquals(MAINNET_ADDRESS, transfer.myAddress)
        assertEquals(BigInteger.valueOf(60_000), transfer.amount)
        assertEquals(MAINNET_ADDRESS, transfer.sender)
        assertEquals(COUNTERPARTY, transfer.receiver)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(BigInteger.valueOf(141), transfer.fee)
    }

    @Test
    fun `uses the supplied cursor for follow up bitcoin pages`() = runBlocking {
        val client = FakeBitcoinIndexerClient(listOf(incomingTransaction(txid = TXID_2, amountSats = 25_000, confirmed = false)))
        val source = BitcoinHistorySource(BitcoinTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = TXID_1,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = bitcoinChain(),
            chainAsset = bitcoinAsset(),
            accountAddress = MAINNET_ADDRESS
        )

        assertEquals(listOf(TXID_1), client.lastSeenTxids)
        assertEquals(TXID_2, page.nextCursor)
        assertEquals(1, page.items.size)
        val transfer = page.items.single().type as Operation.Type.Transfer
        assertEquals(COUNTERPARTY, transfer.sender)
        assertEquals(MAINNET_ADDRESS, transfer.receiver)
        assertEquals(Operation.Status.PENDING, transfer.status)
        assertEquals(BigInteger.ZERO, transfer.fee)
    }

    @Test
    fun `returns empty page without network calls for unsupported filters assets and limits`() = runBlocking {
        val client = FakeBitcoinIndexerClient(listOf(outgoingTransaction()))
        val source = BitcoinHistorySource(BitcoinTransactionHistorySync(client), INDEXER_URL)

        val noTransfer = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = emptySet(),
            accountId = byteArrayOf(),
            chain = bitcoinChain(),
            chainAsset = bitcoinAsset(),
            accountAddress = MAINNET_ADDRESS
        )
        val wrongAsset = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = bitcoinChain(),
            chainAsset = bitcoinAsset(symbol = "DOT"),
            accountAddress = MAINNET_ADDRESS
        )
        val emptyLimit = source.getOperations(
            pageSize = 0,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = bitcoinChain(),
            chainAsset = bitcoinAsset(),
            accountAddress = MAINNET_ADDRESS
        )

        assertTrue(noTransfer.items.isEmpty())
        assertTrue(wrongAsset.items.isEmpty())
        assertTrue(emptyLimit.items.isEmpty())
        assertEquals(0, client.calls)
    }

    @Test
    fun `fails closed for wrong bitcoin network address before fetching`() = runBlocking {
        val client = FakeBitcoinIndexerClient(listOf(outgoingTransaction()))
        val source = BitcoinHistorySource(BitcoinTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = bitcoinChain(isTestNet = false),
            chainAsset = bitcoinAsset(),
            accountAddress = TESTNET_ADDRESS
        )

        assertTrue(page.items.isEmpty())
        assertEquals(0, client.calls)
    }

    @Test
    fun `provider routes bitcoin history type to bitcoin source`() {
        val provider = HistorySourceProvider(
            walletOperationsApi = mock(OperationsHistoryApi::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            historyInfoRemoteLoader = mock(HistoryInfoRemoteLoader::class.java),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            bitcoinTransactionHistorySync = BitcoinTransactionHistorySync(FakeBitcoinIndexerClient()),
            solanaTransactionHistorySync = SolanaTransactionHistorySync(mock(SolanaIndexerClient::class.java)),
            irohaToriiClient = mock(IrohaToriiClient::class.java)
        )

        assertTrue(provider(INDEXER_URL, Chain.ExternalApi.Section.Type.BITCOIN) is BitcoinHistorySource)
    }

    private class FakeBitcoinIndexerClient(
        private val transactions: List<BitcoinEsploraTransaction> = emptyList()
    ) : BitcoinIndexerClient {
        var calls = 0
            private set
        var lastBaseUrl: String? = null
            private set
        val lastSeenTxids = mutableListOf<String?>()

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
            calls += 1
            lastBaseUrl = baseUrl
            lastSeenTxids.add(lastSeenTxid)

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
        ): String = TXID_1
    }

    private companion object {
        const val INDEXER_URL = "https://bitcoin.example/api"
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        const val COUNTERPARTY = "bc1q6rz28mcfaxtmd6v789l9rrlrusdprr9pkv76kj"
        val TXID_1 = "11".repeat(32)
        val TXID_2 = "22".repeat(32)
        val BLOCK_HASH = "aa".repeat(32)

        fun bitcoinChain(isTestNet: Boolean = false): Chain {
            return Chain(
                id = if (isTestNet) "bitcoin-testnet" else "bitcoin-mainnet",
                paraId = null,
                rank = null,
                name = "Bitcoin",
                minSupportedVersion = null,
                assets = listOf(bitcoinAsset(isTestNet = isTestNet)),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.BITCOIN, INDEXER_URL),
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

        fun bitcoinAsset(symbol: String = "BTC", isTestNet: Boolean = false): Asset {
            return Asset(
                id = "BTC",
                name = "Bitcoin",
                symbol = symbol,
                iconUrl = "",
                chainId = if (isTestNet) "bitcoin-testnet" else "bitcoin-mainnet",
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

        fun outgoingTransaction(
            txid: String = TXID_1,
            amountSats: Long = 60_000,
            feeSats: Long = 141,
            confirmed: Boolean = true
        ): BitcoinEsploraTransaction {
            return BitcoinEsploraTransaction(
                txid = txid,
                fee = feeSats,
                status = BitcoinEsploraTxStatus(
                    confirmed = confirmed,
                    blockHash = if (confirmed) BLOCK_HASH else null,
                    blockHeight = if (confirmed) 100 else null,
                    blockTime = if (confirmed) 1_710_000_000 else null
                ),
                vin = listOf(input(MAINNET_ADDRESS, amountSats + feeSats + 10_000)),
                vout = listOf(output(COUNTERPARTY, amountSats), output(MAINNET_ADDRESS, 10_000))
            )
        }

        fun incomingTransaction(
            txid: String,
            amountSats: Long,
            confirmed: Boolean
        ): BitcoinEsploraTransaction {
            return BitcoinEsploraTransaction(
                txid = txid,
                status = BitcoinEsploraTxStatus(confirmed = confirmed),
                vin = listOf(input(COUNTERPARTY, amountSats)),
                vout = listOf(output(MAINNET_ADDRESS, amountSats))
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
