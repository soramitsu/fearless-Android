package jp.co.soramitsu.common.data.network.solana

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import java.math.BigInteger
import java.net.MalformedURLException
import java.net.URL

interface SolanaRpcClient {
    suspend fun latestBlockhash(
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): SolanaLatestBlockhashResponse

    suspend fun feeForMessage(
        messageBase64: String,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): SolanaFeeForMessageResponse

    suspend fun minimumBalanceForRentExemption(
        dataLength: Int,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): Long

    suspend fun accountExists(
        address: String,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): Boolean

    suspend fun simulateTransaction(
        transactionBase64: String,
        options: SolanaSimulationOptions = SolanaSimulationOptions(),
        rpcUrl: String? = null
    ): SolanaSimulationResponse

    suspend fun sendRawTransaction(
        transactionBase64: String,
        options: SolanaBroadcastOptions = SolanaBroadcastOptions(),
        rpcUrl: String? = null
    ): String
}

class RetrofitSolanaRpcClient(
    private val api: SolanaRpcApi,
    private val defaultRpcUrl: String = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
) : SolanaRpcClient {
    private var nextId = 1L

    init {
        SolanaRpcRoutes.normalizeRpcUrl(defaultRpcUrl)
    }

    override suspend fun latestBlockhash(
        commitment: SolanaRpcCommitment,
        rpcUrl: String?
    ): SolanaLatestBlockhashResponse {
        return request(
            method = "getLatestBlockhash",
            params = jsonArray(jsonObject("commitment" to commitment.value)),
            rpcUrl = rpcUrl,
            normalize = ::normalizeLatestBlockhashResponse
        )
    }

    override suspend fun feeForMessage(
        messageBase64: String,
        commitment: SolanaRpcCommitment,
        rpcUrl: String?
    ): SolanaFeeForMessageResponse {
        return request(
            method = "getFeeForMessage",
            params = jsonArray(
                SolanaRpcRoutes.normalizeTransactionBase64(messageBase64),
                jsonObject("commitment" to commitment.value)
            ),
            rpcUrl = rpcUrl,
            normalize = ::normalizeFeeForMessageResponse
        )
    }

    override suspend fun minimumBalanceForRentExemption(
        dataLength: Int,
        commitment: SolanaRpcCommitment,
        rpcUrl: String?
    ): Long {
        return request(
            method = "getMinimumBalanceForRentExemption",
            params = jsonArray(
                SolanaRpcRoutes.normalizeDataLength(dataLength),
                jsonObject("commitment" to commitment.value)
            ),
            rpcUrl = rpcUrl
        ) { value ->
            requireUnsignedLong(value, SolanaRpcException.Code.INVALID_RENT_RESPONSE)
        }
    }

    override suspend fun accountExists(
        address: String,
        commitment: SolanaRpcCommitment,
        rpcUrl: String?
    ): Boolean {
        return request(
            method = "getAccountInfo",
            params = jsonArray(
                SolanaRpcRoutes.normalizePublicKey(address),
                jsonObject(
                    "commitment" to commitment.value,
                    "encoding" to "base64"
                )
            ),
            rpcUrl = rpcUrl,
            normalize = ::normalizeAccountInfoExists
        )
    }

    override suspend fun simulateTransaction(
        transactionBase64: String,
        options: SolanaSimulationOptions,
        rpcUrl: String?
    ): SolanaSimulationResponse {
        if (options.sigVerify && options.replaceRecentBlockhash) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_SIMULATION_OPTIONS)
        }

        return request(
            method = "simulateTransaction",
            params = jsonArray(
                SolanaRpcRoutes.normalizeTransactionBase64(transactionBase64),
                jsonObject(
                    "commitment" to options.commitment.value,
                    "encoding" to "base64",
                    "replaceRecentBlockhash" to options.replaceRecentBlockhash,
                    "sigVerify" to options.sigVerify
                )
            ),
            rpcUrl = rpcUrl,
            normalize = ::normalizeSimulationResponse
        )
    }

    override suspend fun sendRawTransaction(
        transactionBase64: String,
        options: SolanaBroadcastOptions,
        rpcUrl: String?
    ): String {
        val normalizedRetries = options.maxRetries?.also {
            if (it !in 0..10) {
                throw SolanaRpcException(SolanaRpcException.Code.INVALID_MAX_RETRIES)
            }
        }
        val optionBody = jsonObject(
            "encoding" to "base64",
            "preflightCommitment" to options.preflightCommitment.value,
            "skipPreflight" to options.skipPreflight
        )
        if (normalizedRetries != null) {
            optionBody.addProperty("maxRetries", normalizedRetries)
        }

        return request(
            method = "sendTransaction",
            params = jsonArray(
                SolanaRpcRoutes.normalizeTransactionBase64(transactionBase64),
                optionBody
            ),
            rpcUrl = rpcUrl,
            normalize = ::normalizeSignature
        )
    }

    private suspend fun <T> request(
        method: String,
        params: JsonArray,
        rpcUrl: String?,
        normalize: (JsonElement) -> T
    ): T {
        val id = nextId++
        val body = jsonObject(
            "id" to id,
            "jsonrpc" to "2.0",
            "method" to method
        )
        body.add("params", params)

        val response = api.postJsonRpc(SolanaRpcRoutes.normalizeRpcUrl(rpcUrl ?: defaultRpcUrl), body)
        val jsonrpc = response["jsonrpc"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        if (jsonrpc != "2.0" || response["id"]?.asLongOrNull() != id) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_RESPONSE)
        }
        response["error"]?.takeUnless { it is JsonNull }?.let {
            throw SolanaRpcException(
                code = SolanaRpcException.Code.RPC_ERROR,
                rpcError = normalizeRpcError(it)
            )
        }
        val result = response["result"] ?: throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_RESPONSE)

        return normalize(result)
    }

    private fun normalizeLatestBlockhashResponse(value: JsonElement): SolanaLatestBlockhashResponse {
        val objectValue = value.asObjectOrThrow(SolanaRpcException.Code.INVALID_BLOCKHASH_RESPONSE)
        return SolanaLatestBlockhashResponse(
            context = normalizeContext(objectValue["context"], SolanaRpcException.Code.INVALID_BLOCKHASH_RESPONSE),
            value = normalizeLatestBlockhash(objectValue["value"], SolanaRpcException.Code.INVALID_BLOCKHASH_RESPONSE)
        )
    }

    private fun normalizeFeeForMessageResponse(value: JsonElement): SolanaFeeForMessageResponse {
        val objectValue = value.asObjectOrThrow(SolanaRpcException.Code.INVALID_FEE_RESPONSE)
        val feeValue = objectValue["value"]
        return SolanaFeeForMessageResponse(
            context = normalizeContext(objectValue["context"], SolanaRpcException.Code.INVALID_FEE_RESPONSE),
            value = if (feeValue == null || feeValue is JsonNull) {
                null
            } else {
                requireUnsignedLong(feeValue, SolanaRpcException.Code.INVALID_FEE_RESPONSE)
            }
        )
    }

    private fun normalizeSimulationResponse(value: JsonElement): SolanaSimulationResponse {
        val objectValue = value.asObjectOrThrow(SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE)
        val simulationValue = objectValue["value"].asObjectOrThrow(SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE)
        val logs = simulationValue["logs"]?.takeUnless { it is JsonNull }?.let { logElement ->
            if (!logElement.isJsonArray || logElement.asJsonArray.any { !it.isJsonPrimitive || !it.asJsonPrimitive.isString }) {
                throw SolanaRpcException(SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE)
            }
            logElement.asJsonArray.map { it.asString }
        }
        val unitsConsumed = simulationValue["unitsConsumed"]?.takeUnless { it is JsonNull }?.let {
            requireUnsignedLong(it, SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE)
        }
        val replacementBlockhash = simulationValue["replacementBlockhash"]?.takeUnless { it is JsonNull }?.let {
            normalizeLatestBlockhash(it, SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE)
        }

        return SolanaSimulationResponse(
            context = normalizeContext(objectValue["context"], SolanaRpcException.Code.INVALID_SIMULATION_RESPONSE),
            value = SolanaSimulationValue(
                errorJson = simulationValue["err"]?.takeUnless { it is JsonNull }?.let { GSON.toJson(it) },
                logs = logs,
                replacementBlockhash = replacementBlockhash,
                unitsConsumed = unitsConsumed
            )
        )
    }

    private fun normalizeAccountInfoExists(value: JsonElement): Boolean {
        val objectValue = value.asObjectOrThrow(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        normalizeContext(objectValue["context"], SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        val accountValue = objectValue["value"] ?: throw SolanaRpcException(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)

        return when {
            accountValue is JsonNull -> false
            accountValue.isJsonObject -> true
            else -> throw SolanaRpcException(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        }
    }

    private fun normalizeLatestBlockhash(value: JsonElement?, code: SolanaRpcException.Code): SolanaLatestBlockhash {
        val objectValue = value.asObjectOrThrow(code)
        return SolanaLatestBlockhash(
            blockhash = normalizeBase58(objectValue["blockhash"], code),
            lastValidBlockHeight = requireUnsignedLong(objectValue["lastValidBlockHeight"], code)
        )
    }

    private fun normalizeContext(value: JsonElement?, code: SolanaRpcException.Code): SolanaRpcContext {
        val objectValue = value.asObjectOrThrow(code)
        val apiVersion = objectValue["apiVersion"]?.takeUnless { it is JsonNull }?.let {
            if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) {
                throw SolanaRpcException(code)
            }
            it.asString
        }
        return SolanaRpcContext(
            apiVersion = apiVersion,
            slot = requireUnsignedLong(objectValue["slot"], code)
        )
    }

    private fun normalizeSignature(value: JsonElement): String {
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString || !BASE58_SIGNATURE.matches(value.asString)) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_SIGNATURE_RESPONSE)
        }

        return value.asString
    }

    private fun normalizeBase58(value: JsonElement?, code: SolanaRpcException.Code): String {
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || !BASE58_32_TO_64.matches(value.asString)) {
            throw SolanaRpcException(code)
        }

        return value.asString
    }

    private fun requireUnsignedLong(value: JsonElement?, code: SolanaRpcException.Code): Long {
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber) {
            throw SolanaRpcException(code)
        }
        val text = value.asString
        if (!UNSIGNED_INTEGER.matches(text)) {
            throw SolanaRpcException(code)
        }
        val parsed = BigInteger(text)
        if (parsed > LONG_MAX) {
            throw SolanaRpcException(code)
        }

        return parsed.toLong()
    }

    private fun normalizeRpcError(value: JsonElement): SolanaJsonRpcErrorPayload {
        val objectValue = value.takeIf { it.isJsonObject }?.asJsonObject
        return SolanaJsonRpcErrorPayload(
            code = objectValue?.get("code")?.asIntOrNull() ?: -1,
            message = objectValue?.get("message")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                ?: "Unknown Solana RPC error",
            dataJson = objectValue?.get("data")?.let { GSON.toJson(it) }
        )
    }

    private fun JsonElement?.asObjectOrThrow(code: SolanaRpcException.Code): JsonObject {
        if (this == null || !isJsonObject) {
            throw SolanaRpcException(code)
        }

        return asJsonObject
    }

    private fun JsonElement.asLongOrNull(): Long? {
        if (!isJsonPrimitive || !asJsonPrimitive.isNumber) {
            return null
        }
        val text = asString
        if (!UNSIGNED_INTEGER.matches(text)) {
            return null
        }
        val parsed = BigInteger(text)
        if (parsed > LONG_MAX) {
            return null
        }

        return parsed.toLong()
    }

    private fun JsonElement.asIntOrNull(): Int? {
        if (!isJsonPrimitive || !asJsonPrimitive.isNumber) {
            return null
        }
        val text = asString
        if (!SIGNED_INTEGER.matches(text)) {
            return null
        }

        return runCatching { BigInteger(text).intValueExact() }.getOrNull()
    }

    private fun jsonArray(vararg values: Any): JsonArray {
        return JsonArray().apply {
            values.forEach { add(toJsonElement(it)) }
        }
    }

    private fun jsonObject(vararg values: Pair<String, Any>): JsonObject {
        return JsonObject().apply {
            values.forEach { (key, value) -> add(key, toJsonElement(value)) }
        }
    }

    private fun toJsonElement(value: Any): JsonElement {
        return when (value) {
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is JsonElement -> value
            else -> throw IllegalArgumentException("Unsupported Solana RPC JSON value")
        }
    }

    private companion object {
        val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
        val GSON = Gson()
        val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
        val SIGNED_INTEGER = Regex("^-?(0|[1-9][0-9]*)$")
        val BASE58_32_TO_64 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,64}$")
        val BASE58_SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,128}$")
    }
}

