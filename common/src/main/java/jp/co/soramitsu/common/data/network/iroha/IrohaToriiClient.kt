package jp.co.soramitsu.common.data.network.iroha

import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
import retrofit2.HttpException
import retrofit2.Response
import java.util.Base64

interface IrohaToriiClient {
    suspend fun health(baseUrl: String? = null): String

    suspend fun accounts(
        baseUrl: String? = null,
        limit: Int? = null,
        offset: Long? = null,
        countMode: IrohaToriiRoutes.CountMode? = null
    ): IrohaAccountListResponse

    suspend fun account(
        accountId: String,
        baseUrl: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountListItem

    suspend fun accountAssets(
        accountId: String,
        baseUrl: String? = null,
        limit: Int? = null,
        offset: Long? = null,
        countMode: IrohaToriiRoutes.CountMode? = null,
        asset: String? = null,
        scope: String? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAccountAssetListResponse

    suspend fun assetDefinitions(
        baseUrl: String? = null,
        limit: Int? = null,
        offset: Long? = null,
        countMode: IrohaToriiRoutes.CountMode? = null,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
    ): IrohaAssetDefinitionListResponse

    suspend fun submitTransactionAndWait(
        noritoBytes: ByteArray,
        expectedHash: String,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String? = null
    ): IrohaSubmitAndWaitOutcome

    suspend fun transactionStatus(
        hash: String,
        baseUrl: String? = null,
        scope: IrohaToriiRoutes.TransactionStatusScope = IrohaToriiRoutes.TransactionStatusScope.Auto
    ): IrohaPipelineTransactionStatusResponse

    suspend fun mcpCapabilities(
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String? = null
    ): Map<String, Any?>

    suspend fun mcpJsonRpc(
        request: IrohaMcpJsonRpcRequest,
        network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira,
        baseUrl: String? = null
    ): IrohaMcpJsonRpcResponse
}

class RetrofitIrohaToriiClient(
    private val api: IrohaToriiApi,
    private val defaultNetwork: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira
) : IrohaToriiClient {

    override suspend fun health(baseUrl: String?): String {
        return api.health(IrohaToriiRoutes.healthUrl(resolveBaseUrl(baseUrl)))
    }

    override suspend fun accounts(
        baseUrl: String?,
        limit: Int?,
        offset: Long?,
        countMode: IrohaToriiRoutes.CountMode?
    ): IrohaAccountListResponse {
        return completeRoutedBody(api.accounts(
            IrohaToriiRoutes.accountsUrl(
                baseUrl = resolveBaseUrl(baseUrl),
                limit = limit,
                offset = offset,
                countMode = countMode
            )
        ))
    }

    override suspend fun account(
        accountId: String,
        baseUrl: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountListItem {
        return completeRoutedBody(api.account(
            IrohaToriiRoutes.accountUrl(
                normalizeWalletAccountId(accountId, network),
                resolveBaseUrl(baseUrl, network)
            )
        ))
    }

    override suspend fun accountAssets(
        accountId: String,
        baseUrl: String?,
        limit: Int?,
        offset: Long?,
        countMode: IrohaToriiRoutes.CountMode?,
        asset: String?,
        scope: String?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAccountAssetListResponse {
        return completeRoutedBody(api.accountAssets(
            IrohaToriiRoutes.accountAssetsUrl(
                accountId = normalizeWalletAccountId(accountId, network),
                baseUrl = resolveBaseUrl(baseUrl, network),
                limit = limit,
                offset = offset,
                countMode = countMode,
                asset = asset,
                scope = scope
            )
        ))
    }

    override suspend fun assetDefinitions(
        baseUrl: String?,
        limit: Int?,
        offset: Long?,
        countMode: IrohaToriiRoutes.CountMode?,
        network: UniversalWalletRegistry.IrohaNetwork
    ): IrohaAssetDefinitionListResponse {
        return completeRoutedBody(
            api.assetDefinitions(
                IrohaToriiRoutes.assetDefinitionsUrl(
                    baseUrl = resolveBaseUrl(baseUrl, network),
                    limit = limit,
                    offset = offset,
                    countMode = countMode
                )
            )
        )
    }

    override suspend fun submitTransactionAndWait(
        noritoBytes: ByteArray,
        expectedHash: String,
        timeoutMillis: Long,
        pollIntervalMillis: Long,
        network: UniversalWalletRegistry.IrohaNetwork,
        baseUrl: String?
    ): IrohaSubmitAndWaitOutcome {
        val canonicalExpectedHash = canonicalTransactionHash(expectedHash)
            ?: throw IrohaSubmitAndWaitException(
                IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE,
                "Expected Iroha transaction hash is not canonical"
            )
        require(noritoBytes.isNotEmpty()) { "Signed Iroha transaction must not be empty" }
        require(timeoutMillis in 1..MAX_SUBMIT_WAIT_TIMEOUT_MILLIS) {
            "Iroha submit-and-wait timeout is out of range"
        }
        require(pollIntervalMillis in MIN_SUBMIT_WAIT_POLL_MILLIS..MAX_SUBMIT_WAIT_POLL_MILLIS) {
            "Iroha submit-and-wait poll interval is out of range"
        }

        val response = mcpJsonRpc(
            request = IrohaMcpJsonRpcRequest(
                id = "submit-${canonicalExpectedHash.take(16)}",
                method = "tools/call",
                params = mapOf(
                    "name" to SUBMIT_AND_WAIT_TOOL,
                    "arguments" to mapOf(
                        "accept" to JSON_MEDIA_TYPE,
                        "body_base64" to Base64.getEncoder().encodeToString(noritoBytes),
                        "hash" to canonicalExpectedHash,
                        "poll_interval_ms" to pollIntervalMillis,
                        "status_accept" to JSON_MEDIA_TYPE,
                        "terminal_statuses" to listOf(APPLIED_STATUS),
                        "timeout_ms" to timeoutMillis
                    )
                )
            ),
            network = network,
            baseUrl = baseUrl
        )

        response.error?.let(::throwSubmitAndWaitRpcError)
        return parseSubmitAndWaitOutcome(response.result, canonicalExpectedHash)
    }

    override suspend fun transactionStatus(
        hash: String,
        baseUrl: String?,
        scope: IrohaToriiRoutes.TransactionStatusScope
    ): IrohaPipelineTransactionStatusResponse {
        return completeRoutedBody(api.transactionStatus(
            IrohaToriiRoutes.transactionStatusUrl(
                hash = hash,
                baseUrl = resolveBaseUrl(baseUrl),
                scope = scope
            )
        ))
    }

    override suspend fun mcpCapabilities(
        network: UniversalWalletRegistry.IrohaNetwork,
        baseUrl: String?
    ): Map<String, Any?> {
        return completeMcpBody(
            api.mcpCapabilities(IrohaToriiRoutes.mcpUrl(network, resolveBaseUrl(baseUrl, network)))
        )
    }

    override suspend fun mcpJsonRpc(
        request: IrohaMcpJsonRpcRequest,
        network: UniversalWalletRegistry.IrohaNetwork,
        baseUrl: String?
    ): IrohaMcpJsonRpcResponse {
        val response = completeMcpBody(
            api.mcpJsonRpc(
                IrohaToriiRoutes.mcpUrl(network, resolveBaseUrl(baseUrl, network)),
                request
            )
        )
        val hasExactlyOnePayload = (response.result == null) != (response.error == null)
        if (response.jsonrpc != "2.0" || response.id != request.id || !hasExactlyOnePayload) {
            throw IrohaToriiResponseException("Torii returned an unbound JSON-RPC response")
        }

        return response
    }

    private fun normalizeWalletAccountId(
        accountId: String,
        network: UniversalWalletRegistry.IrohaNetwork
    ): String {
        return IrohaAddressCodec.parse(accountId, network.chainDiscriminant).i105
    }

    private fun resolveBaseUrl(
        baseUrl: String?,
        network: UniversalWalletRegistry.IrohaNetwork = defaultNetwork
    ): String {
        return baseUrl ?: IrohaToriiRoutes.requireToriiBaseUrl(network)
    }

    private fun <T> completeRoutedBody(response: Response<T>): T {
        if (!response.isSuccessful) throw HttpException(response)

        validateFanoutHeaders { response.headers().values(it).singleOrNull() }
        requireJsonContentType(response)

        return requireSuccessfulBody(response)
    }

    private fun <T> completeMcpBody(response: Response<T>): T {
        if (!response.isSuccessful) throw HttpException(response)

        requireJsonContentType(response)

        return requireSuccessfulBody(response)
    }

    private fun requireJsonContentType(response: Response<*>) {
        val contentType = response.headers().values("Content-Type").singleOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (contentType != JSON_MEDIA_TYPE) {
            throw IrohaToriiResponseException("Torii returned a non-JSON successful response")
        }
    }

    private fun <T> requireSuccessfulBody(response: Response<T>): T {
        return response.body()
            ?: throw IrohaToriiResponseException("Torii returned an empty successful response")
    }

    private fun validateFanoutHeaders(header: (String) -> String?) {
        val rawFanout = FANOUT_HEADER_NAMES.map(header)
        val values = rawFanout.map { value ->
            value?.takeIf(FANOUT_COUNT::matches)?.toIntOrNull()
                ?: throw IrohaToriiResponseException("Torii returned malformed fanout headers")
        }
        val fanout = IrohaToriiFanoutStatus(
            attempted = values[0],
            succeeded = values[1],
            failed = values[2],
            denied = values[3],
            unavailable = values[4],
            notFound = values[5]
        )
        if (!fanout.isValid) {
            throw IrohaToriiResponseException("Torii returned inconsistent fanout headers")
        }
        if (!fanout.isComplete) throw IrohaToriiDegradedException(fanout)
    }

    private fun validateStructuredRoute(route: Map<*, *>, label: String): Map<*, *> {
        val status = route.exactLong("status")
            ?: throw invalidSubmitAndWait("$label is missing an exact HTTP status", route)
        if (status !in 200L..299L) {
            throw invalidSubmitAndWait("$label returned HTTP $status", route)
        }
        val headers = route["headers"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("$label omitted fanout headers", route)
        val normalizedHeaders = linkedMapOf<String, String>()
        headers.entries.forEach { entry ->
            val name = entry.key as? String
                ?: throw invalidSubmitAndWait("$label returned a non-string header name", route)
            val value = entry.value as? String
                ?: throw invalidSubmitAndWait("$label returned a non-string header value", route)
            val normalizedName = name.lowercase()
            if (normalizedHeaders.put(normalizedName, value) != null) {
                throw invalidSubmitAndWait("$label returned duplicate case-insensitive headers", route)
            }
        }
        try {
            validateFanoutHeaders(normalizedHeaders::get)
        } catch (error: IrohaToriiDegradedException) {
            throw error
        } catch (error: IrohaToriiResponseException) {
            throw error
        }
        val contentType = (route["content_type"] as? String)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (contentType != JSON_MEDIA_TYPE) {
            throw invalidSubmitAndWait("$label returned a non-JSON body", route)
        }
        return route["body"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("$label is missing an object body", route)
    }

    private fun parseSubmitAndWaitOutcome(
        rawResult: Any?,
        expectedHash: String
    ): IrohaSubmitAndWaitOutcome {
        val toolResult = rawResult as? Map<*, *>
            ?: throw invalidSubmitAndWait("MCP submit-and-wait result is not an object", rawResult)
        if (toolResult["isError"] == true) {
            val errorEnvelope = toolResult["structuredContent"] as? Map<*, *>
            val message = errorEnvelope?.get("message") as? String
                ?: "MCP submit-and-wait returned a tool error"
            throw classifiedSubmitAndWaitError(message, errorEnvelope ?: toolResult)
        }
        val result = toolResult["structuredContent"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("MCP submit-and-wait omitted structuredContent", toolResult)
        val status = result.exactLong("status")
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted an exact status", result)
        if (status != HTTP_OK.toLong()) {
            throw invalidSubmitAndWait("Submit-and-wait returned unexpected status $status", result)
        }

        val hash = result.canonicalHash("hash")
        val transactionHash = result.canonicalHash("tx_hash")
        val terminalKind = result.pipelineStatus("terminal_kind")
        val terminalStatuses = (result["terminal_statuses"] as? List<*>)
            ?.map { value ->
                (value as? String)?.toPipelineStatus()
                    ?: throw invalidSubmitAndWait("Submit-and-wait returned an invalid terminal status", result)
            }
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted terminal_statuses", result)
        if (terminalStatuses != listOf(IrohaPipelineTransactionStatusKind.Applied)) {
            throw invalidSubmitAndWait("Submit-and-wait changed the requested terminal statuses", result)
        }
        val attempts = result.exactLong("attempts")?.takeIf { it > 0 }
            ?: throw invalidSubmitAndWait("Submit-and-wait returned invalid attempts", result)
        val elapsedMillis = result.exactLong("elapsed_ms")?.takeIf { it >= 0 }
            ?: throw invalidSubmitAndWait("Submit-and-wait returned invalid elapsed_ms", result)

        val submit = result["submit"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted submit receipt", result)
        val receiptBody = validateStructuredRoute(submit, "Submit route")
        val receiptPayload = receiptBody["payload"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("Submit receipt omitted payload", receiptBody)
        val receiptHash = receiptPayload.canonicalHash("entrypoint_hash")

        val finalStatus = result["final_status"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted final_status", result)
        val finalBody = validateStructuredRoute(finalStatus, "Final status route")
        val finalHash = finalBody.canonicalHash("hash")
        val finalStatusBody = finalBody["status"] as? Map<*, *>
            ?: throw invalidSubmitAndWait("Final status route omitted typed status", finalBody)
        val finalKind = finalStatusBody.pipelineStatus("kind")
        if (terminalKind != IrohaPipelineTransactionStatusKind.Applied ||
            finalKind != IrohaPipelineTransactionStatusKind.Applied
        ) {
            throw invalidSubmitAndWait("Submit-and-wait did not reach Applied finality", result)
        }
        if (listOf(hash, transactionHash, receiptHash, finalHash).any { it != expectedHash }) {
            throw invalidSubmitAndWait("Submit-and-wait hashes do not match the locally supplied hash", result)
        }

        return IrohaSubmitAndWaitOutcome(
            hash = hash,
            transactionHash = transactionHash,
            receiptHash = receiptHash,
            finalHash = finalHash,
            terminalKind = terminalKind,
            terminalStatuses = terminalStatuses,
            attempts = attempts,
            elapsedMillis = elapsedMillis,
            rejectionReason = finalStatusBody["rejection_reason"]
        )
    }

    private fun throwSubmitAndWaitRpcError(error: IrohaMcpJsonRpcError): Nothing {
        throw classifiedSubmitAndWaitError(error.message, error.data)
    }

    private fun classifiedSubmitAndWaitError(message: String, details: Any?): IrohaSubmitAndWaitException {
        val code = when {
            message.contains("last_status=Rejected", ignoreCase = true) ->
                IrohaSubmitAndWaitErrorCode.REJECTED
            message.contains("last_status=Expired", ignoreCase = true) ->
                IrohaSubmitAndWaitErrorCode.EXPIRED
            message.contains("timed out waiting for terminal transaction status", ignoreCase = true) ->
                IrohaSubmitAndWaitErrorCode.TIMEOUT
            else -> IrohaSubmitAndWaitErrorCode.RPC_ERROR
        }
        return IrohaSubmitAndWaitException(code, message, details)
    }

    private fun Map<*, *>.canonicalHash(key: String): String {
        val raw = this[key] as? String
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted $key", this)
        return canonicalTransactionHash(raw)
            ?: throw invalidSubmitAndWait("Submit-and-wait returned a non-canonical $key", this)
    }

    private fun Map<*, *>.pipelineStatus(key: String): IrohaPipelineTransactionStatusKind {
        val raw = this[key] as? String
            ?: throw invalidSubmitAndWait("Submit-and-wait omitted $key", this)
        return raw.toPipelineStatus()
            ?: throw invalidSubmitAndWait("Submit-and-wait returned unsupported status $raw", this)
    }

    private fun String.toPipelineStatus(): IrohaPipelineTransactionStatusKind? {
        return IrohaPipelineTransactionStatusKind.values().firstOrNull { it.name == this }
    }

    private fun Map<*, *>.exactLong(key: String): Long? {
        val value = this[key] as? Number ?: return null
        val doubleValue = value.toDouble()
        val longValue = value.toLong()
        return longValue.takeIf { doubleValue.isFinite() && doubleValue == longValue.toDouble() }
    }

    private fun canonicalTransactionHash(value: String?): String? {
        return value?.takeIf(CANONICAL_TRANSACTION_HASH::matches)
    }

    private fun invalidSubmitAndWait(message: String, details: Any?): IrohaSubmitAndWaitException {
        return IrohaSubmitAndWaitException(
            IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE,
            message,
            details
        )
    }

    private companion object {
        const val SUBMIT_AND_WAIT_TOOL = "iroha.transactions.submit_and_wait"
        const val APPLIED_STATUS = "Applied"
        const val JSON_MEDIA_TYPE = "application/json"
        const val HTTP_OK = 200
        const val MAX_SUBMIT_WAIT_TIMEOUT_MILLIS = 300_000L
        const val MIN_SUBMIT_WAIT_POLL_MILLIS = 100L
        const val MAX_SUBMIT_WAIT_POLL_MILLIS = 60_000L
        const val FANOUT_ATTEMPTED_HEADER = "x-iroha-fanout-routes-attempted"
        const val FANOUT_SUCCEEDED_HEADER = "x-iroha-fanout-routes-succeeded"
        const val FANOUT_FAILED_HEADER = "x-iroha-fanout-routes-failed"
        const val FANOUT_DENIED_HEADER = "x-iroha-fanout-routes-denied"
        const val FANOUT_UNAVAILABLE_HEADER = "x-iroha-fanout-routes-unavailable"
        const val FANOUT_NOT_FOUND_HEADER = "x-iroha-fanout-routes-not-found"
        val FANOUT_HEADER_NAMES = listOf(
            FANOUT_ATTEMPTED_HEADER,
            FANOUT_SUCCEEDED_HEADER,
            FANOUT_FAILED_HEADER,
            FANOUT_DENIED_HEADER,
            FANOUT_UNAVAILABLE_HEADER,
            FANOUT_NOT_FOUND_HEADER
        )
        val CANONICAL_TRANSACTION_HASH = Regex("^[0-9a-f]{63}[13579bdf]$")
        val FANOUT_COUNT = Regex("^(0|[1-9][0-9]*)$")
    }
}
