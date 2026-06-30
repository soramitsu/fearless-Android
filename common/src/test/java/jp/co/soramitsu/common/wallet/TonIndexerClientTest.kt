package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.ton.RetrofitTonIndexerClient
import jp.co.soramitsu.common.data.network.ton.TonAccountStateResponse
import jp.co.soramitsu.common.data.network.ton.TonBalanceResponse
import jp.co.soramitsu.common.data.network.ton.TonBalancesResponse
import jp.co.soramitsu.common.data.network.ton.TonContractsResponse
import jp.co.soramitsu.common.data.network.ton.TonHealthStatus
import jp.co.soramitsu.common.data.network.ton.TonIndexerApi
import jp.co.soramitsu.common.data.network.ton.TonIndexerIdentityException
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes
import jp.co.soramitsu.common.data.network.ton.TonIndexerServiceInfo
import jp.co.soramitsu.common.data.network.ton.TonJettonTransferPayloadResponse
import jp.co.soramitsu.common.data.network.ton.TonNativeBalance
import jp.co.soramitsu.common.data.network.ton.TonRunGetMethodRequest
import jp.co.soramitsu.common.data.network.ton.TonRunGetMethodResponse
import jp.co.soramitsu.common.data.network.ton.TonRunGetMethodsRequest
import jp.co.soramitsu.common.data.network.ton.TonRunGetMethodsResponse
import jp.co.soramitsu.common.data.network.ton.TonSwapsResponse
import jp.co.soramitsu.common.data.network.ton.TonTransactionsResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TonIndexerClientTest {

    @Test
    fun `delegates account endpoints through validated ton indexer routes`() = runBlocking {
        val api = FakeTonIndexerApi()
        val client = RetrofitTonIndexerClient(api)

        client.verifyServiceInfo()
        assertEquals("https://ti.soramitsu.io/api/indexer/v1/service-info", api.lastUrl)

        client.balance(ADDRESS)
        assertEquals("https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/balance", api.lastUrl)

        client.transactions(address = ADDRESS, page = 2, cursorLt = "10", cursorHash = HASH)
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/txs?page=2&cursor_lt=10&cursor_hash=$ENCODED_HASH",
            api.lastUrl
        )

        client.swaps(
            address = ADDRESS,
            limit = 25,
            fromUtime = 100,
            toUtime = 200,
            payToken = "TON",
            receiveToken = "TST",
            executionType = TonIndexerRoutes.TonSwapExecutionType.Market,
            status = TonIndexerRoutes.TonSwapStatus.Success,
            includeReverse = true
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/swaps?limit=25&from_utime=100&to_utime=200&pay_token=TON&receive_token=TST&execution_type=market&status=success&include_reverse=true",
            api.lastUrl
        )

        client.jettonTransferPayload(ADDRESS, RAW_ADDRESS.uppercase())
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/jettons/$ADDRESS/transfer/$RAW_ADDRESS/payload",
            api.lastUrl
        )
    }

    @Test
    fun `delegates getter calls with normalized request bodies`() {
        runBlocking {
            val api = FakeTonIndexerApi()
            val client = RetrofitTonIndexerClient(api)

            client.runGetMethod(ADDRESS, " seqno ")
            assertEquals("https://ti.soramitsu.io/api/indexer/v1/runGetMethod", api.lastUrl)
            assertEquals(ADDRESS, api.lastRunGetMethodRequest?.address)
            assertEquals("seqno", api.lastRunGetMethodRequest?.method)

            val call = TonIndexerRoutes.runGetMethodRequest(ADDRESS, "seqno")
            client.runGetMethods(listOf(call))
            assertEquals("https://ti.soramitsu.io/api/indexer/v1/runGetMethods", api.lastUrl)
            assertEquals(listOf(call), api.lastRunGetMethodsRequest?.calls)

            assertThrows(TonIndexerRoutes.TonIndexerRouteException::class.java) {
                runBlocking {
                    client.runGetMethod(ADDRESS, "bad-method")
                }
            }
            assertThrows(TonIndexerRoutes.TonIndexerRouteException::class.java) {
                runBlocking {
                    client.runGetMethods(emptyList())
                }
            }
        }
    }

    @Test
    fun `verify service info rejects misrouted ton indexer`() = runBlocking {
        val api = FakeTonIndexerApi()
        val client = RetrofitTonIndexerClient(api)

        api.serviceInfoResponse = api.serviceInfoResponse.copy(serviceId = "si.soramitsu.io")

        val error = assertThrows(TonIndexerIdentityException::class.java) {
            runBlocking {
                client.verifyServiceInfo()
            }
        }
        assertEquals("si.soramitsu.io", error.serviceInfo.serviceId)
        assertEquals("https://ti.soramitsu.io/api/indexer/v1/service-info", api.lastUrl)
    }

    private class FakeTonIndexerApi : TonIndexerApi {
        var lastUrl: String? = null
            private set
        var lastRunGetMethodRequest: TonRunGetMethodRequest? = null
            private set
        var lastRunGetMethodsRequest: TonRunGetMethodsRequest? = null
            private set
        var serviceInfoResponse = TonIndexerServiceInfo(
            schemaVersion = 1,
            serviceId = "ti.soramitsu.io",
            serviceName = "TON Indexer",
            ecosystem = "ton",
            chainId = "ton:mainnet",
            network = "mainnet",
            publicBaseUrl = "https://ti.soramitsu.io",
            readOnly = true
        )

        override suspend fun getHealth(url: String): TonHealthStatus {
            lastUrl = url
            return TonHealthStatus()
        }

        override suspend fun getContracts(url: String): TonContractsResponse {
            lastUrl = url
            return TonContractsResponse(count = 0)
        }

        override suspend fun getServiceInfo(url: String): TonIndexerServiceInfo {
            lastUrl = url
            return serviceInfoResponse
        }

        override suspend fun getBalance(url: String): TonBalanceResponse {
            lastUrl = url
            return TonBalanceResponse(
                ton = TonNativeBalance(balance = "0"),
                confirmed = true,
                updatedAt = 1L,
                network = "mainnet"
            )
        }

        override suspend fun getBalances(url: String): TonBalancesResponse {
            lastUrl = url
            return TonBalancesResponse(
                address = ADDRESS,
                tonRaw = "0",
                ton = "0",
                confirmed = true,
                updatedAt = 1L,
                network = "mainnet"
            )
        }

        override suspend fun getAssets(url: String): TonBalancesResponse {
            lastUrl = url
            return getBalances(url)
        }

        override suspend fun getState(url: String): TonAccountStateResponse {
            lastUrl = url
            return TonAccountStateResponse(
                address = ADDRESS,
                balance = "0",
                updatedAt = 1L
            )
        }

        override suspend fun getTransactions(url: String): TonTransactionsResponse {
            lastUrl = url
            return TonTransactionsResponse(
                page = 1,
                pageSize = 0,
                totalTxs = 0,
                totalPagesMin = 0,
                historyComplete = true,
                network = "mainnet"
            )
        }

        override suspend fun getSwaps(url: String): TonSwapsResponse {
            lastUrl = url
            return TonSwapsResponse(
                address = ADDRESS,
                count = 0,
                network = "mainnet"
            )
        }

        override suspend fun getJettonTransferPayload(url: String): TonJettonTransferPayloadResponse {
            lastUrl = url
            return TonJettonTransferPayloadResponse()
        }

        override suspend fun runGetMethod(
            url: String,
            body: TonRunGetMethodRequest
        ): TonRunGetMethodResponse {
            lastUrl = url
            lastRunGetMethodRequest = body
            return TonRunGetMethodResponse(exitCode = 0, gasUsed = 0)
        }

        override suspend fun runGetMethods(
            url: String,
            body: TonRunGetMethodsRequest
        ): TonRunGetMethodsResponse {
            lastUrl = url
            lastRunGetMethodsRequest = body
            return TonRunGetMethodsResponse()
        }
    }

    private companion object {
        const val ADDRESS = "UQDxAUFadQXDd3EXGa3TLF_EF66gMc9h3_aZ0j0zXNoIYUCc"
        const val RAW_ADDRESS = "0:f101415a7505c377711719add32c5fc417aea031cf61dff699d23d335cda0861"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val ENCODED_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA%3D"
    }
}
