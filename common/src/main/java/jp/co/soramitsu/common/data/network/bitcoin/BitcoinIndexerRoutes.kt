package jp.co.soramitsu.common.data.network.bitcoin

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.net.MalformedURLException
import java.net.URL

object BitcoinIndexerRoutes {
    const val MAX_TX_HEX_LENGTH = 800_000

    fun addressUrl(
        address: String,
        network: Network = Network.Mainnet,
        baseUrl: String = baseUrl(network)
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/address/${normalizeAddress(address, network)}"
    }

    fun utxosUrl(
        address: String,
        network: Network = Network.Mainnet,
        baseUrl: String = baseUrl(network)
    ): String {
        return "${addressUrl(address, network, baseUrl)}/utxo"
    }

    fun transactionsUrl(
        address: String,
        network: Network = Network.Mainnet,
        baseUrl: String = baseUrl(network),
        lastSeenTxid: String? = null,
        mempool: Boolean = false
    ): String {
        val normalizedAddress = normalizeAddress(address, network)
        val base = "${normalizeBaseUrl(baseUrl)}/address/$normalizedAddress/txs"

        return when {
            mempool -> "$base/mempool"
            lastSeenTxid != null -> "$base/chain/${normalizeTxid(lastSeenTxid)}"
            else -> base
        }
    }

    fun feeEstimatesUrl(
        network: Network = Network.Mainnet,
        baseUrl: String = baseUrl(network)
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/fee-estimates"
    }

    fun broadcastTransactionUrl(
        network: Network = Network.Mainnet,
        baseUrl: String = baseUrl(network)
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/tx"
    }

    fun normalizeBroadcastTransactionBody(txHex: String): String {
        val normalized = txHex.trim().lowercase()
        if (!TX_HEX.matches(normalized) || normalized.length > MAX_TX_HEX_LENGTH) {
            throw BitcoinIndexerRouteException(ErrorCode.INVALID_TX_HEX)
        }

        return normalized
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val parsed = try {
            URL(trimmed)
        } catch (_: MalformedURLException) {
            throw BitcoinIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"

        if ((parsed.protocol != "https" && !(parsed.protocol == "http" && isLocal)) ||
            parsed.host.isBlank() || parsed.userInfo != null || parsed.query != null || parsed.ref != null
        ) {
            throw BitcoinIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }

        return trimmed.trimEnd('/')
    }

    fun normalizeAddress(address: String, network: Network): String {
        return try {
            BitcoinP2wpkhAddress.normalize(address, network)
        } catch (_: IllegalArgumentException) {
            throw BitcoinIndexerRouteException(ErrorCode.INVALID_ADDRESS)
        }
    }

    fun normalizeTxid(txid: String): String {
        val normalized = txid.trim().lowercase()
        if (!TXID.matches(normalized)) {
            throw BitcoinIndexerRouteException(ErrorCode.INVALID_TXID)
        }

        return normalized
    }

    fun baseUrl(network: Network): String = when (network) {
        Network.Mainnet -> UniversalWalletRegistry.BITCOIN_MAINNET_INDEXER_BASE_URL
        Network.Testnet -> UniversalWalletRegistry.BITCOIN_TESTNET_INDEXER_BASE_URL
    }

    class BitcoinIndexerRouteException(val code: ErrorCode) : IllegalArgumentException(code.name)

    enum class ErrorCode {
        INVALID_BASE_URL,
        INVALID_ADDRESS,
        INVALID_TXID,
        INVALID_TX_HEX
    }

    enum class Network {
        Mainnet,
        Testnet
    }

    private val TXID = Regex("^[0-9a-f]{64}$")
    private val TX_HEX = Regex("^(?:[0-9a-f]{2})+$")
}
