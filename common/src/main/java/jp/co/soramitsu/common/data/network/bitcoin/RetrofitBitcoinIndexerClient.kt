package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.data.network.WalletProviderPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Esplora client with network-scoped sticky failover and bounded rate-limit cooldowns. */
class RetrofitBitcoinIndexerClient(
    private val api: BitcoinIndexerApi,
    private val providers: WalletProviderPool = WalletProviderPool()
) : BitcoinIndexerClient {
    override suspend fun address(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): BitcoinEsploraAddress =
        providers.execute(endpoints(network, baseUrl)) { api.getAddress(BitcoinIndexerRoutes.addressUrl(address, network, it)) }

    override suspend fun utxos(address: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): List<BitcoinEsploraUtxo> =
        providers.execute(endpoints(network, baseUrl)) { api.getUtxos(BitcoinIndexerRoutes.utxosUrl(address, network, it)) }

    override suspend fun transactions(
        address: String,
        network: BitcoinIndexerRoutes.Network,
        baseUrl: String?,
        lastSeenTxid: String?,
        mempool: Boolean
    ): List<BitcoinEsploraTransaction> = providers.execute(endpoints(network, baseUrl)) {
        api.getTransactions(BitcoinIndexerRoutes.transactionsUrl(address, network, it, lastSeenTxid, mempool))
    }

    override suspend fun feeEstimates(network: BitcoinIndexerRoutes.Network, baseUrl: String?): Map<String, Double> =
        providers.execute(endpoints(network, baseUrl)) { api.getFeeEstimates(BitcoinIndexerRoutes.feeEstimatesUrl(network, it)) }

    override suspend fun transactionStatus(txid: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): BitcoinEsploraTxStatus? {
        require(Regex("^[0-9a-f]{64}$").matches(txid))
        return providers.execute(endpoints(network, baseUrl)) { endpoint ->
            try {
                api.getTransactionStatus("$endpoint/tx/$txid/status").also { status ->
                    require(!status.confirmed || (status.blockHeight != null && status.blockHeight >= 0 &&
                        status.blockHash?.matches(Regex("^[0-9a-f]{64}$")) == true))
                }
            } catch (error: retrofit2.HttpException) {
                if (error.code() == 404) null else throw error
            }
        }
    }

    override suspend fun broadcastTransaction(txHex: String, network: BitcoinIndexerRoutes.Network, baseUrl: String?): String {
        // A fallback may only resubmit these exact signed bytes, never rebuild a transaction.
        val body = BitcoinIndexerRoutes.normalizeBroadcastTransactionBody(txHex).encodeToByteArray().toRequestBody(TEXT_PLAIN_MEDIA_TYPE)
        return providers.execute(endpoints(network, baseUrl)) {
            api.broadcastTransaction(BitcoinIndexerRoutes.broadcastTransactionUrl(network, it), body)
        }
    }

    private fun endpoints(network: BitcoinIndexerRoutes.Network, baseUrl: String?): List<String> {
        val primary = BitcoinIndexerRoutes.normalizeBaseUrl(baseUrl ?: BitcoinIndexerRoutes.baseUrl(network))
        val alternate = if (network == BitcoinIndexerRoutes.Network.Mainnet) {
            "https://mempool.space/api"
        } else {
            "https://mempool.space/testnet/api"
        }
        val publicPool = listOf(BitcoinIndexerRoutes.baseUrl(network), alternate)
        // An explicit provider is a distinct trust/chain boundary unless it is an exact member of
        // this network's public pool. Regtest often shares testnet address encoding, even over TLS.
        return if (primary in publicPool) listOf(primary, *publicPool.toTypedArray()).distinct() else listOf(primary)
    }

    private companion object {
        val TEXT_PLAIN_MEDIA_TYPE = "text/plain".toMediaType()
    }
}
