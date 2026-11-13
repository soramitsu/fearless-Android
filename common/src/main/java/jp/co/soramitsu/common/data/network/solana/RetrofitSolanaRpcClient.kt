package jp.co.soramitsu.common.data.network.solana

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.data.network.WalletProviderPool
import jp.co.soramitsu.common.data.network.WalletProviderUnavailableException
import jp.co.soramitsu.common.utils.SolanaTransactionSigner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLHandshakeException
import java.math.BigInteger
import java.net.MalformedURLException
import java.net.URL

class RetrofitSolanaRpcClient(
    private val api: SolanaRpcApi,
    private val defaultRpcUrl: String = UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL,
    private val fallbackRpcUrls: List<String> = if (defaultRpcUrl == UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL) {
        SolanaRpcEndpoints.PUBLIC_MAINNET - UniversalWalletRegistry.SOLANA_MAINNET_RPC_URL
    } else {
        emptyList()
    },
    private val providers: WalletProviderPool = WalletProviderPool(),
    private val expectedGenesisHash: String = UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH,
    private val pacer: SolanaRequestPacer = SolanaRequestPacer.Shared
) : SolanaRpcClient {
    private val nextId = AtomicLong(1)
    private val configuredEndpoints = (listOf(defaultRpcUrl) + fallbackRpcUrls).map(SolanaRpcRoutes::normalizeRpcUrl).distinct()

    override fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient {
        require(rpcUrls.isNotEmpty() && rpcUrls.size <= 8)
        val configured = rpcUrls.map(SolanaRpcRoutes::normalizeRpcUrl).distinct()
        // Sticky successes belong to one ordered configuration. A new selected first peer
        // must not inherit a former configuration's preferred fallback or cooldown.
        val routing = if (configured == configuredEndpoints) providers else WalletProviderPool()
        return RetrofitSolanaRpcClient(api, rpcUrls.first(), rpcUrls.drop(1), routing, expectedGenesisHash, pacer)
    }

    override fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient {
        SolanaPublicKeyCodec.decode(expectedGenesisHash)
        return if (this.expectedGenesisHash == expectedGenesisHash) this else {
            // Routing cooldowns and preferred endpoints belong to one cluster.
            RetrofitSolanaRpcClient(api, defaultRpcUrl, fallbackRpcUrls, expectedGenesisHash = expectedGenesisHash, pacer = pacer)
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
        "getSignatureStatuses", signatureStatusParams(signature), rpcUrl, ::normalizeSignatureStatus
    )

    /**
     * Asks every endpoint at once and judges each answer on its own: a node that lags, or a load balancer of mixed
     * freshness behind one URL, can find no trace of a transaction that has already landed. One endpoint proves
     * expiry only when its finalized progress (slot and block height from one `getEpochInfo` answer) is past
     * [lastValidBlockHeight] and its signature lookup, asked afterwards, comes from a slot at least that recent and
     * finds nothing. A confirmed status on any endpoint wins at once, and any other trace keeps the payment pending.
     * Without a trace, Expired stands only when every endpoint that answered proved it; with at least
     * [LAG_CAP_MIN_ANSWERS] answers, an endpoint whose finalized slot is more than [MAX_FINALIZED_LAG_SLOTS] behind
     * the (lower) median of all answers abstains, so one stalled node cannot hold a payment forever. Each endpoint
     * has [OUTCOME_ENDPOINT_TIMEOUT_MILLIS] to answer; a failure or timeout does not count. Expired reports how many
     * distinct hosts proved it, so a lone proof, or two spellings of one node, can be treated as unverified. Fails
     * only when no endpoint answered.
     */
    override suspend fun signatureOutcome(signature: String, lastValidBlockHeight: Long, rpcUrl: String?): SolanaSignatureOutcome {
        require(lastValidBlockHeight >= 0)
        val params = signatureStatusParams(signature)
        val endpoints = endpointsFor(rpcUrl)
        val answers = coroutineScope {
            val arrivals = Channel<Result<SignatureObservation>>(endpoints.size)
            val lookups = endpoints.map { endpoint -> launch { arrivals.send(observeSignature(endpoint, params)) } }
            val received = mutableListOf<Result<SignatureObservation>>()
            while (received.size < endpoints.size) {
                val next = arrivals.receive().also { received += it }
                // A confirmed status settles the payment; slower endpoints need not finish.
                if (next.getOrNull()?.status?.isConfirmed == true) {
                    lookups.forEach { it.cancel() }
                    break
                }
            }
            received
        }
        val observations = answers.mapNotNull { it.getOrNull() }
        if (observations.isEmpty()) throw answers.firstNotNullOfOrNull { it.exceptionOrNull() } ?: WalletProviderUnavailableException()
        val trace = observations.firstOrNull { it.status?.isConfirmed == true } ?: observations.firstOrNull { it.status != null }
        if (trace != null) return SolanaSignatureOutcome.Seen(requireNotNull(trace.status))
        val voters = if (observations.size < LAG_CAP_MIN_ANSWERS) observations else {
            val median = observations.map { it.finalized.slot }.sorted()[(observations.size - 1) / 2]
            observations.filter { median - it.finalized.slot <= MAX_FINALIZED_LAG_SLOTS }
        }
        if (!voters.all { it.provesExpiry(lastValidBlockHeight) }) return SolanaSignatureOutcome.Unknown
        // Two spellings of one node are one proof: count hosts, not URLs.
        return SolanaSignatureOutcome.Expired(voters.mapTo(mutableSetOf()) { SolanaRpcEndpoints.hostOf(it.endpoint) ?: it.endpoint }.size)
    }

    /** One endpoint's own view within its time bound; a failure or timeout means that endpoint did not answer. */
    private suspend fun observeSignature(endpoint: String, params: JsonArray): Result<SignatureObservation> = try {
        withTimeoutOrNull(OUTCOME_ENDPOINT_TIMEOUT_MILLIS) {
            verifyGenesis(endpoint)
            // Asked in this order, so the lookup is at least as recent as the progress it is judged against.
            val finalized = post(endpoint, "getEpochInfo", jsonArray(jsonObject("commitment" to SolanaRpcCommitment.Finalized.value)),
                ::normalizeFinalizedProgress)
            val (lookupSlot, status) = post(endpoint, "getSignatureStatuses", params, ::normalizeSignatureStatusWithSlot)
            SignatureObservation(endpoint, finalized, lookupSlot, status)
        }?.let { Result.success(it) } ?: Result.failure(WalletProviderUnavailableException())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private data class FinalizedProgress(val slot: Long, val blockHeight: Long)

    private data class SignatureObservation(val endpoint: String, val finalized: FinalizedProgress, val lookupSlot: Long,
        val status: SolanaRpcSignatureStatus?) {
        /** No trace, in a lookup at least as recent as finalized progress already past the payment's last valid height. */
        fun provesExpiry(lastValidBlockHeight: Long): Boolean =
            status == null && finalized.blockHeight > lastValidBlockHeight && lookupSlot >= finalized.slot
    }

    private fun normalizeFinalizedProgress(value: JsonElement): FinalizedProgress {
        val code = SolanaRpcException.Code.INVALID_EPOCH_INFO_RESPONSE
        val info = value.asObjectOrThrow(code)
        return FinalizedProgress(slot = requireUnsignedLong(info["absoluteSlot"], code), blockHeight = requireUnsignedLong(info["blockHeight"], code))
    }

    private fun normalizeSignatureStatusWithSlot(result: JsonElement): Pair<Long, SolanaRpcSignatureStatus?> {
        val code = SolanaRpcException.Code.INVALID_SIGNATURE_STATUS_RESPONSE
        val slot = normalizeContext(result.asObjectOrThrow(code)["context"], code).slot
        return slot to normalizeSignatureStatus(result)
    }

    private fun signatureStatusParams(signature: String) =
        jsonArray(jsonArray(normalizeSignature(JsonPrimitive(signature))), jsonObject("searchTransactionHistory" to true))

    private fun normalizeSignatureStatus(result: JsonElement): SolanaRpcSignatureStatus? {
        val code = SolanaRpcException.Code.INVALID_SIGNATURE_STATUS_RESPONSE
        val response = result.asObjectOrThrow(code)
        normalizeContext(response["context"], code)
        val values = response["value"]?.takeIf { it.isJsonArray && it.asJsonArray.size() == 1 }?.asJsonArray ?: throw SolanaRpcException(code)
        return if (values[0] is JsonNull) null else {
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

    /**
     * Offers the exact signed bytes to each endpoint once until one accepts them, and on failure reports what that
     * proves (see [SolanaBroadcastException]). An endpoint whose cluster cannot be verified is never offered them.
     */
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
        val transaction = SolanaRpcRoutes.normalizeTransactionBase64(transactionBase64)
        var mayHaveReachedNode = false
        var judgedByNode = false
        fun outcome() = when {
            mayHaveReachedNode -> SolanaBroadcastException.Outcome.AMBIGUOUS
            judgedByNode -> SolanaBroadcastException.Outcome.REFUSED
            else -> SolanaBroadcastException.Outcome.NOT_SENT
        }
        return try {
            providers.execute(endpointsFor(rpcUrl), isTransient = { it is BroadcastAttemptFailed && it.tryNext }, sticky = ::isSticky) { endpoint ->
                val id = nextId.getAndIncrement()
                try {
                    verifyGenesis(endpoint)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw BroadcastAttemptFailed(error, tryNext = true)
                }
                val optionBody = jsonObject(
                    "encoding" to "base64",
                    "preflightCommitment" to options.preflightCommitment.value,
                    // After an attempt that may have been relayed, a lagging node's preflight could refuse a
                    // transaction that is already on its way: later attempts only relay the same bytes.
                    "skipPreflight" to (options.skipPreflight || mayHaveReachedNode)
                )
                if (normalizedRetries != null) {
                    optionBody.addProperty("maxRetries", normalizedRetries)
                }
                val body = jsonObject("id" to id, "jsonrpc" to "2.0", "method" to BROADCAST_METHOD)
                    .apply { add("params", jsonArray(transaction, optionBody)) }
                val response = try {
                    postJson(endpoint, body)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (!refusedBeforeSending(error)) mayHaveReachedNode = true
                    throw BroadcastAttemptFailed(error, tryNext = true)
                }
                try {
                    normalizeResponse(response, id, ::normalizeSignature)
                } catch (error: SolanaRpcException) {
                    val rpcError = error.rpcError.takeIf { error.code == SolanaRpcException.Code.RPC_ERROR }
                    when {
                        // The node already has these exact bytes: the transaction is out there under its own id.
                        rpcError != null && isAlreadyProcessed(rpcError) -> runCatching { SolanaTransactionSigner.firstSignatureBase58(transaction) }
                            .getOrElse { mayHaveReachedNode = true; throw BroadcastAttemptFailed(error, tryNext = true) }
                        // A node that is behind, or offers no sendTransaction, refuses before relaying; another may take it.
                        rpcError != null && rpcError.code in PRE_RELAY_RETRYABLE_RPC_CODES -> throw BroadcastAttemptFailed(error, tryNext = true)
                        // The node judged the transaction itself (preflight, signature, undecodable): that verdict is final.
                        rpcError != null && rpcError.code in PRE_RELAY_VERDICT_RPC_CODES -> {
                            judgedByNode = true
                            throw BroadcastAttemptFailed(error, tryNext = false)
                        }
                        else -> {
                            // Any other JSON-RPC error (a gateway's quota, auth or internal error) or an unreadable reply
                            // proves nothing: the transaction may have been relayed before the failure.
                            mayHaveReachedNode = true
                            throw BroadcastAttemptFailed(error, tryNext = true)
                        }
                    }
                }
            }
        } catch (error: BroadcastAttemptFailed) {
            throw SolanaBroadcastException(outcome(), error.cause)
        } catch (error: WalletProviderUnavailableException) {
            throw SolanaBroadcastException(outcome(), error)
        }
    }

    /** An answer given before the request reached a node: an HTTP refusal or a connection that was never made. */
    private fun refusedBeforeSending(error: Throwable): Boolean = when (error) {
        is HttpException -> error.code() in HTTP_CLIENT_ERRORS && error.code() != HTTP_REQUEST_TIMEOUT && error.code() != HTTP_TOO_MANY_REQUESTS
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SSLHandshakeException -> true
        else -> false
    }

    private fun isAlreadyProcessed(error: SolanaJsonRpcErrorPayload): Boolean =
        error.message.contains("already been processed", ignoreCase = true) || error.dataJson?.contains("\"AlreadyProcessed\"") == true

    private class BroadcastAttemptFailed(cause: Throwable, val tryNext: Boolean) : Exception(cause)

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
            normalizeResponse(postJson(endpoint, body), id, normalize)
        }
    }

    /** Reads only: a broadcast has its own classified loop in [sendRawTransaction]. */
    private suspend fun <T> withVerifiedEndpoint(rpcUrl: String?, action: suspend (String) -> T): T =
        providers.execute(endpointsFor(rpcUrl), isTransient = ::isEndpointFailure, sticky = ::isSticky) { endpoint ->
            verifyGenesis(endpoint)
            action(endpoint)
        }

    private fun endpointsFor(rpcUrl: String?): List<String> {
        val requested = rpcUrl?.let(SolanaRpcRoutes::normalizeRpcUrl)
        return if (requested == null) configuredEndpoints else if (requested in configuredEndpoints) {
            (listOf(requested) + configuredEndpoints).distinct()
        } else {
            // Never infer the cluster of an arbitrary override and silently fall back to mainnet.
            listOf(requested)
        }
    }

    /**
     * Rechecks the exact endpoint before every read or broadcast. A successful earlier request must not
     * authorize a provider whose cluster configuration has changed.
     */
    private suspend fun verifyGenesis(endpoint: String) {
        val genesis = post(endpoint, "getGenesisHash", JsonArray()) {
            normalizeBase58(it, SolanaRpcException.Code.INVALID_GENESIS_RESPONSE)
        }
        if (genesis != expectedGenesisHash) throw SolanaRpcException(SolanaRpcException.Code.GENESIS_MISMATCH)
    }

    /** One request to one already chosen endpoint, with its own id. */
    private suspend fun <T> post(endpoint: String, method: String, params: JsonArray, normalize: (JsonElement) -> T): T {
        val id = nextId.getAndIncrement()
        val body = jsonObject("id" to id, "jsonrpc" to "2.0", "method" to method).apply { add("params", params) }
        return normalizeResponse(postJson(endpoint, body), id, normalize)
    }

    /** Every request goes through the shared pacer, so endpoints that refuse bursts see spaced requests. */
    private suspend fun postJson(endpoint: String, body: JsonObject): JsonObject = pacer.paced(endpoint) { api.postJsonRpc(endpoint, body) }

    /** A rate-limited endpoint may answer, but never becomes the one every later call tries first. */
    private fun isSticky(endpoint: String): Boolean = SolanaRpcEndpoints.requestSpacingMillis(endpoint) == 0L

    /**
     * Whether another endpoint of the same cluster should answer a read instead. Transport errors, 408, 429 and
     * 5xx are handled by the pool. Any other HTTP 4xx is the endpoint refusing this client before a node acts on
     * the call (a key, a paid plan or an allowed method is required; publicnode answers 403 to token-account
     * reads). Reads have no side effects, so an unreadable envelope or a JSON-RPC error also moves to the next
     * endpoint; a well-formed answer with an invalid value still fails closed.
     */
    private fun isEndpointFailure(error: Throwable): Boolean = when {
        error is HttpException -> error.code() in HTTP_CLIENT_ERRORS
        error is SolanaRpcException && error.code in CLUSTER_IDENTITY_FAILURES -> true
        error is SolanaRpcException && error.rpcError?.code in NODE_UNAVAILABLE_RPC_CODES -> true
        error is JsonParseException -> true
        else -> error is SolanaRpcException && error.code in UNREADABLE_ANSWERS
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
        const val BROADCAST_METHOD = "sendTransaction"
        // Each endpoint's part of an outcome check is bounded on its own, so a hung endpoint delays it by at most this.
        const val OUTCOME_ENDPOINT_TIMEOUT_MILLIS = 15_000L
        // From this many answers on, an endpoint far behind the others' finalized slot abstains from expiry votes.
        const val LAG_CAP_MIN_ANSWERS = 3
        const val MAX_FINALIZED_LAG_SLOTS = 1_000L
        val HTTP_CLIENT_ERRORS = 400..499
        const val HTTP_REQUEST_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        val CLUSTER_IDENTITY_FAILURES = setOf(SolanaRpcException.Code.GENESIS_MISMATCH, SolanaRpcException.Code.INVALID_GENESIS_RESPONSE)
        // Node is unhealthy or behind (-32004/-32005), an index or slot it cannot serve yet (-32010/-32014/-32016),
        // or a method this endpoint does not offer (-32601): another node of the cluster may answer.
        val NODE_UNAVAILABLE_RPC_CODES = setOf(-32004, -32005, -32010, -32014, -32016, -32601)
        val UNREADABLE_ANSWERS = setOf(SolanaRpcException.Code.RPC_ERROR, SolanaRpcException.Code.INVALID_RPC_RESPONSE)
        // JSON-RPC errors a node gives a broadcast only before relaying it. Verdicts on the transaction: preflight
        // failure (-32002), signature failure (-32003), invalid request or params (-32600/-32602), undecodable (-32700).
        val PRE_RELAY_VERDICT_RPC_CODES = setOf(-32002, -32003, -32600, -32602, -32700)
        // The node could not take it at all: method not offered (-32601), unhealthy or behind (-32004/-32005/-32014/-32016).
        val PRE_RELAY_RETRYABLE_RPC_CODES = setOf(-32601, -32004, -32005, -32014, -32016)
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

