package jp.co.soramitsu.common.data.network.iroha

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.net.MalformedURLException
import java.net.URLEncoder
import java.net.URL

object IrohaToriiRoutes {
    const val DEFAULT_LIMIT = 100
    const val MAX_LIMIT = 500

    fun healthUrl(baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira)): String {
        return "${normalizeBaseUrl(baseUrl)}/health"
    }

    fun accountsUrl(
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        cursor: String? = null
    ): String {
        return appendQuery("${normalizeBaseUrl(baseUrl)}/v1/accounts", pageQuery(limit, cursor))
    }

    fun accountUrl(
        accountId: String,
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira)
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/v1/accounts/${encodePathSegment(normalizeAccountId(accountId))}"
    }

    fun accountAssetsUrl(
        accountId: String,
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        cursor: String? = null
    ): String {
        val query = pageQuery(limit, cursor)

        return appendQuery("${accountUrl(accountId, baseUrl)}/assets", query)
    }

    fun accountHistoryUrl(
        accountId: String,
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        cursor: String? = null,
        filter: String? = null
    ): String {
        val query = pageQuery(limit, cursor).toMutableList()
        filter?.let { query += "filter=${encodeQueryValue(normalizeFilter(it))}" }

        return appendQuery("${accountUrl(accountId, baseUrl)}/history", query)
    }

    /** Torii text filter for the transfer movements of the given asset definitions. */
    fun transferHistoryFilter(assetDefinitionIds: Collection<String>): String {
        val literals = assetDefinitionIds.map { "\"${escapeFilterString(normalizeAssetSelector(it))}\"" }.distinct()
        if (literals.isEmpty()) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_ASSET)
        }

        return "asset_definition_id in [${literals.joinToString(", ")}] and type = \"TRANSFER\""
    }

    fun assetDefinitionsUrl(
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        cursor: String? = null
    ): String {
        return appendQuery(
            "${normalizeBaseUrl(baseUrl)}/v1/assets/definitions",
            pageQuery(limit, cursor)
        )
    }

    fun submitTransactionUrl(baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira)): String {
        return "${normalizeBaseUrl(baseUrl)}/v1/pipeline/transactions"
    }

    fun transactionStatusUrl(
        hash: String,
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        scope: TransactionStatusScope = TransactionStatusScope.Auto
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/v1/pipeline/transactions/status?hash=${normalizeHash(hash)}&scope=${scope.apiValue}"
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val parsed = try {
            URL(trimmed)
        } catch (_: MalformedURLException) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_BASE_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"

        if (
            parsed.host.isNullOrEmpty() ||
            (parsed.protocol != "https" && !(parsed.protocol == "http" && isLocal)) ||
            parsed.userInfo != null || parsed.query != null || parsed.ref != null
        ) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_BASE_URL)
        }

        return trimmed.trimEnd('/')
    }

    fun normalizeAccountId(accountId: String): String {
        return normalizePathValue(accountId, ErrorCode.INVALID_ACCOUNT_ID)
    }

    fun normalizeAssetSelector(asset: String): String {
        val normalized = asset.trim()
        if (normalized.isEmpty() || normalized.length > 256 || normalized.any { it <= ' ' } || ASSET_FORBIDDEN.containsMatchIn(normalized)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_ASSET)
        }

        return normalized
    }

    fun requireToriiBaseUrl(network: UniversalWalletRegistry.IrohaNetwork): String {
        return network.toriiBaseUrl ?: throw IrohaToriiRouteException(ErrorCode.MISSING_TORII_BASE_URL)
    }

    private fun normalizePathValue(value: String, errorCode: ErrorCode): String {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.length > 256 || normalized.any { it <= ' ' } || PATH_FORBIDDEN.containsMatchIn(normalized)) {
            throw IrohaToriiRouteException(errorCode)
        }

        return normalized
    }

    private fun normalizeHash(hash: String): String {
        val normalized = hash.trim().removePrefix("0x").lowercase()
        if (!HASH_256.matches(normalized)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_HASH)
        }

        return normalized
    }

    // Torii cursors are opaque base64url tokens; pass them back exactly as received.
    private fun normalizeCursor(cursor: String): String {
        if (!CURSOR.matches(cursor)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_CURSOR)
        }

        return cursor
    }

    private fun normalizeFilter(filter: String): String {
        if (filter.isBlank() || filter.length > MAX_FILTER_LENGTH || filter.any { it < ' ' || it == '\u007f' }) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_FILTER)
        }

        return filter
    }

    private fun escapeFilterString(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun pageQuery(limit: Int?, cursor: String?): List<String> {
        val query = mutableListOf<String>()

        limit?.let {
            if (it !in 1..MAX_LIMIT) {
                throw IrohaToriiRouteException(ErrorCode.INVALID_LIMIT)
            }
            query += "limit=$it"
        }
        cursor?.let { query += "cursor=${normalizeCursor(it)}" }

        return query
    }

    private fun appendQuery(base: String, query: List<String>): String {
        return if (query.isEmpty()) base else "$base?${query.joinToString("&")}"
    }

    private fun encodePathSegment(value: String): String {
        return encodeQueryValue(value)
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    class IrohaToriiRouteException(val code: ErrorCode) : IllegalArgumentException(code.name)

    enum class ErrorCode {
        INVALID_BASE_URL,
        MISSING_TORII_BASE_URL,
        INVALID_ACCOUNT_ID,
        INVALID_ASSET,
        INVALID_HASH,
        INVALID_LIMIT,
        INVALID_CURSOR,
        INVALID_FILTER
    }

    enum class TransactionStatusScope(val apiValue: String) {
        Local("local"),
        Auto("auto"),
        Global("global")
    }

    private val PATH_FORBIDDEN = Regex("[/?#]")
    private val ASSET_FORBIDDEN = Regex("[/?]")
    private val HASH_256 = Regex("^[0-9a-f]{64}$")
    private val CURSOR = Regex("^[A-Za-z0-9_-]{1,4096}$")
    private const val MAX_FILTER_LENGTH = 2048
}
