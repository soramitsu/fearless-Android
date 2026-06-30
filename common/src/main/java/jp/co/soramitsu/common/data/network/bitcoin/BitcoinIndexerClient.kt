package jp.co.soramitsu.common.data.network.bitcoin

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

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

    suspend fun broadcastTransaction(
        txHex: String,
        network: BitcoinIndexerRoutes.Network = BitcoinIndexerRoutes.Network.Mainnet,
        baseUrl: String? = null
    ): String
}

class RetrofitBitcoinIndexerClient(
    private val api: BitcoinIndexerApi
) : BitcoinIndexerClient {

    override suspend fun address(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?
    ): BitcoinEsploraAddress {
        return api.getAddress(BitcoinIndexerRoutes.addressUrl(address, network, resolveBaseUrl(network, baseUrl)))
    }

    override suspend fun utxos(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?
    ): List<BitcoinEsploraUtxo> {
        return api.getUtxos(BitcoinIndexerRoutes.utxosUrl(address, network, resolveBaseUrl(network, baseUrl)))
    }

    override suspend fun transactions(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?,
        lastSeenTxid: String?,
        mempool: Boolean
    ): List<BitcoinEsploraTransaction> {
        return api.getTransactions(
            BitcoinIndexerRoutes.transactionsUrl(
                address = address,
                network = network,
                baseUrl = resolveBaseUrl(network, baseUrl),
                lastSeenTxid = lastSeenTxid,
                mempool = mempool
            )
        )
    }

    override suspend fun feeEstimates(
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?
    ): Map<String, Double> {
        return api.getFeeEstimates(BitcoinIndexerRoutes.feeEstimatesUrl(network, resolveBaseUrl(network, baseUrl)))
    }

    override suspend fun broadcastTransaction(
        txHex: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?
    ): String {
        val body = BitcoinIndexerRoutes.normalizeBroadcastTransactionBody(txHex)
            .encodeToByteArray()
            .toRequestBody(TEXT_PLAIN_MEDIA_TYPE)

        return api.broadcastTransaction(
            BitcoinIndexerRoutes.broadcastTransactionUrl(network, resolveBaseUrl(network, baseUrl)),
            body
        )
    }

    private fun resolveBaseUrl(network: BitcoinIndexerRoutes.Network, baseUrl: String?): String {
        return baseUrl ?: BitcoinIndexerRoutes.baseUrl(network)
    }

    private companion object {
        val TEXT_PLAIN_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
