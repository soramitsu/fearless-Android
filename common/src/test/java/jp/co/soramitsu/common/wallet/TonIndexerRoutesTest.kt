package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.ton.TonBalanceResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerServiceInfo
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes.ErrorCode
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes.TonIndexerRouteException
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes.TonSwapExecutionType
import jp.co.soramitsu.common.data.network.ton.TonIndexerRoutes.TonSwapStatus
import jp.co.soramitsu.common.data.network.ton.isExpectedTiServiceInfo
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TonIndexerRoutesTest {

    @Test
    fun `builds ti endpoint urls with validated parameters`() {
        assertEquals(
            "${UniversalWalletRegistry.TON_INDEXER_BASE_URL}/api/indexer/v1/health",
            TonIndexerRoutes.healthUrl()
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/contracts",
            TonIndexerRoutes.contractsUrl("https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/service-info",
            TonIndexerRoutes.serviceInfoUrl("https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/balance",
            TonIndexerRoutes.balanceUrl(ADDRESS, "https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/balances",
            TonIndexerRoutes.balancesUrl(ADDRESS, "https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/assets",
            TonIndexerRoutes.assetsUrl(ADDRESS, "https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/state",
            TonIndexerRoutes.stateUrl(ADDRESS, "https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/txs?page=2&cursor_lt=10&cursor_hash=$ENCODED_HASH",
            TonIndexerRoutes.transactionsUrl(ADDRESS, "https://ti.soramitsu.io/", page = 2, cursorLt = "10", cursorHash = HASH)
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/accounts/$ADDRESS/swaps?limit=25&from_utime=100&to_utime=200&pay_token=TON&receive_token=TST&execution_type=market&status=success&include_reverse=true",
            TonIndexerRoutes.swapsUrl(
                address = ADDRESS,
                baseUrl = "https://ti.soramitsu.io/",
                limit = 25,
                fromUtime = 100,
                toUtime = 200,
                payToken = "TON",
                receiveToken = "TST",
                executionType = TonSwapExecutionType.Market,
                status = TonSwapStatus.Success,
                includeReverse = true
            )
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/jettons/$ADDRESS/transfer/$RAW_ADDRESS/payload",
            TonIndexerRoutes.jettonTransferPayloadUrl(ADDRESS, RAW_ADDRESS.uppercase(), "https://ti.soramitsu.io/")
        )
        assertEquals(
            "https://ti.soramitsu.io/api/indexer/v1/runGetMethod",
            TonIndexerRoutes.runGetMethodUrl("https://ti.soramitsu.io/")
        )
        assertEquals(
            "seqno",
            TonIndexerRoutes.runGetMethodRequest(ADDRESS, " seqno ").method
        )
        assertEquals(
            1,
            TonIndexerRoutes.runGetMethodsRequest(listOf(TonIndexerRoutes.runGetMethodRequest(ADDRESS, "seqno"))).calls.size
        )
    }

    @Test
    fun `allows local http base urls but rejects nonlocal insecure base urls`() {
        assertEquals("http://localhost:3000", TonIndexerRoutes.normalizeBaseUrl("http://localhost:3000/"))
        assertEquals("http://127.0.0.1:3000", TonIndexerRoutes.normalizeBaseUrl("http://127.0.0.1:3000/"))

        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            TonIndexerRoutes.normalizeBaseUrl("http://ti.soramitsu.io")
        }
        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            TonIndexerRoutes.normalizeBaseUrl("not a url")
        }
    }

    @Test
    fun `rejects malformed ton route inputs before network calls`() {
        assertRouteError(ErrorCode.INVALID_ADDRESS) {
            TonIndexerRoutes.balanceUrl("../bad")
        }
        assertRouteError(ErrorCode.INVALID_PAGE) {
            TonIndexerRoutes.transactionsUrl(ADDRESS, page = 0)
        }
        assertRouteError(ErrorCode.CURSOR_MISMATCH) {
            TonIndexerRoutes.transactionsUrl(ADDRESS, cursorLt = "1")
        }
        assertRouteError(ErrorCode.INVALID_CURSOR) {
            TonIndexerRoutes.transactionsUrl(ADDRESS, cursorLt = "bad", cursorHash = HASH)
        }
        assertRouteError(ErrorCode.INVALID_CURSOR) {
            TonIndexerRoutes.transactionsUrl(ADDRESS, cursorLt = "1", cursorHash = "../../../bad")
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            TonIndexerRoutes.swapsUrl(ADDRESS, limit = 0)
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            TonIndexerRoutes.swapsUrl(ADDRESS, limit = 501)
        }
        assertRouteError(ErrorCode.INVALID_UTIME_RANGE) {
            TonIndexerRoutes.swapsUrl(ADDRESS, fromUtime = 20, toUtime = 10)
        }
        assertRouteError(ErrorCode.INVALID_TOKEN_FILTER) {
            TonIndexerRoutes.swapsUrl(ADDRESS, payToken = "../bad")
        }
        assertRouteError(ErrorCode.INVALID_METHOD) {
            TonIndexerRoutes.runGetMethodRequest(ADDRESS, "bad-method")
        }
        assertRouteError(ErrorCode.INVALID_CALLS) {
            TonIndexerRoutes.runGetMethodsRequest(emptyList())
        }
        assertRouteError(ErrorCode.INVALID_CALLS) {
            val call = TonIndexerRoutes.runGetMethodRequest(ADDRESS, "seqno")
            TonIndexerRoutes.runGetMethodsRequest(List(65) { call })
        }
    }

    @Test
    fun `parses ti balance without losing large integer precision`() {
        val response = Gson().fromJson(
            """
            {
              "ton": {
                "balance": "340282366920938463463374607431768211455",
                "last_tx_lt": "12345678901234567890",
                "last_tx_hash": "$HASH"
              },
              "jettons": [
                {
                  "master": "$ADDRESS",
                  "wallet": "$RAW_ADDRESS",
                  "balance": "18446744073709551616",
                  "decimals": 9,
                  "symbol": "TST"
                }
              ],
              "confirmed": true,
              "updated_at": 1710000000000,
              "network": "mainnet"
            }
            """.trimIndent(),
            TonBalanceResponse::class.java
        )

        assertEquals("340282366920938463463374607431768211455", response.ton.balance)
        assertEquals("12345678901234567890", response.ton.lastTxLt)
        assertEquals("18446744073709551616", response.jettons.single().balance)
        assertEquals("mainnet", response.network)
    }

    @Test
    fun `verifies ti service identity and rejects misrouted service info`() {
        val response = Gson().fromJson(
            """
            {
              "schemaVersion": 1,
              "serviceId": "ti.soramitsu.io",
              "serviceName": "TON Indexer",
              "ecosystem": "ton",
              "chainId": "ton:mainnet",
              "network": "mainnet",
              "publicBaseUrl": "https://ti.soramitsu.io",
              "readOnly": true,
              "capabilities": ["account-transactions"],
              "endpoints": {
                "transactions": "/api/indexer/v1/accounts/{addr}/txs"
              }
            }
            """.trimIndent(),
            TonIndexerServiceInfo::class.java
        )

        assertTrue(response.isExpectedTiServiceInfo())
        assertFalse(response.copy(serviceId = "si.soramitsu.io").isExpectedTiServiceInfo())
        assertFalse(response.copy(readOnly = false).isExpectedTiServiceInfo())
    }

    private fun assertRouteError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(TonIndexerRouteException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val ADDRESS = "UQDxAUFadQXDd3EXGa3TLF_EF66gMc9h3_aZ0j0zXNoIYUCc"
        const val RAW_ADDRESS = "0:f101415a7505c377711719add32c5fc417aea031cf61dff699d23d335cda0861"
        const val HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        const val ENCODED_HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA%3D"
    }
}
