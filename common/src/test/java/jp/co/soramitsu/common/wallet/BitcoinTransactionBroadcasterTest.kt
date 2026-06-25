package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinBroadcastException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionBroadcaster
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinTransactionBroadcasterTest {

    @Test
    fun `normalizes transaction hex delegates selected network and validates txid response`() = runBlocking {
        val client = FakeBitcoinIndexerClient(TXID.uppercase())
        val broadcaster = BitcoinTransactionBroadcaster(client)

        val result = broadcaster.broadcast(
            txHex = "  00AA  ",
            network = BitcoinIndexerRoutes.Network.Testnet,
            baseUrl = "https://bitcoin.example/api"
        )

        assertEquals(BitcoinIndexerRoutes.Network.Testnet, result.network)
        assertEquals("00aa", result.txHex)
        assertEquals(TXID, result.txid)
        assertEquals("00aa", client.lastTxHex)
        assertEquals(BitcoinIndexerRoutes.Network.Testnet, client.lastNetwork)
        assertEquals("https://bitcoin.example/api", client.lastBaseUrl)
    }

    @Test
    fun `rejects invalid transaction hex before calling indexer`() {
        val client = FakeBitcoinIndexerClient(TXID)
        val broadcaster = BitcoinTransactionBroadcaster(client)

        assertBroadcastError(BitcoinBroadcastException.Code.INVALID_TX_HEX) {
            broadcaster.broadcast("00gg")
        }
        assertNull(client.lastTxHex)
    }

    @Test
    fun `rejects malformed txid returned by indexer`() {
        val broadcaster = BitcoinTransactionBroadcaster(FakeBitcoinIndexerClient("not-a-txid"))

        assertBroadcastError(BitcoinBroadcastException.Code.INVALID_TXID_RESPONSE) {
            broadcaster.broadcast("00aa")
        }
    }

    private fun assertBroadcastError(
        expected: BitcoinBroadcastException.Code,
        block: suspend () -> Unit
    ) {
        val error = assertThrows(BitcoinBroadcastException::class.java) {
            runBlocking {
                block()
            }
        }
        assertEquals(expected, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val broadcastResponse: String
    ) : BitcoinIndexerClient {
        var lastTxHex: String? = null
            private set
        var lastNetwork: BitcoinIndexerRoutes.Network? = null
            private set
        var lastBaseUrl: String? = null
            private set

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress = error("Not used")

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> = error("Not used")

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> = error("Not used")

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> = error("Not used")

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String {
            lastTxHex = txHex
            lastNetwork = network
            lastBaseUrl = baseUrl

            return broadcastResponse
        }
    }

    private companion object {
        val TXID = "11".repeat(32)
    }
}
