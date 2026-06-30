package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinFeeEstimator
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinFeeEstimatorException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinFeeEstimatorTest {

    @Test
    fun `selects exact next slower or slowest available fee estimate`() {
        val estimator = BitcoinFeeEstimator(FakeBitcoinIndexerClient())

        assertEquals(4.0, estimator.select(mapOf("1" to 9.0, "2" to 4.0, "6" to 2.0), 2).feeRateSatPerVbyte, 0.0)
        assertEquals(2.0, estimator.select(mapOf("1" to 9.0, "6" to 2.0), 2).feeRateSatPerVbyte, 0.0)
        assertEquals(2.0, estimator.select(mapOf("1" to 9.0, "6" to 2.0), 10).feeRateSatPerVbyte, 0.0)
    }

    @Test
    fun `rejects unsafe fee estimates and targets`() {
        val estimator = BitcoinFeeEstimator(FakeBitcoinIndexerClient())

        assertFeeError(BitcoinFeeEstimatorException.Code.FEE_ESTIMATES_UNAVAILABLE) {
            estimator.select(emptyMap(), 2)
        }
        assertFeeError(BitcoinFeeEstimatorException.Code.INVALID_FEE_TARGET) {
            estimator.select(mapOf("2" to 1.0), 0)
        }
        assertFeeError(BitcoinFeeEstimatorException.Code.INVALID_FEE_RATE) {
            estimator.select(mapOf("2" to 10_001.0), 2)
        }
    }

    @Test
    fun `fetches estimates through selected bitcoin indexer network`() = runBlocking {
        val client = FakeBitcoinIndexerClient(estimates = mapOf("3" to 5.0))
        val estimator = BitcoinFeeEstimator(client)

        val result = estimator.estimate(
            network = BitcoinIndexerRoutes.Network.Testnet,
            baseUrl = "https://bitcoin.example/api",
            targetBlocks = 2
        )

        assertEquals(5.0, result.feeRateSatPerVbyte, 0.0)
        assertEquals(2, result.requestedTargetBlocks)
        assertEquals(3, result.selectedTargetBlocks)
        assertEquals(BitcoinIndexerRoutes.Network.Testnet, client.lastNetwork)
        assertEquals("https://bitcoin.example/api", client.lastBaseUrl)
    }

    private fun assertFeeError(
        expected: BitcoinFeeEstimatorException.Code,
        block: () -> Unit
    ) {
        val error = assertThrows(BitcoinFeeEstimatorException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val estimates: Map<String, Double> = emptyMap()
    ) : BitcoinIndexerClient {
        var lastNetwork: BitcoinIndexerRoutes.Network? = null
            private set
        var lastBaseUrl: String? = null
            private set

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> {
            lastNetwork = network
            lastBaseUrl = baseUrl
            return estimates
        }

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            error("Unexpected Bitcoin address call")
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> {
            error("Unexpected Bitcoin UTXO call")
        }

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> {
            error("Unexpected Bitcoin transaction call")
        }

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String {
            error("Unexpected Bitcoin broadcast call")
        }
    }
}
