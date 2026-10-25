package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.time.Instant
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

private const val MAX_KAIASCAN_PAGE_SIZE = 500 // Free-plan cap; API schema permits up to 2,000.
private const val MAINNET_HOST = "mainnet-oapi.kaiascan.io"
private const val KAIROS_HOST = "kairos-oapi.kaiascan.io"
private val ADDRESS = Regex("0x[0-9a-fA-F]{40}")
private val HASH = Regex("0x[0-9a-fA-F]{64}")

/** Reads the chain-bound KaiaScan OAPI; the configured URL is never used as an auth-header destination. */
class KaiaScanHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val historyUrl: String,
    private val apiKey: String = BuildConfig.KAIASCAN_API_KEY
) : HistorySource {
    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        if (pageSize <= 0 || TransactionFilter.TRANSFER !in filters) return CursorPage(null, emptyList())

        val host = when (chain.id) {
            "8217" -> MAINNET_HOST
            "1001" -> KAIROS_HOST
            else -> throw HistoryNotSupportedException()
        }
        verifyConfiguredEndpoint(host)
        require(apiKey.isNotBlank() && apiKey.length <= 512 && apiKey.all { it > ' ' && it != '\u007f' }) {
            "KaiaScan history credential unavailable"
        }
        require(ADDRESS.matches(accountAddress) && accountAddress.equals(accountId.toHexString(true), true)) {
            "KaiaScan history account mismatch"
        }
        val native = when (chainAsset.type) {
            ChainAssetType.Normal -> {
                require(chainAsset.isUtility) { "KaiaScan native asset mismatch" }
                true
            }
            ChainAssetType.ERC20, ChainAssetType.BEP20 -> {
                require(ADDRESS.matches(chainAsset.id)) { "KaiaScan token contract mismatch" }
                false
            }
            else -> throw HistoryNotSupportedException()
        }
        val page = cursor?.toIntOrNull() ?: 1
        require(cursor == null || (page > 0 && cursor == page.toString())) { "Invalid KaiaScan history cursor" }
        val size = pageSize.coerceAtMost(MAX_KAIASCAN_PAGE_SIZE)
        val url = "https://$host/api/v1/accounts/$accountAddress/" +
            if (native) "transactions" else "token-transfers"
        val bearer = "Bearer $apiKey"
        val response = if (native) {
            walletOperationsApi.getKaiaScanNativeHistory(url, bearer, page, size)
        } else {
            walletOperationsApi.getKaiaScanTokenHistory(url, bearer, chainAsset.id, page, size)
        }
        val (rows, last) = response.checkedPage(page, size)
        val operations = rows.mapNotNull { row ->
            val item = row.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalArgumentException("Malformed KaiaScan history row")
            if (native) item.nativeOperation(accountAddress, chainAsset) else item.tokenOperation(accountAddress, chainAsset)
        }
        check(operations.isNotEmpty() || last) { "KaiaScan page has no representable transfers" }
        val nextCursor = if (last) null else (page + 1).toString()
        return CursorPage(nextCursor, operations)
    }

    private fun verifyConfiguredEndpoint(expectedHost: String) {
        val uri = runCatching { URI(historyUrl) }.getOrNull()
        require(
            uri != null && uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
                uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in setOf("/api/v1", "/api/v1/") &&
                (uri.host == expectedHost || (expectedHost == MAINNET_HOST &&
                    uri.host in setOf("scope.klaytn.com", "scope.kaia.io")))
        ) { "Untrusted KaiaScan history endpoint" }
    }
}

private fun JsonObject.checkedPage(requestedPage: Int, size: Int): Pair<JsonArray, Boolean> {
    val rows = requiredArray("results")
    val paging = requiredObject("paging")
    val totalCount = paging.requiredInteger("total_count")
    val currentPage = paging.requiredInt("current_page")
    val totalPages = paging.requiredInt("total_page")
    val last = paging.requiredBoolean("last")
    require(totalCount >= BigInteger.ZERO && rows.size() <= size && totalCount >= BigInteger.valueOf(rows.size().toLong())) {
        "Malformed KaiaScan history pagination"
    }
    if (rows.size() == 0 && requestedPage == 1 && totalCount == BigInteger.ZERO) {
        require(currentPage in 0..1 && totalPages == 0 && last) { "Malformed KaiaScan empty history" }
    } else {
        require(currentPage == requestedPage && totalPages >= requestedPage && last == (requestedPage == totalPages)) {
            "Malformed KaiaScan history pagination"
        }
        require(rows.size() > 0 || last) { "Malformed KaiaScan empty history" }
    }
    require(last || requestedPage < Int.MAX_VALUE) { "KaiaScan history cursor overflow" }
    return rows to last
}