object SolanaRpcRoutes {
    private const val MAX_TRANSACTION_BASE64_LENGTH = 1_000_000
    private const val MAX_RENT_DATA_LENGTH = 10_000_000

    fun normalizeRpcUrl(rpcUrl: String): String {
        val trimmed = rpcUrl.trim()
        val parsed = try {
            URL(trimmed)
        } catch (_: MalformedURLException) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"
        if (parsed.protocol != "https" && !isLocal) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_URL)
        }

        return trimmed
            .substringBefore("#")
            .substringBefore("?")
            .trimEnd('/')
    }

    fun normalizeTransactionBase64(transactionBase64: String): String {
        if (transactionBase64.isEmpty() ||
            transactionBase64.length > MAX_TRANSACTION_BASE64_LENGTH ||
            transactionBase64.length % 4 != 0 ||
            !BASE64_TRANSACTION.matches(transactionBase64)
        ) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_TRANSACTION)
        }

        return transactionBase64
    }

    fun normalizeDataLength(dataLength: Int): Int {
        if (dataLength !in 0..MAX_RENT_DATA_LENGTH) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_DATA_LENGTH)
        }

        return dataLength
    }

    fun normalizePublicKey(publicKey: String): String {
        val trimmed = publicKey.trim()
        if (!BASE58_PUBLIC_KEY.matches(trimmed)) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_ACCOUNT_ADDRESS)
        }

        return trimmed
    }

    private val BASE64_TRANSACTION = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    private val BASE58_PUBLIC_KEY = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")
}

