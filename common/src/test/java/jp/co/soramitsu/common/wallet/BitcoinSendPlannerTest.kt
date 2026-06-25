package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlanner
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlannerException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSelectionException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinSendPlannerTest {

    @Test
    fun `plans unsigned spend with estimated fee and confirmed utxos`() = runBlocking {
        val client = FakeBitcoinIndexerClient(
            estimates = mapOf("2" to 2.0),
            utxosByAddress = mapOf(MAINNET_ADDRESS to listOf(utxo(valueSats = 100_000)))
        )
        val planner = BitcoinSendPlanner(client)

        val plan = planner.plan(
            amountSats = 50_000,
            sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS, "m/84'/0'/0'/0/0")),
            recipientAddress = RECIPIENT_ADDRESS
        )

        assertEquals(50_000L, plan.amountSats)
        assertEquals(RECIPIENT_ADDRESS, plan.recipientAddress)
        assertEquals(MAINNET_ADDRESS, plan.changeAddress)
        assertEquals(2.0, plan.feeRateSatPerVbyte, 0.0)
        assertEquals(2, plan.feeTargetBlocks)
        assertEquals(listOf(MAINNET_ADDRESS), plan.sourceAddresses)
        assertEquals(1, plan.selectedUtxos.size)
        assertEquals(282L, plan.feeSats)
        assertEquals(49_718L, plan.changeSats)
        assertEquals(0L, plan.absorbedDustSats)
        assertEquals(listOf(BitcoinIndexerRoutes.Network.Mainnet), client.feeEstimateNetworks)
        assertEquals(listOf(MAINNET_ADDRESS), client.utxoAddressCalls)
    }

    @Test
    fun `uses manual fee rate without fetching fee estimates`() = runBlocking {
        val client = FakeBitcoinIndexerClient(
            utxosByAddress = mapOf(MAINNET_ADDRESS to listOf(utxo(valueSats = 50_110)))
        )
        val planner = BitcoinSendPlanner(client)

        val plan = planner.plan(
            amountSats = 50_000,
            sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
            recipientAddress = RECIPIENT_ADDRESS,
            feeRateSatPerVbyte = 1.0
        )

        assertEquals(1.0, plan.feeRateSatPerVbyte, 0.0)
        assertNull(plan.feeTargetBlocks)
        assertEquals(110L, plan.feeSats)
        assertEquals(emptyList<BitcoinIndexerRoutes.Network>(), client.feeEstimateNetworks)
    }

    @Test
    fun `requires explicit opt in for unconfirmed utxos`() = runBlocking {
        val client = FakeBitcoinIndexerClient(
            utxosByAddress = mapOf(MAINNET_ADDRESS to listOf(utxo(valueSats = 100_000, confirmed = false)))
        )
        val planner = BitcoinSendPlanner(client)

        assertPlannerError(BitcoinSendPlannerException.Code.NO_SPENDABLE_UTXOS) {
            runBlocking {
                planner.plan(
                    amountSats = 50_000,
                    sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
                    recipientAddress = RECIPIENT_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }

        val plan = planner.plan(
            amountSats = 50_000,
            sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
            recipientAddress = RECIPIENT_ADDRESS,
            feeRateSatPerVbyte = 1.0,
            includeUnconfirmed = true
        )

        assertEquals(true, plan.includeUnconfirmed)
        assertEquals(1, plan.selectedUtxos.size)
    }

    @Test
    fun `rejects invalid planner inputs before network calls`() {
        val client = FakeBitcoinIndexerClient()
        val planner = BitcoinSendPlanner(client)

        assertPlannerError(BitcoinSendPlannerException.Code.SOURCES_REQUIRED) {
            runBlocking {
                planner.plan(1_000, emptyList(), RECIPIENT_ADDRESS, feeRateSatPerVbyte = 1.0)
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.TOO_MANY_SOURCES) {
            runBlocking {
                planner.plan(
                    amountSats = 1_000,
                    sources = (0..100).map { BitcoinUtxoSource("bc1q${it.toString().padStart(3, '0')}aaaaaaaaaaaaaa") },
                    recipientAddress = RECIPIENT_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.INVALID_SOURCE_ADDRESS) {
            runBlocking {
                planner.plan(1_000, listOf(BitcoinUtxoSource(TESTNET_ADDRESS)), RECIPIENT_ADDRESS, feeRateSatPerVbyte = 1.0)
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.DUPLICATE_SOURCE_ADDRESS) {
            runBlocking {
                planner.plan(
                    1_000,
                    listOf(BitcoinUtxoSource(MAINNET_ADDRESS), BitcoinUtxoSource(MAINNET_ADDRESS.uppercase())),
                    RECIPIENT_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.INVALID_DERIVATION_PATH) {
            runBlocking {
                planner.plan(
                    1_000,
                    listOf(BitcoinUtxoSource(MAINNET_ADDRESS, "../bad")),
                    RECIPIENT_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.INVALID_RECIPIENT_ADDRESS) {
            runBlocking {
                planner.plan(1_000, listOf(BitcoinUtxoSource(MAINNET_ADDRESS)), TESTNET_ADDRESS, feeRateSatPerVbyte = 1.0)
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.INVALID_CHANGE_ADDRESS) {
            runBlocking {
                planner.plan(
                    1_000,
                    listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
                    RECIPIENT_ADDRESS,
                    changeAddress = TESTNET_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }
        assertPlannerError(BitcoinSendPlannerException.Code.INVALID_FEE_RATE) {
            runBlocking {
                planner.plan(1_000, listOf(BitcoinUtxoSource(MAINNET_ADDRESS)), RECIPIENT_ADDRESS, feeRateSatPerVbyte = 0.0)
            }
        }
        assertEquals(emptyList<String>(), client.utxoAddressCalls)
        assertEquals(emptyList<BitcoinIndexerRoutes.Network>(), client.feeEstimateNetworks)
    }

    @Test
    fun `propagates selection failures from spend planning`() {
        val client = FakeBitcoinIndexerClient(
            utxosByAddress = mapOf(MAINNET_ADDRESS to listOf(utxo(valueSats = 30_000)))
        )
        val planner = BitcoinSendPlanner(client)

        val error = assertThrows(BitcoinUtxoSelectionException::class.java) {
            runBlocking {
                planner.plan(
                    amountSats = 50_000,
                    sources = listOf(BitcoinUtxoSource(MAINNET_ADDRESS)),
                    recipientAddress = RECIPIENT_ADDRESS,
                    feeRateSatPerVbyte = 1.0
                )
            }
        }

        assertEquals(BitcoinUtxoSelectionException.Code.INSUFFICIENT_FUNDS, error.code)
    }

    private fun assertPlannerError(
        expected: BitcoinSendPlannerException.Code,
        block: () -> Unit
    ) {
        val error = assertThrows(BitcoinSendPlannerException::class.java) { block() }
        assertEquals(expected, error.code)
    }

    private class FakeBitcoinIndexerClient(
        private val estimates: Map<String, Double> = emptyMap(),
        private val utxosByAddress: Map<String, List<BitcoinEsploraUtxo>> = emptyMap()
    ) : BitcoinIndexerClient {
        val feeEstimateNetworks = mutableListOf<BitcoinIndexerRoutes.Network>()
        val utxoAddressCalls = mutableListOf<String>()

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> {
            feeEstimateNetworks += network
            return estimates
        }

        override suspend fun utxos(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): List<BitcoinEsploraUtxo> {
            utxoAddressCalls += address
            return utxosByAddress[address].orEmpty()
        }

        override suspend fun address(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): BitcoinEsploraAddress {
            error("Unexpected Bitcoin address call")
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

    private companion object {
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val RECIPIENT_ADDRESS = "bc1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"

        fun utxo(
            valueSats: Long,
            confirmed: Boolean = true,
            txid: String = "11".repeat(32)
        ): BitcoinEsploraUtxo {
            return BitcoinEsploraUtxo(
                txid = txid,
                vout = 0,
                value = valueSats,
                status = BitcoinEsploraTxStatus(confirmed = confirmed)
            )
        }
    }
}
