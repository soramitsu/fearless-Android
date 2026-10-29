package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountHistoryPage
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.CountMode
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.ErrorCode
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.IrohaToriiRouteException
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.TransactionStatusScope
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class IrohaToriiRoutesTest {

    @Test
    fun `builds taira torii endpoint urls with validated parameters`() {
        assertEquals("https://taira.sora.org/health", IrohaToriiRoutes.healthUrl())
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
        assertEquals(
            "https://taira.sora.org/v1/assets/definitions?limit=500&offset=500&count_mode=bounded",
            IrohaToriiRoutes.assetDefinitionsUrl(
                limit = 500,
                offset = 500,
                countMode = CountMode.Bounded
            )
        )
        assertEquals("https://taira.sora.org/v1/pipeline/transactions", IrohaToriiRoutes.submitTransactionUrl())
        assertEquals(
            "https://taira.sora.org/v1/pipeline/transactions/status?hash=$HASH&scope=global",
            IrohaToriiRoutes.transactionStatusUrl("0x$HASH", scope = TransactionStatusScope.Global)
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/history",
            IrohaToriiRoutes.accountHistoryUrl(ACCOUNT)
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/history?limit=100&cursor=AQ-0aPCF_x&filter=$ENCODED_TRANSFER_FILTER",
            IrohaToriiRoutes.accountHistoryUrl(
                accountId = ACCOUNT,
                limit = 100,
                cursor = "AQ-0aPCF_x",
                filter = IrohaToriiRoutes.transferHistoryFilter(listOf(XOR_DEFINITION))
            )
        )
    }

    @Test
    fun `builds transfer history filters with quoted asset definition literals`() {
        assertEquals(
            "asset_definition_id in [\"$XOR_DEFINITION\"] and type = \"TRANSFER\"",
            IrohaToriiRoutes.transferHistoryFilter(listOf(" $XOR_DEFINITION ", XOR_DEFINITION))
        )
        assertEquals(
            "asset_definition_id in [\"xor#universal\", \"a\\\"b\\\\c\"] and type = \"TRANSFER\"",
            IrohaToriiRoutes.transferHistoryFilter(listOf("xor#universal", "a\"b\\c"))
        )
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
        assertEquals(
            "https://minamoto.sora.org/v1/accounts/$ENCODED_ACCOUNT/history?limit=1",
            IrohaToriiRoutes.accountHistoryUrl(
                accountId = ACCOUNT,
                baseUrl = IrohaToriiRoutes.requireToriiBaseUrl(UniversalWalletRegistry.nexus),
                limit = 1
            )
        )
        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)
    }

    @Test
    fun `rejects base urls whose authority or suffix can change endpoint routing`() {
        listOf(
            "https:///missing-host", "https://user@taira.sora.org", "https://taira.sora.org?query=1",
            "https://taira.sora.org#fragment", "ftp://localhost", "file://localhost/path"
        ).forEach { url ->
            assertRouteError(ErrorCode.INVALID_BASE_URL) { IrohaToriiRoutes.normalizeBaseUrl(url) }
        }
        assertEquals("https://taira.sora.org/base", IrohaToriiRoutes.normalizeBaseUrl(" https://taira.sora.org/base/ "))
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
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            IrohaToriiRoutes.accountHistoryUrl(ACCOUNT, limit = 501)
        }
        listOf("", "1 2", "../bad", "a=b", "x".repeat(4097)).forEach { cursor ->
            assertRouteError(ErrorCode.INVALID_CURSOR) {
                IrohaToriiRoutes.accountHistoryUrl(ACCOUNT, cursor = cursor)
            }
        }
        listOf(" ", "type = \"TRANSFER\"\nor true", "x".repeat(2049)).forEach { filter ->
            assertRouteError(ErrorCode.INVALID_FILTER) {
                IrohaToriiRoutes.accountHistoryUrl(ACCOUNT, filter = filter)
            }
        }
        listOf(emptyList(), listOf(" "), listOf("bad asset"), listOf("bad/asset")).forEach { ids ->
            assertRouteError(ErrorCode.INVALID_ASSET) {
                IrohaToriiRoutes.transferHistoryFilter(ids)
            }
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

    @Test
    fun `parses taira account history movements`() {
        // Shape served by https://taira.sora.org/v1/accounts/{account_id}/history on 2026-10-23.
        val page = Gson().fromJson(
            """
            {
              "items": [
                {
                  "account_id": "$ACCOUNT",
                  "amount": "100000",
                  "asset_definition_id": "$XOR_DEFINITION",
                  "asset_id": "$XOR_DEFINITION#$ACCOUNT",
                  "block_height": 13,
                  "block_index": 0,
                  "counterparty_account_id": "$COUNTERPARTY",
                  "direction": "incoming",
                  "id": "$TX_HASH:$ACCOUNT:1",
                  "movement_index": 1,
                  "result_ok": true,
                  "source": "transaction",
                  "status": "SUCCESS",
                  "timestamp_ms": 1792667418176,
                  "tx_hash": "$TX_HASH",
                  "type": "TRANSFER"
                },
                {
                  "account_id": "$ACCOUNT",
                  "block_height": 45,
                  "block_index": 0,
                  "direction": "affected",
                  "id": "$TX_HASH:$ACCOUNT:0",
                  "movement_index": 0,
                  "result_ok": true,
                  "source": "transaction",
                  "status": "SUCCESS",
                  "timestamp_ms": 1792669094184,
                  "tx_hash": "$TX_HASH",
                  "type": "RAW_ON_CHAIN"
                }
              ],
              "next_cursor": "AQ-0aPCFFyuWV0_0bMRiLfGRWzMzLDAsMF0"
            }
            """.trimIndent(),
            IrohaAccountHistoryPage::class.java
        )

        val transfer = page.items.first()
        assertEquals("AQ-0aPCFFyuWV0_0bMRiLfGRWzMzLDAsMF0", page.nextCursor)
        assertEquals("TRANSFER", transfer.type)
        assertEquals("incoming", transfer.direction)
        assertEquals(ACCOUNT, transfer.accountId)
        assertEquals(COUNTERPARTY, transfer.counterpartyAccountId)
        assertEquals(XOR_DEFINITION, transfer.assetDefinitionId)
        assertEquals("100000", transfer.amount)
        assertEquals(TX_HASH, transfer.txHash)
        assertEquals(1_792_667_418_176L, transfer.timestampMs)
        assertEquals(1L, transfer.movementIndex)
        assertEquals("SUCCESS", transfer.status)
        assertEquals(true, transfer.resultOk)

        val raw = page.items.last()
        assertEquals("RAW_ON_CHAIN", raw.type)
        assertNull(raw.amount)
        assertNull(raw.counterpartyAccountId)
        assertNull(raw.assetDefinitionId)
        assertNull(Gson().fromJson("{\"items\":[],\"next_cursor\":null}", IrohaAccountHistoryPage::class.java).nextCursor)
    }

    private fun assertRouteError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(IrohaToriiRouteException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val ACCOUNT = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱﾇﾆｲMﾒSﾏﾑヱﾇJヱFmJﾇMs6YN687Y"
        const val ENCODED_ACCOUNT = "testu%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BE%87%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BE%91%E3%83%B1%EF%BE%87J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        const val COUNTERPARTY = "testuﾛ1PpﾜｲﾇLikｦdMxLﾖ8QﾜXRbﾈTｴｷｵg9juｲCｴS9Cyﾎ3ﾐUXHFVF"
        const val XOR_DEFINITION = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val TX_HASH = "886e30365349ab6380c728a9be08682784f37dfa3de57adbdf0cfb7e07e585b5"
        const val ENCODED_TRANSFER_FILTER =
            "asset_definition_id%20in%20%5B%226TEAJqbb8oEPmLncoNiMRbLEK6tw%22%5D%20and%20type%20%3D%20%22TRANSFER%22"
        val HASH = "a".repeat(64)
    }
}
