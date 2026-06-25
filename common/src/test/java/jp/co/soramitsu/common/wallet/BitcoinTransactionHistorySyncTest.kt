package jp.co.soramitsu.common.wallet

import com.google.gson.JsonPrimitive
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraAddress
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraStats
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransaction
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransactionInput
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTransactionOutput
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraTxStatus
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinEsploraUtxo
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistoryException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BitcoinTransactionHistorySyncTest {

    @Test
    fun `normalizes outgoing and incoming transactions from Esplora`() = runBlocking {
        val client = FakeBitcoinIndexerClient(
            transactions = listOf(
                transaction(
                    txid = TXID_1,
                    fee = 141,
                    status = BitcoinEsploraTxStatus(
                        blockHash = BLOCK_HASH,
                        blockHeight = 100,
                        blockTime = 1_710_000_000,
                        confirmed = true
                    ),
                    vin = listOf(input(MAINNET_ADDRESS, 100_000)),
                    vout = listOf(output(COUNTERPARTY, 60_000), output(MAINNET_ADDRESS, 39_859))
                ),
                transaction(
                    txid = TXID_2,
                    fee = 200,
                    status = BitcoinEsploraTxStatus(confirmed = false),
                    vin = listOf(input(COUNTERPARTY, 75_200)),
                    vout = listOf(output(MAINNET_ADDRESS, 75_000))
                ),
                transaction(
                    txid = TXID_3,
                    vin = listOf(input(COUNTERPARTY, 10_000)),
                    vout = listOf(output(COUNTERPARTY, 9_900))
                )
            )
        )
        val history = BitcoinTransactionHistorySync(client).history(
            address = MAINNET_ADDRESS,
            baseUrl = "https://bitcoin.example/api",
            lastSeenTxid = TXID_0.uppercase()
        )

        assertEquals(MAINNET_ADDRESS, history.address)
        assertEquals(BitcoinIndexerRoutes.Network.Mainnet, history.network)
        assertEquals(TXID_3, history.nextLastSeenTxid)
        assertEquals(MAINNET_ADDRESS, client.lastAddress)
        assertEquals("https://bitcoin.example/api", client.lastBaseUrl)
        assertEquals(TXID_0.uppercase(), client.lastSeenTxid)
        assertEquals(2, history.entries.size)

        val outgoing = history.entries[0]
        assertEquals(TXID_1, outgoing.txid)
        assertEquals(BLOCK_HASH, outgoing.blockHash)
        assertEquals(100L, outgoing.blockHeight)
        assertEquals(1_710_000_000L, outgoing.timestamp)
        assertEquals(60_000L, outgoing.amountSats)
        assertEquals(141L, outgoing.feeSats)
        assertEquals(MAINNET_ADDRESS, outgoing.from)
        assertEquals(COUNTERPARTY, outgoing.to)
        assertEquals(true, outgoing.outgoing)
        assertEquals(true, outgoing.confirmed)

        val incoming = history.entries[1]
        assertEquals(TXID_2, incoming.txid)
        assertEquals(TXID_2, incoming.blockHash)
        assertEquals(75_000L, incoming.amountSats)
        assertEquals(0L, incoming.feeSats)
        assertEquals(COUNTERPARTY, incoming.from)
        assertEquals(MAINNET_ADDRESS, incoming.to)
        assertEquals(false, incoming.outgoing)
        assertEquals(false, incoming.confirmed)
    }

    @Test
    fun `filters malformed no movement and non positive amount transactions`() = runBlocking {
        val client = FakeBitcoinIndexerClient(
            transactions = listOf(
                transaction(
                    txid = TXID_1,
                    vin = listOf(input(MAINNET_ADDRESS, JsonPrimitive("1000"))),
                    vout = listOf(output(MAINNET_ADDRESS, 900))
                ),
                transaction(
                    txid = TXID_2,
                    fee = 141,
                    vin = listOf(input(MAINNET_ADDRESS, 1_000)),
                    vout = listOf(output(MAINNET_ADDRESS, 859))
                ),
                transaction(
                    txid = "not-a-txid",
                    vin = listOf(input(COUNTERPARTY, 2_000)),
                    vout = listOf(output(MAINNET_ADDRESS, 1_000))
                )
            )
        )

        val history = BitcoinTransactionHistorySync(client).history(MAINNET_ADDRESS)

        assertEquals(0, history.entries.size)
        assertEquals(TXID_2, history.nextLastSeenTxid)
    }

    @Test
    fun `rejects wrong network address before fetching history`() {
        val client = FakeBitcoinIndexerClient(emptyList())

        val error = assertThrows(BitcoinTransactionHistoryException::class.java) {
            runBlocking {
                BitcoinTransactionHistorySync(client).history(TESTNET_ADDRESS)
            }
        }

        assertEquals(BitcoinTransactionHistoryException.Code.INVALID_ADDRESS, error.code)
        assertEquals(0, client.calls)

        val windowClient = FakeBitcoinIndexerClient()
        val windowError = assertThrows(BitcoinTransactionHistoryException::class.java) {
            runBlocking {
                BitcoinTransactionHistorySync(windowClient).historyWindow(TESTNET_ADDRESS)
            }
        }

        assertEquals(BitcoinTransactionHistoryException.Code.INVALID_ADDRESS, windowError.code)
        assertEquals(0, windowClient.calls)
    }

    @Test
    fun `history window follows esplora chain pages until a short page`() = runBlocking {
        val transactions = (1..27).map { index -> outgoingTransaction(txidAt(index)) }
        val pageOneCursor = transactions[24].txid
        val client = FakeBitcoinIndexerClient(
            transactionProvider = { cursor ->
                when (cursor) {
                    null -> transactions.take(25)
                    pageOneCursor -> transactions.drop(25)
                    else -> emptyList()
                }
            }
        )

        val history = BitcoinTransactionHistorySync(client).historyWindow(
            address = MAINNET_ADDRESS,
            baseUrl = "https://bitcoin.example/api"
        )

        assertEquals(2, client.calls)
        assertEquals(listOf(null, pageOneCursor), client.lastSeenTxids)
        assertEquals(27, history.entries.size)
        assertEquals(transactions.last().txid, history.nextLastSeenTxid)
    }

    @Test
    fun `history window stops when a page repeats the same cursor and entries`() = runBlocking {
        val transactions = (1..25).map { index -> outgoingTransaction(txidAt(index)) }
        val repeatedCursor = transactions.last().txid
        val client = FakeBitcoinIndexerClient(
            transactionProvider = { cursor ->
                when (cursor) {
                    null -> transactions
                    repeatedCursor -> transactions
                    else -> emptyList()
                }
            }
        )

        val history = BitcoinTransactionHistorySync(client).historyWindow(MAINNET_ADDRESS)

        assertEquals(2, client.calls)
        assertEquals(listOf(null, repeatedCursor), client.lastSeenTxids)
        assertEquals(25, history.entries.size)
        assertEquals(repeatedCursor, history.nextLastSeenTxid)
    }

    @Test
    fun `history window caps pagination before unbounded esplora traversal`() = runBlocking {
        val transactions = (1..325).map { index -> outgoingTransaction(txidAt(index)) }
        val client = FakeBitcoinIndexerClient(
            transactionProvider = { cursor ->
                val startIndex = cursor?.let { previous ->
                    transactions.indexOfFirst { it.txid == previous } + 1
                } ?: 0

                transactions.drop(startIndex).take(25)
            }
        )

        val history = BitcoinTransactionHistorySync(client).historyWindow(MAINNET_ADDRESS)

        assertEquals(12, client.calls)
        assertEquals(300, history.entries.size)
        assertEquals(transactions[299].txid, history.nextLastSeenTxid)
    }

    private class FakeBitcoinIndexerClient(
        private val transactions: List<BitcoinEsploraTransaction> = emptyList(),
        private val transactionProvider: ((String?) -> List<BitcoinEsploraTransaction>)? = null
    ) : BitcoinIndexerClient {
        var calls = 0
            private set
        var lastAddress: String? = null
            private set
        var lastBaseUrl: String? = null
            private set
        var lastSeenTxid: String? = null
            private set
        val lastSeenTxids = mutableListOf<String?>()

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
        ): List<BitcoinEsploraUtxo> = emptyList()

        override suspend fun transactions(
            address: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?,
            lastSeenTxid: String?,
            mempool: Boolean
        ): List<BitcoinEsploraTransaction> {
            calls += 1
            lastAddress = address
            lastBaseUrl = baseUrl
            this.lastSeenTxid = lastSeenTxid
            lastSeenTxids.add(lastSeenTxid)

            return transactionProvider?.invoke(lastSeenTxid) ?: transactions
        }

        override suspend fun feeEstimates(
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): Map<String, Double> = emptyMap()

        override suspend fun broadcastTransaction(
            txHex: String,
            network: BitcoinIndexerRoutes.Network,
            baseUrl: String?
        ): String = TXID_1
    }

    private companion object {
        const val MAINNET_ADDRESS = "bc1qcr8te4kr609gcawutmrza0j4xv80jy8z306fyu"
        const val TESTNET_ADDRESS = "tb1q6rz28mcfaxtmd6v789l9rrlrusdprr9pqcpvkl"
        const val COUNTERPARTY = "bc1q6rz28mcfaxtmd6v789l9rrlrusdprr9pkv76kj"
        val BLOCK_HASH = "aa".repeat(32)
        val TXID_0 = "00".repeat(32)
        val TXID_1 = "11".repeat(32)
        val TXID_2 = "22".repeat(32)
        val TXID_3 = "33".repeat(32)

        fun txidAt(index: Int): String = index.toString(16).padStart(64, '0')

        fun outgoingTransaction(txid: String): BitcoinEsploraTransaction {
            return transaction(
                txid = txid,
                fee = 100,
                vin = listOf(input(MAINNET_ADDRESS, 1_000)),
                vout = listOf(output(COUNTERPARTY, 800), output(MAINNET_ADDRESS, 100))
            )
        }

        fun transaction(
            txid: String,
            fee: Long? = null,
            status: BitcoinEsploraTxStatus = BitcoinEsploraTxStatus(confirmed = true),
            vin: List<BitcoinEsploraTransactionInput>,
            vout: List<BitcoinEsploraTransactionOutput>
        ): BitcoinEsploraTransaction {
            return BitcoinEsploraTransaction(
                txid = txid,
                status = status,
                fee = fee,
                vin = vin,
                vout = vout
            )
        }

        fun input(address: String, value: Long): BitcoinEsploraTransactionInput = input(address, JsonPrimitive(value))

        fun input(address: String, value: JsonPrimitive): BitcoinEsploraTransactionInput {
            return BitcoinEsploraTransactionInput(output(address, value))
        }

        fun output(address: String, value: Long): BitcoinEsploraTransactionOutput = output(address, JsonPrimitive(value))

        fun output(address: String, value: JsonPrimitive): BitcoinEsploraTransactionOutput {
            return BitcoinEsploraTransactionOutput(
                scriptPubKeyAddress = address,
                value = value
            )
        }
    }
}
