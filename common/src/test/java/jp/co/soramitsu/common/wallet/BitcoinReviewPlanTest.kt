package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlanner
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlannerException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendRequest
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendService
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendServiceException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinUtxoSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito.mock

class BitcoinReviewPlanTest {
    @Test
    fun `confirmed plan pins inputs fee and change even if fee estimates and new coins change`() = runBlocking {
        val client = Client()
        val planner = BitcoinSendPlanner(client)
        val service = BitcoinSendService(client)
        val plan = planner.plan(50000, listOf(BitcoinUtxoSource(SENDER)), RECIPIENT, SENDER)
        client.fee = 1000.0
        client.coins = client.coins + coin("22", 200000)
        val prepared = service.prepareConfirmedPlan(request(), plan)
        assertEquals(1, client.feeQueries)
        assertEquals(1, prepared.plan.selectedUtxos.size)
        assertEquals(282L, prepared.transaction.feeSats)
        assertEquals(49718L, prepared.transaction.changeSats)
        client.broadcastHash = prepared.transaction.txid
        service.broadcastPrepared(prepared)
        service.broadcastPrepared(prepared)
        assertEquals(listOf(prepared.transaction.txHex, prepared.transaction.txHex), client.broadcasts)
        assertEquals(1, client.feeQueries)
    }

    @Test
    fun `disappeared changed or unconfirmed reviewed input aborts without signing a new plan`() = runBlocking {
        val client = Client()
        val plan = BitcoinSendPlanner(client).plan(50000, listOf(BitcoinUtxoSource(SENDER)), RECIPIENT, SENDER)
        for (coins in listOf(emptyList(), listOf(coin("11", 99999)), listOf(coin("11", 100000).copy(status = BitcoinEsploraTxStatus(false))))) {
            client.coins = coins
            val error = assertThrows(BitcoinSendPlannerException::class.java) {
                runBlocking { BitcoinSendService(client).prepareConfirmedPlan(request(), plan) }
            }
            assertEquals(BitcoinSendPlannerException.Code.REVIEWED_INPUT_CHANGED, error.code)
            assertEquals(emptyList<String>(), client.broadcasts)
        }
    }

    @Test
    fun `confirmed request amount network and recipient must still match the quote`() = runBlocking {
        val client = Client()
        val plan = BitcoinSendPlanner(client).plan(50000, listOf(BitcoinUtxoSource(SENDER)), RECIPIENT, SENDER)
        for (request in listOf(request().copy(amountSats = 50001), request().copy(recipientAddress = SENDER),
            request().copy(network = BitcoinIndexerRoutes.Network.Testnet))) {
            val error = assertThrows(BitcoinSendServiceException::class.java) {
                runBlocking { BitcoinSendService(client).prepareConfirmedPlan(request, plan) }
            }
            assertEquals(BitcoinSendServiceException.Code.PLANNED_TRANSACTION_MISMATCH, error.code)
        }
    }

    @Test
    fun `max plan selects every confirmed input and exactly one recipient output`() = runBlocking {
        val client = Client().also { it.coins += coin("22", 50000) }
        val plan = BitcoinSendPlanner(client).plan(1, listOf(BitcoinUtxoSource(SENDER)), RECIPIENT, SENDER, sendMax = true)
        assertEquals(2, plan.selectedUtxos.size)
        assertEquals(0L, plan.changeSats)
        assertEquals(null, plan.changeAddress)
        assertEquals(150000L, plan.amountSats + plan.feeSats)
        val prepared = BitcoinSendService(client).prepareConfirmedPlan(request().copy(amountSats = plan.amountSats), plan)
        assertEquals(0L, prepared.transaction.changeSats)
    }

    private class Client : BitcoinIndexerClient by mock(BitcoinIndexerClient::class.java) {
        var fee = 2.0
        var feeQueries = 0
        var coins = listOf(coin("11", 100000))
        val broadcasts = mutableListOf<String>()
        var broadcastHash = ""
        override suspend fun feeEstimates(network: BitcoinIndexerRoutes.Network, baseUrl: String?): Map<String, Double> {
            feeQueries++
            return mapOf("2" to fee)
        }
        override suspend fun utxos(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?) = coins
        override suspend fun broadcastTransaction(txHex: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): String {
            broadcasts += txHex
            return broadcastHash
        }
    }

    private fun request() = BitcoinSendRequest(MNEMONIC, 50000, listOf(BitcoinUtxoSource(SENDER)), RECIPIENT, changeAddress = SENDER)

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val SENDER = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val RECIPIENT = "bc1qslk39wvggqa0vl8nd6jckaz54dw3vk45c5w60m"
        fun coin(prefix: String, value: Long) = BitcoinEsploraUtxo(prefix.repeat(32), 1, value, BitcoinEsploraTxStatus(true))
    }
}
