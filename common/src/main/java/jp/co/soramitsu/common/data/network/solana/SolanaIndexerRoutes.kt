package jp.co.soramitsu.common.data.network.solana

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.net.MalformedURLException
import java.net.URL

object SolanaIndexerRoutes {
    const val DEFAULT_LIMIT = 100
    const val MAX_LIMIT = 250
    const val MAX_METADATA_BATCH_SIZE = 100

    fun serviceInfoUrl(baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/service-info"
    }

    fun balancesUrl(wallet: String, baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, wallet, "balances")
    }

    fun assetsUrl(wallet: String, baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, wallet, "assets")
    }

    fun stateUrl(wallet: String, baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, wallet, "state")
    }

    fun transactionsUrl(
        wallet: String,
        baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL,
        before: String? = null,
        limit: Int = DEFAULT_LIMIT
    ): String {
        if (limit !in 1..MAX_LIMIT) {
            throw SolanaIndexerRouteException(ErrorCode.INVALID_LIMIT)
        }

        val normalizedWallet = normalizePublicKey(wallet, ErrorCode.INVALID_WALLET)
        val url = StringBuilder("${normalizeBaseUrl(baseUrl)}/api/indexer/v1/accounts/$normalizedWallet/txs")
            .append("?limit=")
            .append(limit)

        before?.let {
            url.append("&before=").append(normalizeSignature(it))
        }

        return url.toString()
    }

    fun tokenMetadataUrl(mint: String, baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/tokens/${normalizePublicKey(mint, ErrorCode.INVALID_MINT)}/metadata"
    }

    fun tokenMetadataBatchUrl(baseUrl: String = UniversalWalletRegistry.SOLANA_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/tokens/metadata"
    }

    fun tokenMetadataBatchRequest(mints: List<String>): SolanaTokenMetadataBatchRequest {
        if (mints.isEmpty() || mints.size > MAX_METADATA_BATCH_SIZE) {
            throw SolanaIndexerRouteException(ErrorCode.INVALID_MINTS)
        }

        return SolanaTokenMetadataBatchRequest(
            mints = mints.map { normalizePublicKey(it, ErrorCode.INVALID_MINT) }
        )
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val parsed = try {
            URL(trimmed)
        } catch (_: MalformedURLException) {
            throw SolanaIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"

        if (parsed.protocol != "https" && !isLocal) {
            throw SolanaIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }

        return trimmed.trimEnd('/')
    }

    private fun accountUrl(baseUrl: String, wallet: String, section: String): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/accounts/${normalizePublicKey(wallet, ErrorCode.INVALID_WALLET)}/$section"
    }

    private fun normalizePublicKey(value: String, errorCode: ErrorCode): String {
        if (!BASE58_PUBLIC_KEY.matches(value)) {
            throw SolanaIndexerRouteException(errorCode)
        }

        return value
    }

    private fun normalizeSignature(value: String): String {
        if (!BASE58_SIGNATURE.matches(value)) {
            throw SolanaIndexerRouteException(ErrorCode.INVALID_BEFORE)
        }

        return value
    }

    class SolanaIndexerRouteException(val code: ErrorCode) : IllegalArgumentException(code.name)

    enum class ErrorCode {
        INVALID_BASE_URL,
        INVALID_WALLET,
        INVALID_MINT,
        INVALID_MINTS,
        INVALID_BEFORE,
        INVALID_LIMIT
    }

    private val BASE58_PUBLIC_KEY = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
    private val BASE58_SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,128}$")
}
