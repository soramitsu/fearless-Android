package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerServiceInfo
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes.ErrorCode
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes.SolanaIndexerRouteException
import jp.co.soramitsu.common.data.network.solana.SolanaWalletBalancesResponse
import jp.co.soramitsu.common.data.network.solana.isExpectedSiServiceInfo
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaIndexerRoutesTest {

    @Test
    fun `builds si wallet endpoint urls with validated parameters`() {
        assertEquals(
            "${UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL}/api/indexer/v1/service-info",
            SolanaIndexerRoutes.serviceInfoUrl()
        )
        assertEquals(
            "${UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL}/api/indexer/v1/accounts/$WALLET/balances",
            SolanaIndexerRoutes.balancesUrl(WALLET)
        )
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/accounts/$WALLET/assets",
            SolanaIndexerRoutes.assetsUrl(WALLET, "https://si.soramitsu.io/")
        )
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/accounts/$WALLET/state",
            SolanaIndexerRoutes.stateUrl(WALLET, "https://si.soramitsu.io/")
        )
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/accounts/$WALLET/txs?limit=25&before=$SIGNATURE",
            SolanaIndexerRoutes.transactionsUrl(WALLET, "https://si.soramitsu.io/", SIGNATURE, 25)
        )
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/tokens/$MINT/metadata",
            SolanaIndexerRoutes.tokenMetadataUrl(MINT, "https://si.soramitsu.io/")
        )
        assertEquals(
            "https://si.soramitsu.io/api/indexer/v1/tokens/metadata",
            SolanaIndexerRoutes.tokenMetadataBatchUrl("https://si.soramitsu.io/")
        )
        assertEquals(listOf(MINT), SolanaIndexerRoutes.tokenMetadataBatchRequest(listOf(MINT)).mints)
    }

    @Test
    fun `allows local http base urls but rejects nonlocal insecure base urls`() {
        assertEquals("http://localhost:3000", SolanaIndexerRoutes.normalizeBaseUrl("http://localhost:3000/"))
        assertEquals("http://127.0.0.1:3000", SolanaIndexerRoutes.normalizeBaseUrl("http://127.0.0.1:3000/"))

        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            SolanaIndexerRoutes.normalizeBaseUrl("http://si.soramitsu.io")
        }
        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            SolanaIndexerRoutes.normalizeBaseUrl("not a url")
        }
    }

    @Test
    fun `rejects malformed wallet tx and token metadata inputs`() {
        assertRouteError(ErrorCode.INVALID_WALLET) {
            SolanaIndexerRoutes.balancesUrl("../bad")
        }
        assertRouteError(ErrorCode.INVALID_MINT) {
            SolanaIndexerRoutes.tokenMetadataUrl("../bad")
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            SolanaIndexerRoutes.transactionsUrl(WALLET, limit = 0)
        }
        assertRouteError(ErrorCode.INVALID_LIMIT) {
            SolanaIndexerRoutes.transactionsUrl(WALLET, limit = 251)
        }
        assertRouteError(ErrorCode.INVALID_BEFORE) {
            SolanaIndexerRoutes.transactionsUrl(WALLET, before = "../../../bad")
        }
        assertRouteError(ErrorCode.INVALID_MINTS) {
            SolanaIndexerRoutes.tokenMetadataBatchRequest(emptyList())
        }
        assertRouteError(ErrorCode.INVALID_MINTS) {
            SolanaIndexerRoutes.tokenMetadataBatchRequest(List(101) { MINT })
        }
    }

    @Test
    fun `parses si balances without losing large integer precision`() {
        val response = Gson().fromJson(
            """
            {
              "wallet": "$WALLET",
              "native": {
                "type": "native",
                "mint": "SOL",
                "lamports": "1234567890",
                "decimals": 9,
                "uiAmountString": "1.234567890"
              },
              "tokens": [
                {
                  "type": "token",
                  "accountAddress": "$TOKEN_ACCOUNT",
                  "mint": "$MINT",
                  "owner": "$WALLET",
                  "program": "spl-token",
                  "programId": "$TOKEN_PROGRAM",
                  "amount": "18446744073709551616",
                  "decimals": 6,
                  "uiAmountString": "18446744073709.551616",
                  "state": "initialized",
                  "isNative": false,
                  "delegatedAmount": null,
                  "rentExemptReserve": null
                }
              ],
              "total": 2,
              "syncedAt": 1710000000000
            }
            """.trimIndent(),
            SolanaWalletBalancesResponse::class.java
        )

        assertEquals(WALLET, response.wallet)
        assertEquals("1234567890", response.native.lamports)
        assertEquals("18446744073709551616", response.tokens.single().amount)
        assertEquals("spl-token", response.tokens.single().program)
        assertEquals(2, response.total)
    }

    @Test
    fun `verifies si service identity and rejects misrouted service info`() {
        val response = Gson().fromJson(
            """
            {
              "schemaVersion": 1,
              "serviceId": "si.soramitsu.io",
              "serviceName": "Solswap Indexer",
              "ecosystem": "solana",
              "chainId": "solana:mainnet",
              "network": "mainnet",
              "publicBaseUrl": "https://si.soramitsu.io",
              "readOnly": true,
              "capabilities": ["wallet-transactions"],
              "endpoints": {
                "transactions": "/api/indexer/v1/accounts/{wallet}/txs"
              }
            }
            """.trimIndent(),
            SolanaIndexerServiceInfo::class.java
        )

        assertTrue(response.isExpectedSiServiceInfo())
        assertFalse(response.copy(serviceId = "ti.soramitsu.io").isExpectedSiServiceInfo())
        assertFalse(response.copy(ecosystem = "ton").isExpectedSiServiceInfo())
    }

    private fun assertRouteError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(SolanaIndexerRouteException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val WALLET = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk"
        const val MINT = "So11111111111111111111111111111111111111112"
        const val TOKEN_ACCOUNT = "9xQeWvG816bUx9EPfQ4vF5xXw4wa9VFeTuzA7h4sFnH"
        const val TOKEN_PROGRAM = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        val SIGNATURE = "1".repeat(88)
    }
}
