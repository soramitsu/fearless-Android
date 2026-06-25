package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes.BitcoinIndexerRouteException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes.ErrorCode
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes.Network
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinIndexerRoutesTest {

    @Test
    fun `builds bitcoin esplora endpoint urls with validated parameters`() {
        assertEquals(
            "${UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL}/address/$MAINNET_ADDRESS",
            BitcoinIndexerRoutes.addressUrl(MAINNET_ADDRESS)
        )
        assertEquals(
            "https://blockstream.info/testnet/api/address/$TESTNET_ADDRESS/utxo",
            BitcoinIndexerRoutes.utxosUrl(TESTNET_ADDRESS, Network.Testnet)
        )
        assertEquals(
            "https://bitcoin.example/api/address/$MAINNET_ADDRESS/txs",
            BitcoinIndexerRoutes.transactionsUrl(MAINNET_ADDRESS, baseUrl = "https://bitcoin.example/api/")
        )
        assertEquals(
            "https://bitcoin.example/api/address/$MAINNET_ADDRESS/txs/chain/$TXID",
            BitcoinIndexerRoutes.transactionsUrl(MAINNET_ADDRESS, baseUrl = "https://bitcoin.example/api/", lastSeenTxid = TXID.uppercase())
        )
        assertEquals(
            "https://bitcoin.example/api/address/$MAINNET_ADDRESS/txs/mempool",
            BitcoinIndexerRoutes.transactionsUrl(MAINNET_ADDRESS, baseUrl = "https://bitcoin.example/api/", mempool = true)
        )
        assertEquals(
            "https://blockstream.info/api/fee-estimates",
            BitcoinIndexerRoutes.feeEstimatesUrl()
        )
        assertEquals(
            "https://blockstream.info/testnet/api/tx",
            BitcoinIndexerRoutes.broadcastTransactionUrl(Network.Testnet)
        )
        assertEquals("00aa", BitcoinIndexerRoutes.normalizeBroadcastTransactionBody("  00AA  "))
    }

    @Test
    fun `allows local http base urls but rejects nonlocal insecure base urls`() {
        assertEquals("http://localhost:3000/api", BitcoinIndexerRoutes.normalizeBaseUrl("http://localhost:3000/api/"))
        assertEquals("http://127.0.0.1:3000/api", BitcoinIndexerRoutes.normalizeBaseUrl("http://127.0.0.1:3000/api/"))

        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            BitcoinIndexerRoutes.normalizeBaseUrl("http://blockstream.info/api")
        }
        assertRouteError(ErrorCode.INVALID_BASE_URL) {
            BitcoinIndexerRoutes.normalizeBaseUrl("not a url")
        }
    }

    @Test
    fun `rejects malformed bitcoin route inputs before network calls`() {
        assertRouteError(ErrorCode.INVALID_ADDRESS) {
            BitcoinIndexerRoutes.addressUrl("../bad")
        }
        assertRouteError(ErrorCode.INVALID_ADDRESS) {
            BitcoinIndexerRoutes.addressUrl(TESTNET_ADDRESS, Network.Mainnet)
        }
        assertRouteError(ErrorCode.INVALID_TXID) {
            BitcoinIndexerRoutes.transactionsUrl(MAINNET_ADDRESS, lastSeenTxid = "../../../bad")
        }
        assertRouteError(ErrorCode.INVALID_TX_HEX) {
            BitcoinIndexerRoutes.normalizeBroadcastTransactionBody("abc")
        }
        assertRouteError(ErrorCode.INVALID_TX_HEX) {
            BitcoinIndexerRoutes.normalizeBroadcastTransactionBody("00gg")
        }
        assertRouteError(ErrorCode.INVALID_TX_HEX) {
            BitcoinIndexerRoutes.normalizeBroadcastTransactionBody("00".repeat(BitcoinIndexerRoutes.MAX_TX_HEX_LENGTH / 2 + 1))
        }
    }

    @Test
    fun `parses esplora address stats without losing satoshi precision`() {
        val response = Gson().fromJson(
            """
            {
              "address": "$MAINNET_ADDRESS",
              "chain_stats": {
                "funded_txo_count": 2,
                "funded_txo_sum": 2100000000000000,
                "spent_txo_count": 1,
                "spent_txo_sum": 123456789,
                "tx_count": 3
              },
              "mempool_stats": {
                "funded_txo_count": 1,
                "funded_txo_sum": 5000,
                "spent_txo_count": 0,
                "spent_txo_sum": 0,
                "tx_count": 1
              }
            }
            """.trimIndent(),
            BitcoinEsploraAddress::class.java
        )

        assertEquals(MAINNET_ADDRESS, response.address)
        assertEquals(2_099_999_876_543_211L, response.confirmedSats)
        assertEquals(5_000L, response.mempoolSats)
        assertEquals(2_099_999_876_548_211L, response.totalSats)
    }

    @Test
    fun `parses esplora transaction movement outputs without coercing string amounts`() {
        val response = Gson().fromJson(
            """
            {
              "txid": "$TXID",
              "status": {
                "confirmed": true
              },
              "vin": [
                {
                  "prevout": {
                    "scriptpubkey_address": "$MAINNET_ADDRESS",
                    "value": 1000
                  }
                }
              ],
              "vout": [
                {
                  "scriptpubkey_address": "$MAINNET_ADDRESS",
                  "value": "900"
                }
              ]
            }
            """.trimIndent(),
            BitcoinEsploraTransaction::class.java
        )

        assertEquals(1_000L, response.vin.orEmpty().single().prevout?.valueSatsOrNull())
        assertEquals(null, response.vout.orEmpty().single().valueSatsOrNull())
    }

    private fun assertRouteError(expected: ErrorCode, block: () -> Unit) {
        val error = assertThrows(BitcoinIndexerRouteException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private companion object {
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        val TXID = "11".repeat(32)
    }
}
