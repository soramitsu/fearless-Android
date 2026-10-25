package jp.co.soramitsu.common.data.network.solana

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.data.network.WalletProviderPool
import java.util.concurrent.atomic.AtomicLong
import java.math.BigInteger
import java.net.MalformedURLException
import java.net.URL

class RetrofitSolanaRpcClient(
    private val api: SolanaRpcApi,
    private val defaultRpcUrl: String = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL,
    private val fallbackRpcUrls: List<String> = if (defaultRpcUrl == UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL) {
        listOf("https://solana-rpc.publicnode.com")
    } else {
        emptyList()
    },
    private val providers: WalletProviderPool = WalletProviderPool(),
    private val expectedGenesisHash: String = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH
) : SolanaRpcClient {
    private val nextId = AtomicLong(1)
    private val configuredEndpoints = (listOf(defaultRpcUrl) + fallbackRpcUrls).map(SolanaRpcRoutes::normalizeRpcUrl).distinct()

    override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
        require(rpcUrls.isNotEmpty() && rpcUrls.size <= 8)
        val configured = rpcUrls.map(SolanaRpcRoutes::normalizeRpcUrl).distinct()
        // Sticky successes belong to one ordered configuration. A new selected first peer
        // must not inherit a former configuration's preferred fallback or cooldown.
        val routing = if (configured == configuredEndpoints) providers else WalletProviderPool()
        return RetrofitSolanaRpcClient(api, rpcUrls.first(), rpcUrls.drop(1), routing, expectedGenesisHash)
    }

    override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient {
        SolanaPublicKeyCodec.decode(expectedGenesisHash)
        return if (this.expectedGenesisHash == expectedGenesisHash) this else {
            // Routing cooldowns and preferred endpoints belong to one cluster.
            RetrofitSolanaRpcClient(api, defaultRpcUrl, fallbackRpcUrls, expectedGenesisHash = expectedGenesisHash)
        }
    }

    /** Verifies only this client's endpoints, without requesting wallet data. */
    suspend fun verifyCluster() = withVerifiedEndpoint(null) { }

    init {
        SolanaRpcRoutes.normalizeRpcUrl(defaultRpcUrl)
        SolanaPublicKeyCodec.decode(expectedGenesisHash)
    }

    override suspend fun balance(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): Long = request(
        "getBalance", jsonArray(SolanaRpcRoutes.normalizePublicKey(address), jsonObject("commitment" to commitment.value)), rpcUrl
    ) { result ->
        val response = result.asObjectOrThrow(SolanaRpcException.Code.INVALID_BALANCE_RESPONSE)
        normalizeContext(response["context"], SolanaRpcException.Code.INVALID_BALANCE_RESPONSE)
        requireUnsignedLong(response["value"], SolanaRpcException.Code.INVALID_BALANCE_RESPONSE)
    }

    override suspend fun accountInfo(address: String, commitment: SolanaRpcCommitment, rpcUrl: String?): SolanaRpcAccountInfo? = request(
        "getAccountInfo", jsonArray(SolanaRpcRoutes.normalizePublicKey(address), jsonObject(
            "commitment" to commitment.value, "encoding" to "base64", "dataSlice" to jsonObject("offset" to 0, "length" to 165)
        )), rpcUrl
    ) { result ->
        val response = result.asObjectOrThrow(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        normalizeContext(response["context"], SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        val value = response["value"] ?: throw SolanaRpcException(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE)
        parseAccountFields(SolanaRpcException.Code.INVALID_ACCOUNT_INFO_RESPONSE) { SolanaRpcAccountParser.account(value) }
    }

    override suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment,
        rpcUrl: String?): List<SolanaRpcTokenAccount> {
        val wallet = SolanaRpcRoutes.normalizePublicKey(address)
        val program = SolanaRpcRoutes.normalizePublicKey(programAddress)
        require(program in setOf(SolanaTransferTransactionBuilder.SPL_TOKEN_PROGRAM_ADDRESS, SolanaTransferTransactionBuilder.TOKEN_2022_PROGRAM_ADDRESS))
        return request("getTokenAccountsByOwner", jsonArray(wallet, jsonObject("programId" to program),
            jsonObject("commitment" to commitment.value, "encoding" to "jsonParsed")), rpcUrl) { result ->
            val response = result.asObjectOrThrow(SolanaRpcException.Code.INVALID_TOKEN_ACCOUNTS_RESPONSE)
            normalizeContext(response["context"], SolanaRpcException.Code.INVALID_TOKEN_ACCOUNTS_RESPONSE)
            val value = response["value"] ?: throw SolanaRpcException(SolanaRpcException.Code.INVALID_TOKEN_ACCOUNTS_RESPONSE)
            parseAccountFields(SolanaRpcException.Code.INVALID_TOKEN_ACCOUNTS_RESPONSE) {
                SolanaRpcAccountParser.tokenAccounts(value, wallet, program)
            }
        }
    }

    override suspend fun blockHeight(commitment: SolanaRpcCommitment, rpcUrl: String?): Long = request(
        "getBlockHeight", jsonArray(jsonObject("commitment" to commitment.value)), rpcUrl
    ) { requireUnsignedLong(it, SolanaRpcException.Code.INVALID_BLOCK_HEIGHT_RESPONSE) }

    override suspend fun signatureStatus(signature: String, rpcUrl: String?): SolanaRpcSignatureStatus? = request(
        "getSignatureStatuses", jsonArray(jsonArray(normalizeSignature(JsonPrimitive(signature))), jsonObject("searchTransactionHistory" to true)), rpcUrl
    ) { result ->
        val code = SolanaRpcException.Code.INVALID_SIGNATURE_STATUS_RESPONSE
        val response = result.asObjectOrThrow(code)
        normalizeContext(response["context"], code)
        val values = response["value"]?.takeIf { it.isJsonArray && it.asJsonArray.size() == 1 }?.asJsonArray ?: throw SolanaRpcException(code)
        if (values[0] is JsonNull) null else {
            val value = values[0].asObjectOrThrow(code)
            if (!value.has("err") || !value.has("confirmations")) throw SolanaRpcException(code)
            val confirmations = value["confirmations"].takeUnless { it is JsonNull }?.let { requireUnsignedLong(it, code) }
            val status = value["confirmationStatus"]?.takeUnless { it is JsonNull }?.let {
                if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString || it.asString !in setOf("processed", "confirmed", "finalized")) {
                    throw SolanaRpcException(code)
                }
                it.asString
            }
            SolanaRpcSignatureStatus(requireUnsignedLong(value["slot"], code), status, confirmations,
                value["err"].takeUnless { it is JsonNull }?.let(GSON::toJson))
        }
    }

    override suspend fun recentPrioritizationFees(rpcUrl: String?): List<Long> = request(
        "getRecentPrioritizationFees", jsonArray(), rpcUrl
    ) { result ->
        val code = SolanaRpcException.Code.INVALID_PRIORITY_FEE_RESPONSE
        if (!result.isJsonArray || result.asJsonArray.size() > 256) throw SolanaRpcException(code)
        result.asJsonArray.map { item ->
            val row = item.asObjectOrThrow(code)
            requireUnsignedLong(row["slot"], code)
            requireUnsignedLong(row["prioritizationFee"], code)
        }
    }

    private fun <T> parseAccountFields(code: SolanaRpcException.Code, parse: () -> T): T = try {
        parse()
    } catch (_: IllegalArgumentException) {
        throw SolanaRpcException(code)
    } catch (_: IllegalStateException) {
        throw SolanaRpcException(code)
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
        val id = nextId.getAndIncrement()
        val body = jsonObject(
            "id" to id,
            "jsonrpc" to "2.0",
            "method" to method
        )
        body.add("params", params)

        return withVerifiedEndpoint(rpcUrl) { endpoint ->
            normalizeResponse(api.postJsonRpc(endpoint, body), id, normalize)
        }
    }

    private suspend fun <T> withVerifiedEndpoint(rpcUrl: String?, action: suspend (String) -> T): T {
        val requested = rpcUrl?.let(SolanaRpcRoutes::normalizeRpcUrl)
        val endpoints = if (requested == null) configuredEndpoints else if (requested in configuredEndpoints) {
            (listOf(requested) + configuredEndpoints).distinct()
        } else {
            // Never infer the cluster of an arbitrary override and silently fall back to mainnet.
            listOf(requested)
        }
        return providers.execute(endpoints, isTransient = { error ->
            error is SolanaRpcException && (error.rpcError?.code in setOf(-32004, -32005) ||
                error.code in setOf(SolanaRpcException.Code.GENESIS_MISMATCH, SolanaRpcException.Code.INVALID_GENESIS_RESPONSE))
        }) { endpoint ->
            // Recheck the exact endpoint before every read or broadcast. A successful earlier
            // request must not authorize a provider whose cluster configuration has changed.
            val genesisId = nextId.getAndIncrement()
            val genesisRequest = jsonObject("id" to genesisId, "jsonrpc" to "2.0", "method" to "getGenesisHash")
                .apply { add("params", JsonArray()) }
            val genesis = normalizeResponse(api.postJsonRpc(endpoint, genesisRequest), genesisId) {
                normalizeBase58(it, SolanaRpcException.Code.INVALID_GENESIS_RESPONSE)
            }
            if (genesis != expectedGenesisHash) throw SolanaRpcException(SolanaRpcException.Code.GENESIS_MISMATCH)
            action(endpoint)
        }
    }

    private fun <T> normalizeResponse(response: JsonObject, id: Long, normalize: (JsonElement) -> T): T {
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

        if (runCatching { SolanaPublicKeyCodec.decodeFixedSize(value.asString, 64) }.isFailure) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_SIGNATURE_RESPONSE)
        }
        return value.asString
    }

    private fun normalizeBase58(value: JsonElement?, code: SolanaRpcException.Code): String {
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString || !BASE58_32_TO_64.matches(value.asString)) {
            throw SolanaRpcException(code)
        }

        if (runCatching { SolanaPublicKeyCodec.decode(value.asString) }.isFailure) throw SolanaRpcException(code)
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

        val parsed = BigInteger(text)
        if (parsed < INT_MIN || parsed > INT_MAX) {
            return null
        }

        return parsed.toInt()
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
        val INT_MIN = BigInteger.valueOf(Int.MIN_VALUE.toLong())
        val INT_MAX = BigInteger.valueOf(Int.MAX_VALUE.toLong())
        val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
        val GSON = Gson()
        val UNSIGNED_INTEGER = Regex("^(0|[1-9][0-9]*)$")
        val SIGNED_INTEGER = Regex("^-?(0|[1-9][0-9]*)$")
        val BASE58_32_TO_64 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,64}$")
        val BASE58_SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,128}$")
    }
}

