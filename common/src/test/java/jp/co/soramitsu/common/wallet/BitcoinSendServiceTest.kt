package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendRequest
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendService
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendServiceException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinSendServiceTest {

    @Test
    fun `prepares signed transaction from selected utxos without broadcasting`() = runBlocking {
        val client = FakeBitcoinIndexerClient()
        val prepared = BitcoinSendService(client).prepare(request())

        assertEquals(282L, prepared.plan.feeSats)
        assertEquals(49_718L, prepared.plan.changeSats)
        assertEquals(EXPECTED_TXID, prepared.transaction.txid)
        assertEquals(EXPECTED_TX_HEX, prepared.transaction.txHex)
        assertNull(client.lastBroadcastTxHex)
    }

    @Test
    fun `sends signed transaction and requires matching broadcast txid`() = runBlocking {
        val client = FakeBitcoinIndexerClient(broadcastResponse = EXPECTED_TXID.uppercase())
        val result = BitcoinSendService(client).send(request(baseUrl = "https://bitcoin.example/api"))

        assertEquals(EXPECTED_TXID, result.broadcastTxid)
        assertEquals(EXPECTED_TXID, result.prepared.transaction.txid)
        assertEquals(EXPECTED_TX_HEX, client.lastBroadcastTxHex)
        assertEquals(BitcoinIndexerRoutes.Network.Mainnet, client.lastBroadcastNetwork)
        assertEquals("https://bitcoin.example/api", client.lastBroadcastBaseUrl)
    }

    @Test
    fun `rejects broadcast txid mismatches`() {
        val client = FakeBitcoinIndexerClient(broadcastResponse = "22".repeat(32))

        val error = assertThrows(BitcoinSendServiceException::class.java) {
            runBlocking {
                BitcoinSendService(client).send(request())
            }
        }

        assertEquals(BitcoinSendServiceException.Code.BROADCAST_TXID_MISMATCH, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val broadcastResponse: String = EXPECTED_TXID
    ) : BitcoinIndexerClient {
        var lastBroadcastTxHex: String? = null
            private set
        var lastBroadcastNetwork: BitcoinIndexerRoutes.Network? = null
            private set
        var lastBroadcastBaseUrl: String? = null
            private set

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            return BitcoinEsploraAddress(
                address = address,
                chainStats = BitcoinEsploraStats(0, 0, 0, 0, 0),
                mempoolStats = BitcoinEsploraStats(0, 0, 0, 0, 0)
            )
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> {
            return listOf(
                BitcoinEsploraUtxo(
                    txid = TXID,
                    vout = 1,
                    value = 100_000,
                    status = BitcoinEsploraTxStatus(confirmed = true)
                )
            )
        }

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> = emptyList()

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> = emptyMap()

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String {
            lastBroadcastTxHex = txHex
            lastBroadcastNetwork = network
            lastBroadcastBaseUrl = baseUrl

            return broadcastResponse
        }
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val MAINNET_RECIPIENT = "bc1qslk39wvggqa0vl8nd6jckaz54dw3vk45c5w60m"
        val TXID = "11".repeat(32)
        const val EXPECTED_TXID = "94c9b9d5070f24e06725b1000d9b1a0d46473d07b59088aca35e3d3da345023d"
        const val EXPECTED_TX_HEX = "0200000000010111111111111111111111111111111111111111111111111111111111111111110100000000ffffffff0250c300000000000016001487ed12b988403af67cf36ea58b7454ab5d165ab436c2000000000000160014c0cebcd6c3d3ca8c75dc5ec62ebe55330ef910e202473044022009ec0c24a20c4346c6516065723e2e83e6a7e4dd278fb66e27f36d9108d7ef4c022077f115bfbd68a2bc7a4c100766d9cceaf5300bcfcdc775be0248e7fb5a58216801210330d54fd0dd420a6e5f8d3624f5f3482cae350f79d5f0753bf5beef9c2d91af3c00000000"

        fun request(baseUrl: String? = null): BitcoinSendRequest {
            return BitcoinSendRequest(
                mnemonic = MNEMONIC,
                amountSats = 50_000,
                sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
                recipientAddress = MAINNET_RECIPIENT,
                changeAddress = MAINNET_ADDRESS,
                feeRateSatPerVbyte = 2.0,
                baseUrl = baseUrl
            )
        }
    }
}
