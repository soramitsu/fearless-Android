package jp.co.soramitsu.common.data.network.bitcoin

class BitcoinTransactionBroadcaster(
    private val client: BitcoinIndexerClient
) {
    suspend fun broadcast(
        txHex: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): BitcoinBroadcastResult {
        val normalizedTxHex = try {
            BitcoinIndexerRoutes.normalizeBroadcastTransactionBody(txHex)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinBroadcastException(BitcoinBroadcastException.Code.INVALID_TX_HEX)
        }
        val broadcastResponse = client.broadcastTransaction(normalizedTxHex, network, baseUrl)
        val txid = try {
            BitcoinIndexerRoutes.normalizeTxid(broadcastResponse)
        } catch (_: BitcoinIndexerRoutes.BitcoinIndexerRouteException) {
            throw BitcoinBroadcastException(BitcoinBroadcastException.Code.INVALID_TXID_RESPONSE)
        }

        return BitcoinBroadcastResult(
            network = network,
            txHex = normalizedTxHex,
            txid = txid
        )
    }
}

data class BitcoinBroadcastResult(
    val network: BitcoinIndexerRoutes.Network,
    val txHex: String,
    val txid: String
)

class BitcoinBroadcastException(
    val code: Code
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_TX_HEX,
        INVALID_TXID_RESPONSE
    }
}
