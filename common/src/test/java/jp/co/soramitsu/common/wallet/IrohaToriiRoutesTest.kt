package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.CountMode
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.ErrorCode
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.IrohaToriiRouteException
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.TransactionStatusScope
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class IrohaToriiRoutesTest {

    @Test
    fun `builds taira torii endpoint urls with validated parameters`() {
        assertEquals("https://taira.sora.org/health", IrohaToriiRoutes.healthUrl())
        assertEquals("https://taira.sora.org/v1/mcp", IrohaToriiRoutes.mcpUrl())
        assertEquals(
            "https://taira.sora.org/v1/accounts?limit=50&offset=10&count_mode=exact",
            IrohaToriiRoutes.accountsUrl(limit = 50, offset = 10, countMode = CountMode.Exact)
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT",
            IrohaToriiRoutes.accountUrl(ACCOUNT)
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/assets?limit=25&count_mode=bounded&asset=xor%23sora&scope=global",
            IrohaToriiRoutes.accountAssetsUrl(
                accountId = ACCOUNT,
                limit = 25,
                countMode = CountMode.Bounded,
                asset = "xor#sora",
                scope = "global"
            )
        )
        assertEquals("https://taira.sora.org/v1/assets/definitions", IrohaToriiRoutes.assetDefinitionsUrl())
        assertEquals("https://taira.sora.org/v1/pipeline/transactions", IrohaToriiRoutes.submitTransactionUrl())
        assertEquals(
            "https://taira.sora.org/v1/pipeline/transactions/status?hash=$HASH&scope=global",
            IrohaToriiRoutes.transactionStatusUrl("0x$HASH", scope = TransactionStatusScope.Global)
        )
        assertEquals("tools/list", IrohaToriiRoutes.mcpJsonRpcRequest(" tools/list ", "1").method)
    }

    @Test
    fun `allows local http base urls but rejects nonlocal insecure base urls`() {
        assertEquals("http://localhost:8080", IrohaToriiRoutes.normalizeBaseUrl("http://localhost:8080/"))
        assertEquals("http://127.0.0.1:8080", IrohaToriiRoutes.normalizeBaseUrl("http://127.0.0.1:8080/"))

        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            IrohaToriiRoutes.normalizeBaseUrl("http://taira.sora.org")
        }
        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            IrohaToriiRoutes.normalizeBaseUrl("not a url")
        }
        assertEquals("https://minamoto.sora.org", IrohaToriiRoutes.requireToriiBaseUrl(UniversalWalletRegistry.nexus))
        assertEquals("https://minamoto.sora.org/v1/mcp", IrohaToriiRoutes.mcpUrl(UniversalWalletRegistry.nexus))
        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)
    }

    @Test
    fun `rejects malformed iroha route inputs before network calls`() {
        assertRouteError(ErrorCode.INVALID_ACCOUNT_ID) {
            IrohaToriiRoutes.accountUrl("../bad")
        }
        assertRouteError(ErrorCode.INVALID_ASSET) {
            IrohaToriiRoutes.accountAssetsUrl(ACCOUNT, asset = "../bad")
        }
        assertRouteError(ErrorCode.INVALID_SCOPE) {
            IrohaToriiRoutes.accountAssetsUrl(ACCOUNT, scope = "bad/scope")
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            IrohaToriiRoutes.accountsUrl(limit = 0)
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            IrohaToriiRoutes.accountsUrl(limit = 501)
        }
        assertRouteError(ErrorCode.INVALID_OFFSET) {
            IrohaToriiRoutes.accountsUrl(offset = -1)
        }
        assertRouteError(ErrorCode.INVALID_HASH) {
            IrohaToriiRoutes.transactionStatusUrl("not-a-hash")
        }
        assertRouteError(ErrorCode.INVALID_JSON_RPC_ID) {
            IrohaToriiRoutes.mcpJsonRpcRequest("tools/list", "../bad")
        }
        assertRouteError(ErrorCode.INVALID_MCP_METHOD) {
            IrohaToriiRoutes.mcpJsonRpcRequest("../bad", "1")
        }
    }

    @Test
    fun `parses account asset quantities as strings`() {
        val response = Gson().fromJson(
            """
            {
              "items": [
                {
                  "account_id": "$ACCOUNT",
                  "asset": "xor#sora",
                  "quantity": "340282366920938463463374607431768211455",
                  "scope": "global"
                }
              ],
              "has_more": false,
              "count_mode": "exact",
              "total": 1
            }
            """.trimIndent(),
            IrohaAccountAssetListResponse::class.java
        )

        assertEquals("340282366920938463463374607431768211455", response.items.single().quantity)
        assertEquals("exact", response.countMode)
        assertEquals(1L, response.total)
    }

    private fun assertRouteError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(IrohaToriiRouteException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val ACCOUNT = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱﾇﾆｲMﾒSﾏﾑヱﾇJヱFmJﾇMs6YN687Y"
        const val ENCODED_ACCOUNT = "testu%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BE%87%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BE%91%E3%83%B1%EF%BE%87J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        val HASH = "a".repeat(64)
    }
}
