package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.RetrofitSolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerApi
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerIdentityException
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaNativeBalance
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadata
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchRequest
import jp.co.soramitsu.common.data.network.solana.SolanaTokenMetadataBatchResponse
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferFeeConfig
import jp.co.soramitsu.common.data.network.solana.SolanaTokenTransferHook
import jp.co.soramitsu.common.data.network.solana.SolanaWalletAssetsResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletStateResponse
import jp.co.soramitsu.common.data.network.solana.SolanaWalletTransactionsResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaIndexerClientTest {

    @Test
    fun `delegates wallet endpoints through validated si routes`() = runBlocking {
        val api = FakeSolanaIndexerApi()
        val client = RetrofitSolanaIndexerClient(api)

        client.verifyServiceInfo()
        assertEquals("https://si.soramitsu.io/api/indexer/v1/service-info", api.lastUrl)

        client.balances(WALLET)
        assertEquals("https://si.soramitsu.io/api/indexer/v1/accounts/$WALLET/balances", api.lastUrl)

        client.transactions(WALLET, before = SIGNATURE, limit = 25)
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/accounts/$WALLET/txs?limit=25&before=$SIGNATURE",
            api.lastUrl
        )

        val metadata = client.tokenMetadata(MINT)
        assertEquals("https://si.soramitsu.io/api/indexer/v1/tokens/$MINT/metadata", api.lastUrl)
        assertEquals(listOf("transferFeeConfig", "transferHook"), metadata.extensions)
        assertEquals("0", metadata.transferFeeConfig?.withheldAmount)
        assertEquals(WALLET, metadata.transferHook?.programId)
        assertEquals(WALLET, metadata.transferHook?.extraAccountMetasAddress)
    }

    @Test
    fun `delegates metadata batch with normalized request body`() {
        runBlocking {
            val api = FakeSolanaIndexerApi()
            val client = RetrofitSolanaIndexerClient(api)

            client.tokenMetadataBatch(listOf(MINT))
            assertEquals("https://si.soramitsu.io/api/indexer/v1/tokens/metadata", api.lastUrl)
            assertEquals(listOf(MINT), api.lastMetadataBatchRequest?.mints)

            assertThrows(SolanaIndexerRoutes.SolanaIndexerRouteException::class.java) {
                runBlocking {
                    client.tokenMetadataBatch(emptyList())
                }
            }
        }
    }

    @Test
    fun `verify service info rejects misrouted solana indexer`() = runBlocking {
        val api = FakeSolanaIndexerApi()
        val client = RetrofitSolanaIndexerClient(api)

        api.serviceInfoResponse = api.serviceInfoResponse.copy(serviceId = "ti.soramitsu.io")

        val error = assertThrows(SolanaIndexerIdentityException::class.java) {
            runBlocking {
                client.verifyServiceInfo()
            }
        }
        assertEquals("ti.soramitsu.io", error.serviceInfo.serviceId)
        assertEquals("https://si.soramitsu.io/api/indexer/v1/service-info", api.lastUrl)
    }

    private class FakeSolanaIndexerApi : SolanaIndexerApi {
        var lastUrl: String? = null
            private set
        var lastMetadataBatchRequest: SolanaTokenMetadataBatchRequest? = null
            private set
        var serviceInfoResponse = SolanaIndexerServiceInfo(
            schemaVersion = 1,
            serviceId = "si.soramitsu.io",
            serviceName = "Solswap Indexer",
            ecosystem = "solana",
            chainId = "solana:mainnet",
            network = "mainnet",
            publicBaseUrl = "https://si.soramitsu.io",
            readOnly = true
        )

        override suspend fun getServiceInfo(url: String): SolanaIndexerServiceInfo {
            lastUrl = url
            return serviceInfoResponse
        }

        override suspend fun getBalances(url: String): SolanaWalletBalancesResponse {
            lastUrl = url
            return SolanaWalletBalancesResponse(
                wallet = WALLET,
                native = SolanaNativeBalance(lamports = "0", uiAmountString = "0"),
                total = 1,
                syncedAt = 1L
            )
        }

        override suspend fun getAssets(url: String): SolanaWalletAssetsResponse {
            lastUrl = url
            return SolanaWalletAssetsResponse(
                wallet = WALLET,
                total = 0,
                syncedAt = 1L
            )
        }

        override suspend fun getState(url: String): SolanaWalletStateResponse {
            lastUrl = url
            return SolanaWalletStateResponse(
                wallet = WALLET,
                exists = true,
                lamports = "0",
                executable = false,
                dataLength = 0,
                syncedAt = 1L
            )
        }

        override suspend fun getTransactions(url: String): SolanaWalletTransactionsResponse {
            lastUrl = url
            return SolanaWalletTransactionsResponse(
                wallet = WALLET,
                limit = 25,
                total = 0,
                syncedAt = 1L
            )
        }

        override suspend fun getTokenMetadata(url: String): SolanaTokenMetadata {
            lastUrl = url
            return SolanaTokenMetadata(
                mint = MINT,
                exists = true,
                program = "spl-token",
                extensions = listOf("transferFeeConfig", "transferHook"),
                transferFeeConfig = SolanaTokenTransferFeeConfig(withheldAmount = "0"),
                transferHook = SolanaTokenTransferHook(programId = WALLET, extraAccountMetasAddress = WALLET),
                syncedAt = 1L
            )
        }

        override suspend fun getTokenMetadataBatch(
            url: String,
            body: SolanaTokenMetadataBatchRequest
        ): SolanaTokenMetadataBatchResponse {
            lastUrl = url
            lastMetadataBatchRequest = body
            return SolanaTokenMetadataBatchResponse(
                total = body.mints.size,
                syncedAt = 1L
            )
        }
    }

    private companion object {
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val MINT = "So11111111111111111111111111111111111111112"
        val SIGNATURE = "1".repeat(88)
    }
}
