package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerApi
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.RetrofitBitcoinIndexerClient
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinIndexerClientTest {

    @Test
    fun `delegates esplora read endpoints through validated routes`() = runBlocking {
        val api = FakeBitcoinIndexerApi()
        val client = RetrofitBitcoinIndexerClient(api)

        client.address(MAINNET_ADDRESS)
        assertEquals("https://blockstream.info/api/address/$MAINNET_ADDRESS", api.lastUrl)

        client.utxos(TESTNET_ADDRESS, BitcoinIndexerRoutes.Network.Testnet)
        assertEquals("https://blockstream.info/testnet/api/address/$TESTNET_ADDRESS/utxo", api.lastUrl)

        client.transactions(MAINNET_ADDRESS, lastSeenTxid = TXID.uppercase())
        assertEquals("https://blockstream.info/api/address/$MAINNET_ADDRESS/txs/chain/$TXID", api.lastUrl)

        client.transactions(MAINNET_ADDRESS, mempool = true)
        assertEquals("https://blockstream.info/api/address/$MAINNET_ADDRESS/txs/mempool", api.lastUrl)
    }

    @Test
    fun `delegates broadcast with normalized text transaction body`() {
        runBlocking {
            val api = FakeBitcoinIndexerApi()
            val client = RetrofitBitcoinIndexerClient(api)

            client.broadcastTransaction("  00AA  ", BitcoinIndexerRoutes.Network.Testnet)

            assertEquals("https://blockstream.info/testnet/api/tx", api.lastUrl)
            assertEquals("text/plain", api.lastRequestBody?.contentType().toString())
            assertEquals("00aa", api.lastRequestBody?.readUtf8())

            assertThrows(BitcoinIndexerRoutes.BitcoinIndexerRouteException::class.java) {
                runBlocking {
                    client.broadcastTransaction("00gg")
                }
            }
        }
    }

    private class FakeBitcoinIndexerApi : BitcoinIndexerApi {
        var lastUrl: String? = null
            private set
        var lastRequestBody: RequestBody? = null
            private set

        override suspend fun getAddress(url: String): BitcoinEsploraAddress {
            lastUrl = url
            return BitcoinEsploraAddress(
                address = MAINNET_ADDRESS,
                chainStats = BitcoinEsploraStats(0, 0, 0, 0, 0),
                mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
            )
        }

        override suspend fun getUtxos(url: String): List<BitcoinEsploraUtxo> {
            lastUrl = url
            return listOf(
                BitcoinEsploraUtxo(
                    txid = TXID,
                    vout = 0,
                    value = 1,
                    status = BitcoinEsploraTxStatus(confirmed = true)
                )
            )
        }

        override suspend fun getTransactions(url: String): List<BitcoinEsploraTransaction> {
            lastUrl = url
            return listOf(
                BitcoinEsploraTransaction(
                    txid = TXID,
                    status = BitcoinEsploraTxStatus(confirmed = true)
                )
            )
        }

        override suspend fun getFeeEstimates(url: String): Map<String, Double> {
            lastUrl = url
            return mapOf("1" to 1.0)
        }

        override suspend fun broadcastTransaction(
            url: String,
            body: RequestBody
        ): String {
            lastUrl = url
            lastRequestBody = body
            return TXID
        }
    }

    private fun RequestBody.readUtf8(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    private companion object {
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        val TXID = "11".repeat(32)
    }
}
