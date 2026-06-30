package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSync
import jp.co.soramitsu.common.data.network.solana.SolanaBalanceSyncException
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaBalanceSyncTest {

    @Test
    fun `normalizes native SOL and token balances from SI into shared asset balances`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                lamports = "2500000000",
                uiAmountString = "2.5",
                tokens = listOf(
                    tokenBalance(SPL_MINT, TOKEN_ACCOUNT, "12500000", "12.5", 6, "spl-token"),
                    tokenBalance(TOKEN_2022_MINT, TOKEN_2022_ACCOUNT, "3000000001", "3.000000001", 9, "token-2022")
                )
            ),
            metadata = listOf(
                tokenMetadata(SPL_MINT, "USDC", "USD Coin", "spl-token"),
                tokenMetadata(TOKEN_2022_MINT, "T22", "Token 2022 Asset", "token-2022")
            )
        )
        val result = SolanaBalanceSync(client).balances(WALLET)

        assertEquals("https://si.soramitsu.io", client.verifiedBaseUrls.single())
        assertEquals("solana-mainnet", result.networkId)
        assertEquals("solana:mainnet", result.chainId)
        assertEquals(3, result.balances.size)
        assertEquals("solana-mainnet", result.nativeBalance.accountId)
        assertEquals("SOL", result.nativeBalance.assetId)
        assertEquals("2500000000", result.nativeBalance.amount)
        assertEquals("2.5", result.nativeBalance.uiAmountString)
        assertEquals(true, result.nativeBalance.isNative)

        val spl = result.tokenBalances[0]
        assertEquals("solana-mainnet", spl.accountId)
        assertEquals(SPL_MINT, spl.assetId)
        assertEquals("12500000", spl.amount)
        assertEquals("12.5", spl.uiAmountString)
        assertEquals(6, spl.decimals)
        assertEquals("USDC", spl.symbol)
        assertEquals("USD Coin", spl.name)
        assertEquals(TOKEN_ACCOUNT, spl.tokenAccountId)
        assertEquals(SPL_MINT, spl.contractAddress)
        assertEquals("spl-token", spl.tokenProgram)

        val token2022 = result.tokenBalances[1]
        assertEquals(TOKEN_2022_MINT, token2022.assetId)
        assertEquals("T22", token2022.symbol)
        assertEquals("Token 2022 Asset", token2022.name)
        assertEquals("token-2022", token2022.tokenProgram)
        assertEquals(listOf(listOf(SPL_MINT, TOKEN_2022_MINT)), client.metadataBatchCalls)
    }

    @Test
    fun `keeps token balances when metadata lookup fails`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                lamports = "1",
                uiAmountString = "0.000000001",
                tokens = listOf(tokenBalance(SPL_MINT, TOKEN_ACCOUNT, "7", "7", 6, "spl-token"))
            ),
            metadataError = IllegalStateException("metadata unavailable")
        )

        val result = SolanaBalanceSync(client).balances(WALLET)

        assertEquals(1, result.tokenBalances.size)
        assertEquals("So11...1112", result.tokenBalances.single().symbol)
        assertEquals(SPL_MINT, result.tokenBalances.single().name)
    }

    @Test
    fun `skips malformed token balances without hiding native SOL`() = runBlocking {
        val client = FakeSolanaIndexerClient(
            response = balancesResponse(
                lamports = "1",
                uiAmountString = "0.000000001",
                tokens = listOf(
                    tokenBalance(SPL_MINT, TOKEN_ACCOUNT, "7", "7", 6, "spl-token").copy(owner = "11111111111111111111111111111111"),
                    tokenBalance(TOKEN_2022_MINT, TOKEN_2022_ACCOUNT, "3", "3", 9, "token-2022")
                )
            )
        )

        val result = SolanaBalanceSync(client).balances(WALLET)

        assertEquals("1", result.nativeBalance.amount)
        assertEquals(listOf(TOKEN_2022_MINT), result.tokenBalances.map { it.assetId })
    }

    @Test
    fun `rejects mismatched wallet and malformed native balances`() {
        val walletMismatch = assertThrows(SolanaBalanceSyncException::class.java) {
            runBlocking {
                SolanaBalanceSync(
                    FakeSolanaIndexerClient(
                        response = balancesResponse(wallet = "11111111111111111111111111111111")
                    )
                ).balances(WALLET)
            }
        }
        assertEquals(SolanaBalanceSyncException.Code.WALLET_MISMATCH, walletMismatch.code)

        val malformedNative = assertThrows(SolanaBalanceSyncException::class.java) {
            runBlocking {
                SolanaBalanceSync(
                    FakeSolanaIndexerClient(
                        response = balancesResponse(lamports = "-1")
                    )
                ).balances(WALLET)
            }
        }
        assertEquals(SolanaBalanceSyncException.Code.INVALID_NATIVE_BALANCE, malformedNative.code)
    }

    @Test
    fun `rejects malformed wallet before network calls`() {
        val client = FakeSolanaIndexerClient()

        assertThrows(SolanaIndexerRoutes.SolanaIndexerRouteException::class.java) {
            runBlocking {
                SolanaBalanceSync(client).balances("../bad")
            }
        }

        assertEquals(emptyList<String>(), client.verifiedBaseUrls)
    }

    private class FakeSolanaIndexerClient(
        private val response: SolanaWalletBalancesResponse = balancesResponse(),
        private val metadata: List<SolanaTokenMetadata> = emptyList(),
        private val metadataError: Exception? = null
    ) : SolanaIndexerClient {
        val verifiedBaseUrls = mutableListOf<String>()
        val metadataBatchCalls = mutableListOf<List<String>>()

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
            assertEquals(WALLET, wallet)
            assertEquals(UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL, baseUrl)
            return response
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
            error("Unexpected Solana transactions call")
        }

        override suspend fun tokenMetadata(mint: String, baseUrl: String?): SolanaTokenMetadata {
            error("Unexpected Solana single metadata call")
        }

        override suspend fun tokenMetadataBatch(
            mints: List<String>,
            baseUrl: String?
        ): SolanaTokenMetadataBatchResponse {
            metadataError?.let { throw it }
            metadataBatchCalls += mints
            return SolanaTokenMetadataBatchResponse(
                total = metadata.size,
                syncedAt = 1L,
                tokens = metadata.filter { it.mint in mints }
            )
        }
    }

    private companion object {
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val SPL_MINT = "So11111111111111111111111111111111111111112"
        const val TOKEN_2022_MINT = "5Pobwp6d9ihN9Nz38f87gVCEBFMgipFiSM2VtUhVit6w"
        const val TOKEN_ACCOUNT = "9xQeWvG816bUx9EPfQ4vF5xXw4wa9VFeTuzA7h4sFnH"
        const val TOKEN_2022_ACCOUNT = "7YttLkHDoYJk5HWB7wZWTsxjZCmMCbU6Uf17dYxcqYND"

        fun balancesResponse(
            wallet: String = WALLET,
            lamports: String = "0",
            uiAmountString: String = "0",
            tokens: List<SolanaTokenBalance> = emptyList()
        ): SolanaWalletBalancesResponse {
            return SolanaWalletBalancesResponse(
                wallet = wallet,
                native = SolanaNativeBalance(
                    lamports = lamports,
                    uiAmountString = uiAmountString
                ),
                tokens = tokens,
                total = tokens.size + 1,
                syncedAt = 1710000000000L
            )
        }

        fun tokenBalance(
            mint: String,
            accountAddress: String,
            amount: String,
            uiAmountString: String,
            decimals: Int,
            program: String
        ): SolanaTokenBalance {
            return SolanaTokenBalance(
                accountAddress = accountAddress,
                mint = mint,
                owner = WALLET,
                program = program,
                programId = if (program == "token-2022") {
                    "TokenzQdY11111111111111111111111111111111111"
                } else {
                    "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
                },
                amount = amount,
                decimals = decimals,
                uiAmountString = uiAmountString
            )
        }

        fun tokenMetadata(
            mint: String,
            symbol: String,
            name: String,
            program: String
        ): SolanaTokenMetadata {
            return SolanaTokenMetadata(
                mint = mint,
                exists = true,
                program = program,
                decimals = 6,
                name = symbol,
                symbol = symbol,
                syncedAt = 1L
            ).copy(name = name)
        }
    }
}
