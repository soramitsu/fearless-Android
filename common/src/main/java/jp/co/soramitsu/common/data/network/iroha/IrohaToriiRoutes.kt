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

    fun mcpUrl(
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String = requireToriiBaseUrl(network)
    ): String {
        return "${normalizeBaseUrl(baseUrl)}${normalizePath(network.mcpPath)}"
    }

    fun accountsUrl(
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        offset: Long? = null,
        countMode: CountMode? = null
    ): String {
        return appendQuery("${normalizeBaseUrl(baseUrl)}/v1/accounts", pageQuery(limit, offset, countMode))
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
        offset: Long? = null,
        countMode: CountMode? = null,
        asset: String? = null,
        scope: String? = null
    ): String {
        val query = pageQuery(limit, offset, countMode).toMutableList()
        asset?.let { query += "asset=${encodeQueryValue(normalizeAssetSelector(it))}" }
        scope?.let { query += "scope=${encodeQueryValue(normalizeAccountAssetScope(it))}" }

        return appendQuery("${accountUrl(accountId, baseUrl)}/assets", query)
    }

    fun assetDefinitionsUrl(
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        limit: Int? = null,
        offset: Long? = null,
        countMode: CountMode? = null
    ): String {
        return appendQuery(
            "${normalizeBaseUrl(baseUrl)}/v1/assets/definitions",
            pageQuery(limit, offset, countMode)
        )
    }

    fun transactionStatusUrl(
        hash: String,
        baseUrl: String = requireToriiBaseUrl(UniversalWalletRegistry.taira),
        scope: TransactionStatusScope = TransactionStatusScope.Auto
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/v1/pipeline/transactions/status?hash=${normalizeHash(hash)}&scope=${scope.apiValue}"
    }

    fun mcpJsonRpcRequest(
        method: String,
        id: String,
        params: Map<String, Any?>? = null
    ): IrohaMcpJsonRpcRequest {
        return IrohaMcpJsonRpcRequest(
            id = normalizeJsonRpcId(id),
            method = normalizeMcpMethod(method),
            params = params
        )
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
            parsed.userInfo != null ||
            parsed.query != null ||
            parsed.ref != null
        ) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_BASE_URL)
        }

        return trimmed.trimEnd('/')
    }

    fun normalizeAccountId(accountId: String): String {
        return normalizePathValue(accountId, ErrorCode.INVALID_ACCOUNT_ID)
    }

    fun normalizeAssetSelector(asset: String): String {
        return normalizeAssetDefinitionId(asset)
    }

    fun normalizeAssetDefinitionId(assetDefinitionId: String): String {
        val normalized = assetDefinitionId.trim()
        if (!CANONICAL_ASSET_DEFINITION_ID.matches(normalized)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_ASSET)
        }

        return normalized
    }

    fun normalizeAccountAssetScope(scope: String): String {
        val normalized = scope.trim()
        if (normalized == "global") return normalized

        val value = DATASPACE_SCOPE.matchEntire(normalized)?.groupValues?.get(1)
            ?: throw IrohaToriiRouteException(ErrorCode.INVALID_SCOPE)
        if (value.length > MAX_U64.length || (value.length == MAX_U64.length && value > MAX_U64)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_SCOPE)
        }

        return normalized
    }

    fun requireToriiBaseUrl(network: UniversalWalletRegistry.IrohaNetwork): String {
        return network.toriiBaseUrl ?: throw IrohaToriiRouteException(ErrorCode.MISSING_TORII_BASE_URL)
    }

    private fun normalizePath(path: String): String {
        val normalized = path.trim()
        if (!normalized.startsWith("/") || normalized.contains("..") || normalized.contains("?") || normalized.contains("#")) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_PATH)
        }

        return normalized.trimEnd('/')
    }

    private fun normalizePathValue(value: String, errorCode: ErrorCode): String {
        val normalized = value.trim()
        if (normalized.isEmpty() || normalized.length > 256 || normalized.any { it <= ' ' } || PATH_FORBIDDEN.containsMatchIn(normalized)) {
            throw IrohaToriiRouteException(errorCode)
        }

        return normalized
    }

    private fun normalizeHash(hash: String): String {
        if (!HASH_256.matches(hash)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_HASH)
        }

        return hash
    }

    private fun normalizeJsonRpcId(id: String): String {
        val normalized = id.trim()
        if (!JSON_RPC_ID.matches(normalized)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_JSON_RPC_ID)
        }

        return normalized
    }

    private fun normalizeMcpMethod(method: String): String {
        val normalized = method.trim()
        if (!MCP_METHOD.matches(normalized)) {
            throw IrohaToriiRouteException(ErrorCode.INVALID_MCP_METHOD)
        }

        return normalized
    }

    private fun pageQuery(limit: Int?, offset: Long?, countMode: CountMode?): List<String> {
        val query = mutableListOf<String>()

        limit?.let {
            if (it !in 1..MAX_LIMIT) {
                throw IrohaToriiRouteException(ErrorCode.INVALID_LIMIT)
            }
            query += "limit=$it"
        }
        offset?.let {
            if (it < 0) {
                throw IrohaToriiRouteException(ErrorCode.INVALID_OFFSET)
            }
            query += "offset=$it"
        }
        countMode?.let {
            query += "count_mode=${it.apiValue}"
        }

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
        INVALID_PATH,
        INVALID_ACCOUNT_ID,
        INVALID_ASSET,
        INVALID_SCOPE,
        INVALID_HASH,
        INVALID_LIMIT,
        INVALID_OFFSET,
        INVALID_JSON_RPC_ID,
        INVALID_MCP_METHOD
    }

    enum class CountMode(val apiValue: String) {
        Bounded("bounded"),
        Exact("exact")
    }

    enum class TransactionStatusScope(val apiValue: String) {
        Local("local"),
        Auto("auto"),
        Global("global")
    }

    private val PATH_FORBIDDEN = Regex("[/?#]")
    private val CANONICAL_ASSET_DEFINITION_ID = Regex("^[1-9A-HJ-NP-Za-km-z]{20,64}$")
    private const val MAX_U64 = "18446744073709551615"
    private val DATASPACE_SCOPE = Regex("^dataspace:(0|[1-9][0-9]*)$")
    private val HASH_256 = Regex("^[0-9a-f]{63}[13579bdf]$")
    private val JSON_RPC_ID = Regex("^[A-Za-z0-9._:-]{1,64}$")
    private val MCP_METHOD = Regex("^[A-Za-z][A-Za-z0-9_/.-]{0,127}$")
}