enum class SolanaRpcCommitment(val value: String) {
    Processed("processed"),
    Confirmed("confirmed"),
    Finalized("finalized")
}

data class SolanaBroadcastOptions(
    val maxRetries: Int? = null,
    val preflightCommitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val skipPreflight: Boolean = false
)

data class SolanaSimulationOptions(
    val commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val replaceRecentBlockhash: Boolean = true,
    val sigVerify: Boolean = false
)

data class SolanaRpcContext(
    val apiVersion: String? = null,
    val slot: Long
)

data class SolanaLatestBlockhash(
    val blockhash: String,
    val lastValidBlockHeight: Long
)

data class SolanaLatestBlockhashResponse(
    val context: SolanaRpcContext,
    val value: SolanaLatestBlockhash
)

data class SolanaFeeForMessageResponse(
    val context: SolanaRpcContext,
    val value: Long?
)

data class SolanaSimulationValue(
    val errorJson: String?,
    val logs: List<String>?,
    val replacementBlockhash: SolanaLatestBlockhash?,
    val unitsConsumed: Long?
)

data class SolanaSimulationResponse(
    val context: SolanaRpcContext,
    val value: SolanaSimulationValue
)

data class SolanaJsonRpcErrorPayload(
    val code: Int,
    val message: String,
    val dataJson: String? = null
)

class SolanaRpcException(
    val code: Code,
    val rpcError: SolanaJsonRpcErrorPayload? = null
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_RPC_URL,
        INVALID_ACCOUNT_ADDRESS,
        INVALID_TRANSACTION,
        INVALID_MAX_RETRIES,
        INVALID_DATA_LENGTH,
        INVALID_SIMULATION_OPTIONS,
        INVALID_RPC_RESPONSE,
        RPC_ERROR,
        INVALID_BLOCKHASH_RESPONSE,
        INVALID_FEE_RESPONSE,
        INVALID_RENT_RESPONSE,
        INVALID_ACCOUNT_INFO_RESPONSE,
        INVALID_SIMULATION_RESPONSE,
        INVALID_SIGNATURE_RESPONSE
    }
}