private fun JsonObject.nativeOperation(account: String, asset: Asset): Operation? {
    require(requiredInteger("block_id") >= BigInteger.ZERO) { "Malformed KaiaScan block" }
    requiredInt("transaction_index")
    requiredString("transaction_type")
    requiredDecimal("effective_gas_price")
    val from = requiredAddress("from")
    val to = optionalAddress("to")
    val feePayer = optionalAddress("fee_payer")
    if (!from.equals(account, true) && !to.equals(account, true)) {
        require(feePayer.equals(account, true)) { "Unrelated KaiaScan history row" }
        return null // Fee sponsorship is not an incoming transfer of someone else's value.
    }
    val amount = requiredDecimal("amount").scaledUnits(asset.precision)
    val fee = requiredDecimal("transaction_fee").scaledUnits(asset.precision)
    // Contract creation has no recipient and cannot be represented as a transfer.
    if (to == null) return null
    val status = when (requiredObject("status").requiredString("status")) {
        "Success" -> Operation.Status.COMPLETED
        "Fail" -> Operation.Status.FAILED
        else -> throw IllegalArgumentException("Unknown KaiaScan transaction status")
    }
    return transferOperation(account, asset, from, to, amount, fee, status)
}

private fun JsonObject.tokenOperation(account: String, asset: Asset): Operation? {
    require(requiredInteger("block_id") >= BigInteger.ZERO) { "Malformed KaiaScan block" }
    require(requiredObject("contract").requiredAddress("contract_address").equals(asset.id, true)) {
        "KaiaScan token contract mismatch"
    }
    val from = requiredAddress("from")
    val to = optionalAddress("to")
    val feePayer = optionalAddress("fee_payer")
    if (!from.equals(account, true) && !to.equals(account, true)) {
        require(feePayer.equals(account, true)) { "Unrelated KaiaScan history row" }
        return null
    }
    require(to != null) { "Malformed KaiaScan token recipient" }
    val amount = requiredDecimal("amount").scaledUnits(asset.precision)
    // An indexed FT movement has a block ID, so COMPLETED describes the token movement.
    // The endpoint omits receipt status and fee; receipt parity still needs live qualification.
    return transferOperation(account, asset, from, to, amount, null, Operation.Status.COMPLETED)
}

private fun JsonObject.transferOperation(
    account: String,
    asset: Asset,
    from: String,
    to: String,
    amount: BigInteger,
    fee: BigInteger?,
    status: Operation.Status
): Operation {
    val hash = requiredString("transaction_hash")
    require(HASH.matches(hash)) { "Malformed KaiaScan transaction hash" }
    val time = runCatching { Instant.parse(requiredString("datetime")).toEpochMilli() }.getOrNull()
        ?: throw IllegalArgumentException("Malformed KaiaScan transaction time")
    return Operation(
        id = hash,
        address = account,
        time = time,
        chainAsset = asset,
        type = Operation.Type.Transfer(
            hash = hash,
            myAddress = account,
            amount = amount,
            receiver = to.lowercase(),
            sender = from.lowercase(),
            status = status,
            fee = fee
        )
    )
}

private fun BigDecimal.scaledUnits(decimals: Int): BigInteger {
    require(decimals in 0..36 && signum() >= 0 && scale() in -78..78 && precision() <= 78) {
        "Invalid KaiaScan amount"
    }
    val units = movePointRight(decimals).toBigIntegerExact()
    require(units.bitLength() <= 256) { "KaiaScan amount exceeds 256 bits" }
    return units
}

private fun JsonObject.requiredField(key: String): JsonElement = get(key)?.takeUnless { it.isJsonNull }
    ?: throw IllegalArgumentException("Missing KaiaScan history field")

private fun JsonObject.requiredObject(key: String): JsonObject = requiredField(key).takeIf { it.isJsonObject }?.asJsonObject
    ?: throw IllegalArgumentException("Malformed KaiaScan history object")

private fun JsonObject.requiredArray(key: String): JsonArray = requiredField(key).takeIf { it.isJsonArray }?.asJsonArray
    ?: throw IllegalArgumentException("Malformed KaiaScan history list")

private fun JsonObject.requiredString(key: String): String {
    val value = requiredField(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString && value.asString.isNotBlank()) {
        "Malformed KaiaScan history text"
    }
    return value.asString
}

private fun JsonObject.requiredDecimal(key: String): BigDecimal {
    val value = requiredField(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asString.length <= 128) {
        "Malformed KaiaScan history number"
    }
    val number = BigDecimal(value.asString)
    require(number.scale() in -78..78 && number.precision() <= 78) { "Oversized KaiaScan history number" }
    return number
}

private fun JsonObject.requiredInteger(key: String): BigInteger = requiredDecimal(key).toBigIntegerExact()

private fun JsonObject.requiredInt(key: String): Int {
    val value = requiredInteger(key)
    require(value >= BigInteger.ZERO && value <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
        "Malformed KaiaScan history integer"
    }
    return value.toInt()
}

private fun JsonObject.requiredBoolean(key: String): Boolean {
    val value = requiredField(key)
    require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "Malformed KaiaScan history boolean" }
    return value.asBoolean
}

private fun JsonObject.requiredAddress(key: String): String = requiredString(key).also {
    require(ADDRESS.matches(it)) { "Malformed KaiaScan history address" }
}

private fun JsonObject.optionalAddress(key: String): String? = get(key)?.takeUnless { it.isJsonNull }?.let {
    requiredAddress(key)
}
