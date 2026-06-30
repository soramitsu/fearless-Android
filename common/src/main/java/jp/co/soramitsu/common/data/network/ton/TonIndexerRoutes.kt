package jp.co.soramitsu.common.data.network.ton

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.net.MalformedURLException
import java.net.URLEncoder
import java.net.URL

object TonIndexerRoutes {
    const val DEFAULT_TX_PAGE = 1
    const val DEFAULT_SWAP_LIMIT = 100
    const val MAX_SWAP_LIMIT = 500
    const val MAX_RUN_GET_BATCH_SIZE = 64

    fun healthUrl(baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/health"
    }

    fun contractsUrl(baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/contracts"
    }

    fun serviceInfoUrl(baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/service-info"
    }

    fun balanceUrl(address: String, baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, address, "balance")
    }

    fun balancesUrl(address: String, baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, address, "balances")
    }

    fun assetsUrl(address: String, baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, address, "assets")
    }

    fun stateUrl(address: String, baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return accountUrl(baseUrl, address, "state")
    }

    fun transactionsUrl(
        address: String,
        baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL,
        page: Int = DEFAULT_TX_PAGE,
        cursorLt: String? = null,
        cursorHash: String? = null
    ): String {
        if (page < 1) {
            throw TonIndexerRouteException(ErrorCode.INVALID_PAGE)
        }
        if ((cursorLt == null) != (cursorHash == null)) {
            throw TonIndexerRouteException(ErrorCode.CURSOR_MISMATCH)
        }

        val url = StringBuilder(accountUrl(baseUrl, address, "txs")).append("?page=").append(page)

        if (cursorLt != null && cursorHash != null) {
            url.append("&cursor_lt=").append(normalizeLt(cursorLt))
            url.append("&cursor_hash=").append(encodeQueryValue(normalizeHash(cursorHash)))
        }

        return url.toString()
    }

    fun swapsUrl(
        address: String,
        baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL,
        limit: Int = DEFAULT_SWAP_LIMIT,
        fromUtime: Long? = null,
        toUtime: Long? = null,
        payToken: String? = null,
        receiveToken: String? = null,
        executionType: TonSwapExecutionType? = null,
        status: TonSwapStatus? = null,
        includeReverse: Boolean? = null
    ): String {
        if (limit !in 1..MAX_SWAP_LIMIT) {
            throw TonIndexerRouteException(ErrorCode.INVALID_LIMIT)
        }
        if (fromUtime != null && fromUtime < 1 || toUtime != null && toUtime < 1) {
            throw TonIndexerRouteException(ErrorCode.INVALID_UTIME)
        }
        if (fromUtime != null && toUtime != null && fromUtime > toUtime) {
            throw TonIndexerRouteException(ErrorCode.INVALID_UTIME_RANGE)
        }

        val query = mutableListOf("limit=$limit")
        fromUtime?.let { query += "from_utime=$it" }
        toUtime?.let { query += "to_utime=$it" }
        payToken?.let { query += "pay_token=${encodeQueryValue(normalizeTokenFilter(it))}" }
        receiveToken?.let { query += "receive_token=${encodeQueryValue(normalizeTokenFilter(it))}" }
        executionType?.let { query += "execution_type=${it.apiValue}" }
        status?.let { query += "status=${it.apiValue}" }
        includeReverse?.let { query += "include_reverse=${if (it) "true" else "false"}" }

        return "${accountUrl(baseUrl, address, "swaps")}?${query.joinToString("&")}"
    }

    fun jettonTransferPayloadUrl(
        jetton: String,
        owner: String,
        baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL
    ): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/jettons/${normalizeAddress(jetton)}/transfer/${normalizeAddress(owner)}/payload"
    }

    fun runGetMethodUrl(baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/runGetMethod"
    }

    fun runGetMethodsUrl(baseUrl: String = UniversalWalletRegistry.TON_INDEXER_BASE_URL): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/runGetMethods"
    }

    fun runGetMethodRequest(
        address: String,
        method: String,
        stack: List<List<Any?>> = emptyList()
    ): TonRunGetMethodRequest {
        return TonRunGetMethodRequest(
            address = normalizeAddress(address),
            method = normalizeGetterMethod(method),
            stack = stack
        )
    }

    fun runGetMethodsRequest(calls: List<TonRunGetMethodRequest>): TonRunGetMethodsRequest {
        if (calls.isEmpty() || calls.size > MAX_RUN_GET_BATCH_SIZE) {
            throw TonIndexerRouteException(ErrorCode.INVALID_CALLS)
        }

        return TonRunGetMethodsRequest(calls)
    }

    fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        val parsed = try {
            URL(trimmed)
        } catch (_: MalformedURLException) {
            throw TonIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"

        if (parsed.protocol != "https" && !isLocal) {
            throw TonIndexerRouteException(ErrorCode.INVALID_BASE_URL)
        }

        return trimmed.trimEnd('/')
    }

    fun normalizeAddress(address: String): String {
        val normalized = address.trim()
        val isFriendly = TON_FRIENDLY_ADDRESS.matches(normalized)
        val isRaw = TON_RAW_ADDRESS.matches(normalized.lowercase())

        if (!isFriendly && !isRaw) {
            throw TonIndexerRouteException(ErrorCode.INVALID_ADDRESS)
        }

        return if (isRaw) normalized.lowercase() else normalized
    }

    private fun accountUrl(baseUrl: String, address: String, section: String): String {
        return "${normalizeBaseUrl(baseUrl)}/api/indexer/v1/accounts/${normalizeAddress(address)}/$section"
    }

    private fun normalizeLt(value: String): String {
        val normalized = value.trim()
        if (!LT.matches(normalized)) {
            throw TonIndexerRouteException(ErrorCode.INVALID_CURSOR)
        }

        return normalized
    }

    private fun normalizeHash(value: String): String {
        val normalized = value.trim()
        if (!BASE64_HASH.matches(normalized)) {
            throw TonIndexerRouteException(ErrorCode.INVALID_CURSOR)
        }

        return normalized
    }

    private fun normalizeTokenFilter(value: String): String {
        val normalized = value.trim()
        if (!TOKEN_FILTER.matches(normalized)) {
            throw TonIndexerRouteException(ErrorCode.INVALID_TOKEN_FILTER)
        }

        return normalized
    }

    private fun normalizeGetterMethod(value: String): String {
        val normalized = value.trim()
        if (!GETTER_METHOD.matches(normalized)) {
            throw TonIndexerRouteException(ErrorCode.INVALID_METHOD)
        }

        return normalized
    }

    private fun encodeQueryValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    class TonIndexerRouteException(val code: ErrorCode) : IllegalArgumentException(code.name)

    enum class ErrorCode {
        INVALID_BASE_URL,
        INVALID_ADDRESS,
        INVALID_PAGE,
        CURSOR_MISMATCH,
        INVALID_CURSOR,
        INVALID_LIMIT,
        INVALID_UTIME,
        INVALID_UTIME_RANGE,
        INVALID_TOKEN_FILTER,
        INVALID_METHOD,
        INVALID_CALLS
    }

    enum class TonSwapExecutionType(val apiValue: String) {
        Market("market"),
        Limit("limit"),
        Twap("twap"),
        Unknown("unknown")
    }

    enum class TonSwapStatus(val apiValue: String) {
        Success("success"),
        Failed("failed"),
        Pending("pending")
    }

    private val TON_FRIENDLY_ADDRESS = Regex("^[A-Za-z0-9_-]{48}$")
    private val TON_RAW_ADDRESS = Regex("^(?:-1|0):[0-9a-f]{64}$")
    private val LT = Regex("^\\d+$")
    private val BASE64_HASH = Regex("^[A-Za-z0-9_+/=-]{40,64}$")
    private val TOKEN_FILTER = Regex("^[A-Za-z0-9._:-]{1,32}$")
    private val GETTER_METHOD = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
}
