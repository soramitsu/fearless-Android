package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenBalanceChange
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistorySync
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionRecord
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import jp.co.soramitsu.common.model.UniversalWalletRegistry
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

class SolanaHistorySourceTest {

    @Test
    fun `maps solana history entries to native transfer operations and preserves cursor`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = transactionsResponse(
                transactions = listOf(transaction(signature = SIGNATURE_1), transaction(signature = SIGNATURE_2, nativeDelta = "0")),
                nextBefore = SIGNATURE_2
            )
        )
        val source = SolanaHistorySource(SolanaTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = SIGNATURE_0,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(),
            accountAddress = WALLET
        )

        assertEquals(listOf(INDEXER_URL), client.verifiedBaseUrls)
        assertEquals(WALLET, client.lastWallet)
        assertEquals(INDEXER_URL, client.lastBaseUrl)
        assertEquals(SIGNATURE_0, client.lastBefore)
        assertEquals(25, client.lastLimit)
        assertEquals(SIGNATURE_2, page.nextCursor)
        assertEquals(1, page.items.size)

        val operation = page.items.single()
        assertEquals(SIGNATURE_1, operation.id)
        assertEquals(WALLET, operation.address)
        assertEquals(1_710_000_000_000L, operation.time)
        val transfer = operation.type as Operation.Type.Transfer
        assertEquals(SIGNATURE_1, transfer.hash)
        assertEquals(WALLET, transfer.myAddress)
        assertEquals(BigInteger("1005000"), transfer.amount)
        assertEquals(WALLET, transfer.sender)
        assertEquals("", transfer.receiver)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(BigInteger("5000"), transfer.fee)
    }

    @Test
    fun `maps solana token history entries to token transfer operations`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = transactionsResponse(
                transactions = listOf(
                    transaction(
                        signature = SIGNATURE_1,
                        nativeDelta = null,
                        tokenChanges = listOf(tokenChange(mint = TOKEN_MINT, amountDelta = "-1500")),
                        feeLamports = "5000"
                    ),
                    transaction(
                        signature = SIGNATURE_2,
                        nativeDelta = null,
                        tokenChanges = listOf(tokenChange(mint = OTHER_TOKEN_MINT, amountDelta = "999"))
                    )
                )
            )
        )
        val source = SolanaHistorySource(SolanaTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(id = TOKEN_MINT, symbol = "USDC", precision = 6, isNative = false),
            accountAddress = WALLET
        )

        assertEquals(listOf(INDEXER_URL), client.verifiedBaseUrls)
        assertEquals(1, client.transactionCalls)
        assertEquals(1, page.items.size)

        val transfer = page.items.single().type as Operation.Type.Transfer
        assertEquals(SIGNATURE_1, transfer.hash)
        assertEquals(BigInteger("1500"), transfer.amount)
        assertEquals(WALLET, transfer.sender)
        assertEquals("", transfer.receiver)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(null, transfer.fee)
    }

    @Test
    fun `returns empty page without network calls for unsupported filters assets and limits`() = runBlocking {
        val client = FakeSolanaIndexerClient(response = transactionsResponse(listOf(transaction())))
        val source = SolanaHistorySource(SolanaTransactionHistorySync(client), INDEXER_URL)

        val noTransfer = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = emptySet(),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(),
            accountAddress = WALLET
        )
        val wrongAsset = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(symbol = "USDC", isNative = false),
            accountAddress = WALLET
        )
        val emptyLimit = source.getOperations(
            pageSize = 0,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(),
            accountAddress = WALLET
        )

        assertTrue(noTransfer.items.isEmpty())
        assertTrue(wrongAsset.items.isEmpty())
        assertTrue(emptyLimit.items.isEmpty())
        assertEquals(0, client.transactionCalls)
    }

    @Test
    fun `fails closed for malformed solana address before fetching`() = runBlocking {
        val client = FakeSolanaIndexerClient(response = transactionsResponse(listOf(transaction())))
        val source = SolanaHistorySource(SolanaTransactionHistorySync(client), INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = solanaChain(),
            chainAsset = solanaAsset(),
            accountAddress = "../bad"
        )

        assertTrue(page.items.isEmpty())
        assertEquals(0, client.transactionCalls)
    }

    @Test
    fun `provider routes solana history type to solana source`() {
        val provider = HistorySourceProvider(
            walletOperationsApi = mock(OperationsHistoryApi::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            historyInfoRemoteLoader = mock(HistoryInfoRemoteLoader::class.java),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            bitcoinTransactionHistorySync = BitcoinTransactionHistorySync(mock(BitcoinIndexerClient::class.java)),
            solanaTransactionHistorySync = SolanaTransactionHistorySync(FakeSolanaIndexerClient()),
            irohaToriiClient = mock(IrohaToriiClient::class.java)
        )

        assertTrue(provider(INDEXER_URL, Chain.ExternalApi.Section.Type.SOLANA) is SolanaHistorySource)
    }

    private class FakeSolanaIndexerClient(
        private val response: SolanaWalletTransactionsResponse = transactionsResponse(emptyList())
    ) : SolanaIndexerClient {
        val verifiedBaseUrls = mutableListOf<String>()
        var transactionCalls = 0
            private set
        var lastWallet: String? = null
            private set
        var lastBaseUrl: String? = null
            private set
        var lastBefore: String? = null
            private set
        var lastLimit: Int? = null
            private set

        override suspend fun serviceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            error("Unexpected Solana service-info call")
        }

        override suspend fun verifyServiceInfo(baseUrl: String?): SolanaIndexerServiceInfo {
            verifiedBaseUrls += baseUrl.orEmpty()
            return SolanaIndexerServiceInfo(
                schemaVersion = 1,
                serviceId = "si.soramitsu.io",
                serviceName = "Solswap Indexer",
                ecosystem = "solana",
                chainId = "solana:mainnet",
                network = "mainnet",
                publicBaseUrl = "https://si.soramitsu.io",
                readOnly = true
            )
        }

        override suspend fun balances(wallet: String, baseUrl: String?): SolanaWalletBalancesResponse {
            return SolanaWalletBalancesResponse(
                wallet = wallet,
                native = SolanaNativeBalance(lamports = "0", uiAmountString = "0"),
                total = 1,
                syncedAt = 1L
            )
        }

        override suspend fun assets(wallet: String, baseUrl: String?): SolanaWalletAssetsResponse {
            error("Unexpected Solana assets call")
        }

        override suspend fun state(wallet: String, baseUrl: String?): SolanaWalletStateResponse {
            error("Unexpected Solana state call")
        }

        override suspend fun transactions(
            wallet: String,
            baseUrl: String?,
            before: String?,
            limit: Int
        ): SolanaWalletTransactionsResponse {
            transactionCalls += 1
            lastWallet = wallet
            lastBaseUrl = baseUrl
            lastBefore = before
            lastLimit = limit
            return response
        }

        override suspend fun tokenMetadata(mint: String, baseUrl: String?): SolanaTokenMetadata {
            error("Unexpected Solana metadata call")
        }

        override suspend fun tokenMetadataBatch(
            mints: List<String>,
            baseUrl: String?
        ): SolanaTokenMetadataBatchResponse {
            error("Unexpected Solana metadata batch call")
        }
    }

    private companion object {
        const val INDEXER_URL = "https://si.soramitsu.io"
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        val SIGNATURE_0 = "1".repeat(88)
        val SIGNATURE_1 = "2".repeat(88)
        val SIGNATURE_2 = "3".repeat(88)
        const val TOKEN_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        const val OTHER_TOKEN_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkYur5nC8LqgxmVZ1"

        fun transactionsResponse(
            transactions: List<SolanaWalletTransactionRecord>,
            nextBefore: String? = null
        ): SolanaWalletTransactionsResponse {
            return SolanaWalletTransactionsResponse(
                wallet = WALLET,
                before = null,
                nextBefore = nextBefore,
                limit = 25,
                total = transactions.size,
                syncedAt = 1_710_000_000_000L,
                transactions = transactions
            )
        }

        fun transaction(
            signature: String = SIGNATURE_1,
            nativeDelta: String? = "-1005000",
            feeLamports: String? = "5000",
            tokenChanges: List<SolanaTokenBalanceChange> = emptyList(),
            solswapRoute: String? = null
        ): SolanaWalletTransactionRecord {
            return SolanaWalletTransactionRecord(
                signature = signature,
                slot = 100,
                timestamp = 1_710_000_000L,
                status = "success",
                feeLamports = feeLamports,
                nativeBalanceChangeLamports = nativeDelta,
                tokenBalanceChanges = tokenChanges,
                solswapRoute = solswapRoute
            )
        }

        fun tokenChange(
            mint: String = TOKEN_MINT,
            amountDelta: String = "-1500",
            decimals: Int = 6
        ): SolanaTokenBalanceChange {
            return SolanaTokenBalanceChange(
                mint = mint,
                preAmount = "2000",
                postAmount = "500",
                amountDelta = amountDelta,
                decimals = decimals,
                uiAmountDeltaString = "-0.0015"
            )
        }

        fun solanaChain(): Chain {
            val network = UniversalWalletRegistry.solanaMainnet

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.name,
                minSupportedVersion = null,
                assets = listOf(solanaAsset()),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.SOLANA, INDEXER_URL),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = false,
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

        fun solanaAsset(
            id: String = "SOL",
            symbol: String = "SOL",
            precision: Int = 9,
            isNative: Boolean = true
        ): Asset {
            return Asset(
                id = id,
                name = "Solana",
                symbol = symbol,
                iconUrl = "",
                chainId = UniversalWalletRegistry.solanaMainnet.id,
                chainName = "Solana",
                chainIcon = null,
                isTestNet = false,
                priceId = null,
                precision = precision,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = isNative,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = isNative
            )
        }
    }
}
