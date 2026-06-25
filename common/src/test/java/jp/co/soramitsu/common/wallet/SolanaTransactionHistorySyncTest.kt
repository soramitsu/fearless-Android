package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistoryException
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistorySync
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionRecord
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import jp.co.soramitsu.common.model.UniversalWalletIndexedOperationType
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionDirection
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionStatus
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaTransactionHistorySyncTest {

    @Test
    fun `normalizes native SOL transaction history from SI`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = transactionsResponse(
                listOf(
                    transaction(signature = SIGNATURE_1),
                    transaction(signature = SIGNATURE_2, nativeDelta = "0")
                ),
                nextBefore = SIGNATURE_2
            )
        )

        val page = SolanaTransactionHistorySync(client).history(
            wallet = WALLET,
            baseUrl = "https://si.soramitsu.io/",
            before = SIGNATURE_0,
            limit = 25
        )

        assertEquals(listOf("https://si.soramitsu.io"), client.verifiedBaseUrls)
        assertEquals(WALLET, client.lastWallet)
        assertEquals("https://si.soramitsu.io", client.lastBaseUrl)
        assertEquals(SIGNATURE_0, client.lastBefore)
        assertEquals(25, client.lastLimit)
        assertEquals(SIGNATURE_2, page.pageInfo.nextCursor)
        assertEquals(1, page.transactions.size)

        val transaction = page.transactions.single()
        assertEquals("solana-mainnet", transaction.accountId)
        assertEquals("solana:mainnet", transaction.chainId)
        assertEquals(SIGNATURE_1, transaction.transactionId)
        assertEquals(UniversalWalletIndexedTransactionStatus.Confirmed, transaction.status)
        assertEquals(UniversalWalletIndexedTransactionDirection.Outgoing, transaction.direction)
        assertEquals(UniversalWalletIndexedOperationType.Transfer, transaction.operationType)
        assertEquals("1005000", transaction.amount)
        assertEquals("SOL", transaction.assetId)
        assertEquals("5000", transaction.feeAmount)
        assertEquals("SOL", transaction.feeAssetId)
        assertEquals("100", transaction.blockNumber)
        assertEquals(1_710_000_000_000L, transaction.timestampMillis)
    }

    @Test
    fun `aggregates matching token deltas and marks solswap routes as swaps`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = transactionsResponse(
                listOf(
                    transaction(
                        tokenChanges = listOf(
                            change(MINT, "500"),
                            change(MINT, "-200"),
                            change(OTHER_MINT, "999")
                        ),
                        solswapRoute = "batch"
                    ),
                    transaction(
                        signature = SIGNATURE_2,
                        tokenChanges = listOf(change(OTHER_MINT, "1"))
                    )
                )
            )
        )

        val page = SolanaTransactionHistorySync(client).history(
            wallet = WALLET,
            assetId = MINT,
            isNative = false
        )

        val transaction = page.transactions.single()
        assertEquals(MINT, transaction.assetId)
        assertEquals("300", transaction.amount)
        assertEquals(UniversalWalletIndexedTransactionDirection.Incoming, transaction.direction)
        assertEquals(UniversalWalletIndexedOperationType.Swap, transaction.operationType)
        assertEquals("5000", transaction.feeAmount)
    }

    @Test
    fun `filters malformed zero amount and unsupported status transactions`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = transactionsResponse(
                listOf(
                    transaction(signature = "not-a-signature"),
                    transaction(signature = SIGNATURE_1, nativeDelta = "0"),
                    transaction(signature = SIGNATURE_2, status = "pending"),
                    transaction(signature = SIGNATURE_3, feeLamports = "-1")
                ),
                nextBefore = "not-a-signature"
            )
        )

        val page = SolanaTransactionHistorySync(client).history(WALLET)

        assertEquals(0, page.transactions.size)
        assertEquals(null, page.pageInfo.nextCursor)
    }

    @Test
    fun `rejects invalid wallet or asset before fetching history`() {
        val client = FakeSolanaIndexerClient()

        val invalidWallet = assertThrows(SolanaTransactionHistoryException::class.java) {
            runBlocking {
                SolanaTransactionHistorySync(client).history("../bad")
            }
        }
        assertEquals(SolanaTransactionHistoryException.Code.INVALID_INPUT, invalidWallet.code)

        val invalidAsset = assertThrows(SolanaTransactionHistoryException::class.java) {
            runBlocking {
                SolanaTransactionHistorySync(client).history(
                    wallet = WALLET,
                    assetId = "../bad",
                    isNative = false
                )
            }
        }
        assertEquals(SolanaTransactionHistoryException.Code.INVALID_INPUT, invalidAsset.code)
        assertEquals(0, client.transactionCalls)
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
            error("Unexpected Solana single metadata call")
        }

        override suspend fun tokenMetadataBatch(
            mints: List<String>,
            baseUrl: String?
        ): SolanaTokenMetadataBatchResponse {
            error("Unexpected Solana metadata batch call")
        }
    }

    private companion object {
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val MINT = "So11111111111111111111111111111111111111112"
        const val OTHER_MINT = "5Pobwp6d9ihN9Nz38f87gVCEBFMgipFiSM2VtUhVit6w"
        val SIGNATURE_0 = "1".repeat(88)
        val SIGNATURE_1 = "2".repeat(88)
        val SIGNATURE_2 = "3".repeat(88)
        val SIGNATURE_3 = "4".repeat(88)

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
            status: String = "success",
            feeLamports: String? = "5000",
            nativeDelta: String? = "-1005000",
            tokenChanges: List<jp.co.soramitsu.common.data.network.solana.SolanaTokenBalanceChange> = emptyList(),
            solswapRoute: String? = null
        ): SolanaWalletTransactionRecord {
            return SolanaWalletTransactionRecord(
                signature = signature,
                slot = 100,
                timestamp = 1_710_000_000L,
                status = status,
                feeLamports = feeLamports,
                nativeBalanceChangeLamports = nativeDelta,
                tokenBalanceChanges = tokenChanges,
                solswapRoute = solswapRoute
            )
        }

        fun change(
            mint: String,
            amountDelta: String
        ): jp.co.soramitsu.common.data.network.solana.SolanaTokenBalanceChange {
            return jp.co.soramitsu.common.data.network.solana.SolanaTokenBalanceChange(
                mint = mint,
                preAmount = "0",
                postAmount = amountDelta,
                amountDelta = amountDelta,
                decimals = 6,
                uiAmountDeltaString = amountDelta
            )
        }
    }
}
