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
    fun `status is exact txid and custom endpoint and invalid confirmed proof is rejected`() = runBlocking {
        val api = FakeBitcoinIndexerApi()
        val client = RetrofitBitcoinIndexerClient(api)
        assertEquals(true, client.transactionStatus(TXID, baseUrl = "http://localhost:18300")?.confirmed)
        assertEquals("http://localhost:18300/tx/$TXID/status", api.lastUrl)
        val malformed = object : BitcoinIndexerApi by api {
            override suspend fun getTransactionStatus(url: String) = BitcoinEsploraTxStatus(true)
        }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { RetrofitBitcoinIndexerClient(malformed).transactionStatus(TXID) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client.transactionStatus("../other") } }
        Unit
    }


    @Test
    fun `default providers fail over once then stay on the healthy endpoint`() = runBlocking {
        val calls = mutableListOf<String>()
        val api = object : BitcoinIndexerApi by FakeBitcoinIndexerApi() {
            override suspend fun getFeeEstimates(url: String): Map<String, Double> {
                calls += url
                if (url.contains("blockstream")) throw java.io.IOException("unavailable")
                return mapOf("1" to 2.0)
            }
        }
        val client = RetrofitBitcoinIndexerClient(api)
        assertEquals(2.0, client.feeEstimates()["1"])
        client.feeEstimates()
        assertEquals(listOf("https://blockstream.info/api/fee-estimates", "https://mempool.space/api/fee-estimates",
            "https://mempool.space/api/fee-estimates"), calls)
    }

    @Test
    fun `ambiguous broadcast fallback preserves bytes and private validator never reaches a public endpoint`() = runBlocking {
        val payloads = mutableListOf<String>()
        val calls = mutableListOf<String>()
        val api = object : BitcoinIndexerApi by FakeBitcoinIndexerApi() {
            override suspend fun broadcastTransaction(url: String, body: RequestBody): String {
                calls += url
                val buffer = Buffer()
                body.writeTo(buffer)
                payloads += buffer.readUtf8()
                if (calls.size == 1) throw java.io.IOException("response lost")
                return TXID
            }
        }
        assertEquals(TXID, RetrofitBitcoinIndexerClient(api).broadcastTransaction("00aa"))
        assertEquals(listOf("00aa", "00aa"), payloads)
        calls.clear()
        val localApi = object : BitcoinIndexerApi by FakeBitcoinIndexerApi() {
            override suspend fun getFeeEstimates(url: String): Map<String, Double> {
                calls += url
                throw java.io.IOException("local stopped")
            }
        }
        try { RetrofitBitcoinIndexerClient(localApi).feeEstimates(baseUrl = "http://127.0.0.1:4321") }
        catch (_: java.io.IOException) { }
        assertEquals(listOf("http://127.0.0.1:4321/fee-estimates"), calls)
    }

    @Test
    fun `explicit emulator custom and wrong network endpoints never fall back to public chains`() = runBlocking {
        for (endpoint in listOf("https://10.0.2.2:4321", "https://[::1]:4321", "https://regtest.example/api",
            "https://192.168.0.9:4321", "https://blockstream.info/api")) {
            val calls = mutableListOf<String>()
            val api = object : BitcoinIndexerApi by FakeBitcoinIndexerApi() {
                override suspend fun getFeeEstimates(url: String): Map<String, Double> {
                    calls += url
                    throw java.io.IOException("isolated provider stopped")
                }
            }
            assertThrows(java.io.IOException::class.java) {
                runBlocking { RetrofitBitcoinIndexerClient(api).feeEstimates(BitcoinIndexerRoutes.Network.Testnet, endpoint) }
            }
            assertEquals(listOf("$endpoint/fee-estimates"), calls)
        }
    }

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

        override suspend fun getTransactionStatus(url: String): BitcoinEsploraTxStatus {
            lastUrl = url
            return BitcoinEsploraTxStatus(true, TXID, 1, 1234)
        }

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
