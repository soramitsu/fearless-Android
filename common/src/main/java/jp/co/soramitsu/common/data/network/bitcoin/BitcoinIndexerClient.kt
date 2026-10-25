package jp.co.soramitsu.common.data.network.bitcoin

interface BitcoinIndexerClient {
    suspend fun address(
        address: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): BitcoinEsploraAddress

    suspend fun utxos(
        address: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): List<BitcoinEsploraUtxo>

    suspend fun transactions(
        address: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null,
        lastSeenTxid: String? = null,
        mempool: Boolean = false
    ): List<BitcoinEsploraTransaction>

    suspend fun feeEstimates(
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): Map<String, Double>

    /** Reads a known transaction; null is absence, never proof that an earlier payment failed. */
    suspend fun transactionStatus(txid: String, network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null): BitcoinEsploraTxStatus? = error("Bitcoin transaction status is unavailable")

    suspend fun broadcastTransaction(
        txHex: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): String
}
