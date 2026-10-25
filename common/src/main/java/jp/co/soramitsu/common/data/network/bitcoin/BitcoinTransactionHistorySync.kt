package jp.co.soramitsu.common.data.network.bitcoin

class BitcoinTransactionHistorySync(
    private val client: BitcoinIndexerClient
) {
    suspend fun historyWindow(
        address: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): BitcoinTransactionHistoryPage {
        val normalizedAddress = normalizeHistoryAddress(address, network)
        val entries = mutableListOf<BitcoinTransactionHistoryEntry>()
        val seenTxids = mutableSetOf<String>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var nextLastSeenTxid: String? = null
        var pagesFetched = 0

        while (pagesFetched < BITCOIN_HISTORY_MAX_PAGES) {
            val currentCursor = cursor
            if (currentCursor != null && !seenCursors.add(currentCursor)) {
                break
            }

            val page = fetchHistoryPage(
                address = normalizedAddress,
                network = network,
                baseUrl = baseUrl,
                lastSeenTxid = currentCursor,
                mempool = false
            )
            pagesFetched += 1

            var newEntries = 0
            page.history.entries.forEach { entry ->
                if (seenTxids.add(entry.txid)) {
                    entries.add(entry)
                    newEntries += 1
                }
            }

            nextLastSeenTxid = page.history.nextLastSeenTxid

            if (
                page.transactionCount < BITCOIN_ESPLORA_HISTORY_PAGE_SIZE ||
                nextLastSeenTxid == null ||
                nextLastSeenTxid == currentCursor ||
                newEntries == 0
            ) {
                break
            }

            cursor = nextLastSeenTxid
        }

        return BitcoinTransactionHistoryPage(
            address = normalizedAddress,
            network = network,
            entries = entries,
            nextLastSeenTxid = nextLastSeenTxid
        )
    }

    suspend fun history(
        address: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null,
        lastSeenTxid: String? = null,
        mempool: Boolean = false
    ): BitcoinTransactionHistoryPage {
        val normalizedAddress = normalizeHistoryAddress(address, network)
        return fetchHistoryPage(
            address = normalizedAddress,
            network = network,
            baseUrl = baseUrl,
            lastSeenTxid = lastSeenTxid,
            mempool = mempool
        ).history
    }

    private suspend fun fetchHistoryPage(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?,
        lastSeenTxid: String?,
        mempool: Boolean
    ): BitcoinFetchedHistoryPage {
        val transactions = client.transactions(
            address = address,
            network = network,
            baseUrl = baseUrl,
            lastSeenTxid = lastSeenTxid,
            mempool = mempool
        )
        val entries = transactions.mapNotNull { normalizeTransaction(it, address) }
        val nextLastSeenTxid = transactions.asReversed().firstNotNullOfOrNull { transaction ->
            try {
                BitcoinIndexerRoutes.normalizeTxid(transaction.txid)
            } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
                null
            }
        }

        return BitcoinFetchedHistoryPage(
            history = BitcoinTransactionHistoryPage(
                address = address,
                network = network,
                entries = entries,
                nextLastSeenTxid = nextLastSeenTxid
            ),
            transactionCount = transactions.size
        )
    }

    private fun normalizeHistoryAddress(
        address: String,
        network: BitcoinIndexerRoutes.Network
    ): String {
        return try {
            BitcoinIndexerRoutes.normalizeAddress(address, network)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinTransactionHistoryException(BitcoinTransactionHistoryException.Code.INVALID_ADDRESS)
        }
    }

    private fun normalizeTransaction(
        transaction: BitcoinEsploraTransaction,
        address: String
    ): BitcoinTransactionHistoryEntry? {
        val txid = try {
            BitcoinIndexerRoutes.normalizeTxid(transaction.txid)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            return null
        }
        val walletAddress = address.lowercase()
        val inputs = transaction.vin.orEmpty().mapNotNull { it.prevout?.parsedOutput() }
        val outputs = transaction.vout.orEmpty().mapNotNull { it.parsedOutput() }
        val sent = sumValues(inputs) { it.address.lowercase() == walletAddress } ?: return null
        val received = sumValues(outputs) { it.address.lowercase() == walletAddress } ?: return null

        if (sent == 0L && received == 0L) {
            return null
        }

        val isOutgoing = sent > 0
        val fee = transaction.fee ?: 0
        if (fee < 0) {
            return null
        }
        val amount = if (isOutgoing) {
            sent - received - fee
        } else {
            received
        }
        if (amount <= 0 || amount > BitcoinUtxoSelector.MAX_SATOSHI) {
            return null
        }
        val counterparty = if (isOutgoing) {
            outputs.firstOrNull { it.address.lowercase() != walletAddress && it.valueSats > 0 }?.address
        } else {
            inputs.firstOrNull { it.address.lowercase() != walletAddress && it.valueSats > 0 }?.address
        } ?: return null

        return BitcoinTransactionHistoryEntry(
            address = address,
            amountSats = amount,
            blockHash = transaction.status.blockHash ?: txid,
            blockHeight = transaction.status.blockHeight,
            confirmed = transaction.status.confirmed,
            feeSats = if (isOutgoing) fee else 0,
            from = if (isOutgoing) address else counterparty,
            outgoing = isOutgoing,
            timestamp = transaction.status.blockTime ?: 0,
            to = if (isOutgoing) counterparty else address,
            txid = txid
        )
    }

    private fun BitcoinEsploraTransactionOutput.parsedOutput(): BitcoinParsedTransactionOutput? {
        val address = scriptPubKeyAddress?.takeIf { it.isNotBlank() } ?: return null
        val value = valueSatsOrNull() ?: return null
        if (value < 0 || value > BitcoinUtxoSelector.MAX_SATOSHI) {
            return null
        }

        return BitcoinParsedTransactionOutput(address, value)
    }

    private fun sumValues(
        outputs: List<BitcoinParsedTransactionOutput>,
        predicate: (BitcoinParsedTransactionOutput) -> Boolean
    ): Long? {
        return outputs.fold(0L) { total, output ->
            if (!predicate(output)) {
                return@fold total
            }
            val sum = total + output.valueSats
            if (sum < total || sum > BitcoinUtxoSelector.MAX_SATOSHI) {
                return null
            }

            sum
        }
    }

    private companion object {
        const val BITCOIN_ESPLORA_HISTORY_PAGE_SIZE = 25
        const val BITCOIN_HISTORY_MAX_PAGES = 12
    }
}

private data class BitcoinFetchedHistoryPage(
    val history: BitcoinTransactionHistoryPage,
    val transactionCount: Int
)

data class BitcoinTransactionHistoryPage(
    val address: String,
    val network: BitcoinIndexerRoutes.Network,
    val entries: List<BitcoinTransactionHistoryEntry>,
    val nextLastSeenTxid: String?
)

data class BitcoinTransactionHistoryEntry(
    val address: String,
    val amountSats: Long,
    val blockHash: String,
    val blockHeight: Long?,
    val confirmed: Boolean,
    val feeSats: Long,
    val from: String,
    val outgoing: Boolean,
    val timestamp: Long,
    val to: String,
    val txid: String
)

class BitcoinTransactionHistoryException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_ADDRESS
    }
}

private data class BitcoinParsedTransactionOutput(
    val address: String,
    val valueSats: Long
)
