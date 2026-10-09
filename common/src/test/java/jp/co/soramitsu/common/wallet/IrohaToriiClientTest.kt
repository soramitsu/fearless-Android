package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcError
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatus
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusKind
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaSubmitAndWaitErrorCode
import jp.co.soramitsu.common.data.network.iroha.IrohaSubmitAndWaitException
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiDegradedException
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiApi
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiResponseException
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.RetrofitIrohaToriiClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec.IrohaAddressException
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class IrohaToriiClientTest {

    @Test
    fun `delegates read endpoints through validated torii routes`() = runBlocking {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        client.accounts(limit = 50, offset = 10, countMode = IrohaToriiRoutes.CountMode.Exact)
        assertEquals(
            "https://taira.sora.org/v1/accounts?limit=50&offset=10&count_mode=exact",
            api.lastUrl
        )

        client.accountAssets(
            accountId = ACCOUNT,
            limit = 25,
            countMode = IrohaToriiRoutes.CountMode.Bounded,
            asset = ASSET_DEFINITION_ID,
            scope = "global"
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/assets?limit=25&count_mode=bounded&asset=$ASSET_DEFINITION_ID&scope=global",
            api.lastUrl
        )

        client.assetDefinitions(
            limit = IrohaToriiRoutes.MAX_LIMIT,
            offset = 0,
            countMode = IrohaToriiRoutes.CountMode.Bounded
        )
        assertEquals(
            "https://taira.sora.org/v1/assets/definitions?limit=500&offset=0&count_mode=bounded",
            api.lastUrl
        )

        client.transactionStatus(HASH, scope = IrohaToriiRoutes.TransactionStatusScope.Global)
        assertEquals(
            "https://taira.sora.org/v1/pipeline/transactions/status?hash=$HASH&scope=global",
            api.lastUrl
        )
    }

    @Test
    fun `submits canonical norito through mcp submit and wait`() = runBlocking {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        val outcome = client.submitTransactionAndWait(
            noritoBytes = byteArrayOf(1, 2, 3),
            expectedHash = HASH,
            timeoutMillis = 120_000,
            pollIntervalMillis = 500
        )
        assertEquals(HASH, outcome.receiptHash)
        assertEquals(IrohaPipelineTransactionStatusKind.Applied, outcome.terminalKind)
        assertEquals("https://taira.sora.org/v1/mcp", api.lastUrl)
        assertEquals("tools/call", api.lastJsonRpcRequest?.method)
        val params = api.lastJsonRpcRequest?.params.orEmpty()
        assertEquals("iroha.transactions.submit_and_wait", params["name"])
        val arguments = params["arguments"] as Map<*, *>
        assertEquals("AQID", arguments["body_base64"])
        assertEquals(HASH, arguments["hash"])
        assertEquals(listOf("Applied"), arguments["terminal_statuses"])

        client.mcpJsonRpc(IrohaToriiRoutes.mcpJsonRpcRequest("tools/list", "1"))
        assertEquals("https://taira.sora.org/v1/mcp", api.lastUrl)
        assertEquals("tools/list", api.lastJsonRpcRequest?.method)
    }

    @Test
    fun `accepts mcp transport without outer fanout when nested routes are complete`() = runBlocking {
        val api = FakeIrohaToriiApi().apply {
            fanoutHeaders = Headers.headersOf("Content-Type", "application/json; charset=utf-8")
        }
        val client = RetrofitIrohaToriiClient(api)

        val outcome = client.submitTransactionAndWait(
            noritoBytes = byteArrayOf(1, 2, 3),
            expectedHash = HASH,
            timeoutMillis = 120_000,
            pollIntervalMillis = 500
        )
        assertEquals(IrohaPipelineTransactionStatusKind.Applied, outcome.terminalKind)
        assertEquals(mapOf("ok" to true), client.mcpCapabilities())
    }

    @Test
    fun `rejects json rpc responses that are not exactly bound to the request`() {
        val mutations: List<(FakeIrohaToriiApi) -> Unit> = listOf(
            { it.responseJsonRpc = null },
            { it.responseJsonRpc = "1.0" },
            { it.responseId = { "wrong-id" } },
            { it.includeResponseResult = false },
            {
                it.responseError = IrohaMcpJsonRpcError(
                    code = -32603,
                    message = "ambiguous"
                )
            }
        )

        mutations.forEach { mutate ->
            val api = FakeIrohaToriiApi().also(mutate)
            assertThrows(IrohaToriiResponseException::class.java) {
                runBlocking {
                    RetrofitIrohaToriiClient(api).mcpJsonRpc(
                        IrohaToriiRoutes.mcpJsonRpcRequest("tools/list", "request-1")
                    )
                }
            }
        }
    }

    @Test
    fun `rejects non json and ambiguous outer successful responses`() {
        val nonJsonApi = FakeIrohaToriiApi().apply {
            fanoutHeaders = fanoutHeaders.newBuilder()
                .set("Content-Type", "text/plain")
                .build()
        }
        val nonJsonClient = RetrofitIrohaToriiClient(nonJsonApi)

        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { nonJsonClient.accounts() }
        }
        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { nonJsonClient.mcpCapabilities() }
        }

        val duplicateApi = FakeIrohaToriiApi().apply {
            fanoutHeaders = fanoutHeaders.newBuilder()
                .add("content-type", "application/json")
                .build()
        }
        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { RetrofitIrohaToriiClient(duplicateApi).accounts() }
        }
    }

    @Test
    fun `rejects non json and case colliding nested route metadata`() {
        val nonJsonApi = FakeIrohaToriiApi().apply {
            nestedContentType = "text/plain"
        }
        val nonJsonError = assertThrows(IrohaSubmitAndWaitException::class.java) {
            runBlocking {
                RetrofitIrohaToriiClient(nonJsonApi).submitTransactionAndWait(
                    noritoBytes = byteArrayOf(1),
                    expectedHash = HASH,
                    timeoutMillis = 1_000,
                    pollIntervalMillis = 100
                )
            }
        }
        assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, nonJsonError.code)

        val collidingHeadersApi = FakeIrohaToriiApi().apply {
            nestedFanoutHeaders = nestedFanoutHeaders +
                ("X-Iroha-Fanout-Routes-Attempted" to "1")
        }
        val collisionError = assertThrows(IrohaSubmitAndWaitException::class.java) {
            runBlocking {
                RetrofitIrohaToriiClient(collidingHeadersApi).submitTransactionAndWait(
                    noritoBytes = byteArrayOf(1),
                    expectedHash = HASH,
                    timeoutMillis = 1_000,
                    pollIntervalMillis = 100
                )
            }
        }
        assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, collisionError.code)
    }

    @Test
    fun `rejects an outer mcp redirect before accepting an attacker final body`() {
        val api = FakeIrohaToriiApi().apply {
            mcpHttpStatus = 302
        }
        val client = RetrofitIrohaToriiClient(api)

        val error = assertThrows(HttpException::class.java) {
            runBlocking {
                client.submitTransactionAndWait(
                    noritoBytes = byteArrayOf(1, 2, 3),
                    expectedHash = HASH,
                    timeoutMillis = 120_000,
                    pollIntervalMillis = 500
                )
            }
        }
        assertEquals(302, error.code())
    }

    @Test
    fun `rejects degraded nested submit and wait route metadata`() {
        val api = FakeIrohaToriiApi().apply {
            nestedFanoutHeaders = mapOf(
                "x-iroha-fanout-routes-attempted" to "2",
                "x-iroha-fanout-routes-succeeded" to "1",
                "x-iroha-fanout-routes-failed" to "1",
                "x-iroha-fanout-routes-denied" to "0",
                "x-iroha-fanout-routes-unavailable" to "0",
                "x-iroha-fanout-routes-not-found" to "1"
            )
        }
        val client = RetrofitIrohaToriiClient(api)

        assertThrows(IrohaToriiDegradedException::class.java) {
            runBlocking {
                client.submitTransactionAndWait(
                    byteArrayOf(1),
                    HASH,
                    timeoutMillis = 1,
                    pollIntervalMillis = 100
                )
            }
        }
    }

    @Test
    fun `rejects any submit and wait hash that differs from local expected hash`() {
        val mismatchedHash = "c".repeat(63) + "3"
        val selectors: List<(FakeIrohaToriiApi) -> Unit> = listOf(
            { it.outcomeHash = mismatchedHash },
            { it.transactionHash = mismatchedHash },
            { it.receiptHash = mismatchedHash },
            { it.finalHash = mismatchedHash }
        )

        selectors.forEach { mutate ->
            val api = FakeIrohaToriiApi().also(mutate)
            val error = assertThrows(IrohaSubmitAndWaitException::class.java) {
                runBlocking {
                    RetrofitIrohaToriiClient(api).submitTransactionAndWait(
                        byteArrayOf(1),
                        HASH,
                        timeoutMillis = 1,
                        pollIntervalMillis = 100
                    )
                }
            }
            assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, error.code)
        }
    }

    @Test
    fun `rejects non canonical transaction hash spellings without normalization`() {
        val spellings = listOf("0x$HASH", HASH.uppercase(), " $HASH")

        spellings.forEach { nonCanonicalHash ->
            val api = FakeIrohaToriiApi()
            val error = assertThrows(IrohaSubmitAndWaitException::class.java) {
                runBlocking {
                    RetrofitIrohaToriiClient(api).submitTransactionAndWait(
                        byteArrayOf(1),
                        nonCanonicalHash,
                        timeoutMillis = 1,
                        pollIntervalMillis = 100
                    )
                }
            }
            assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, error.code)
            assertEquals(null, api.lastUrl)
        }

        val selectors: List<(FakeIrohaToriiApi, String) -> Unit> = listOf(
            { api, hash -> api.outcomeHash = hash },
            { api, hash -> api.transactionHash = hash },
            { api, hash -> api.receiptHash = hash },
            { api, hash -> api.finalHash = hash }
        )
        spellings.forEach { nonCanonicalHash ->
            selectors.forEach { mutate ->
                val api = FakeIrohaToriiApi().also { mutate(it, nonCanonicalHash) }
                val error = assertThrows(IrohaSubmitAndWaitException::class.java) {
                    runBlocking {
                        RetrofitIrohaToriiClient(api).submitTransactionAndWait(
                            byteArrayOf(1),
                            HASH,
                            timeoutMillis = 1,
                            pollIntervalMillis = 100
                        )
                    }
                }
                assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, error.code)
            }
        }
    }

    @Test
    fun `rejects matching but non applied terminal and final status`() {
        val api = FakeIrohaToriiApi().apply {
            terminalKind = IrohaPipelineTransactionStatusKind.Committed
        }
        val error = assertThrows(IrohaSubmitAndWaitException::class.java) {
            runBlocking {
                RetrofitIrohaToriiClient(api).submitTransactionAndWait(
                    byteArrayOf(1),
                    HASH,
                    timeoutMillis = 1,
                    pollIntervalMillis = 100
                )
            }
        }
        assertEquals(IrohaSubmitAndWaitErrorCode.INVALID_RESPONSE, error.code)
    }

    @Test
    fun `routes nexus through registry torii url while allowing runtime override`() {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)

        runBlocking {
            client.mcpCapabilities(network = UniversalWalletRegistry.nexus)
        }
        assertEquals("https://minamoto.sora.org/v1/mcp", api.lastUrl)

        runBlocking {
            client.mcpCapabilities(
                network = UniversalWalletRegistry.nexus,
                baseUrl = "https://nexus.example"
            )
        }
        assertEquals("https://nexus.example/v1/mcp", api.lastUrl)
    }

    @Test
    fun `rejects wrong-network iroha account ids before fetch`() {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        assertThrows(IrohaAddressException::class.java) {
            runBlocking {
                client.account(NEXUS_ACCOUNT)
            }
        }
        assertEquals(null, api.lastUrl)

        runBlocking {
            client.account(
                accountId = NEXUS_ACCOUNT,
                baseUrl = "https://nexus.example",
                network = UniversalWalletRegistry.nexus
            )
        }
        assertEquals("https://nexus.example/v1/accounts/$ENCODED_NEXUS_ACCOUNT", api.lastUrl)
    }

    @Test
    fun `rejects partial successful fanout responses`() {
        val api = FakeIrohaToriiApi().apply {
            fanoutHeaders = Headers.headersOf(
                "x-iroha-fanout-routes-attempted", "5",
                "x-iroha-fanout-routes-succeeded", "1",
                "x-iroha-fanout-routes-failed", "4",
                "x-iroha-fanout-routes-denied", "1",
                "x-iroha-fanout-routes-unavailable", "2",
                "x-iroha-fanout-routes-not-found", "1"
            )
        }
        val client = RetrofitIrohaToriiClient(api)

        val error = assertThrows(IrohaToriiDegradedException::class.java) {
            runBlocking { client.accounts() }
        }

        assertEquals(5, error.fanout.attempted)
        assertEquals(1, error.fanout.succeeded)
        assertEquals(1, error.fanout.denied)
        assertEquals(1, error.fanout.notFound)
    }

    @Test
    fun `rejects incomplete six-header fanout contract`() {
        val api = FakeIrohaToriiApi().apply {
            fanoutHeaders = Headers.headersOf(
                "x-iroha-fanout-routes-attempted", "1",
                "x-iroha-fanout-routes-succeeded", "1",
                "x-iroha-fanout-routes-failed", "0",
                "x-iroha-fanout-routes-denied", "0",
                "x-iroha-fanout-routes-unavailable", "0"
            )
        }
        val client = RetrofitIrohaToriiClient(api)

        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { client.accounts() }
        }
    }

    @Test
    fun `rejects successful routed reads without fanout evidence`() {
        val api = FakeIrohaToriiApi().apply {
            fanoutHeaders = Headers.headersOf()
        }
        val client = RetrofitIrohaToriiClient(api)

        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { client.accounts() }
        }
    }

    @Test
    fun `rejects overflowing fanout counters as malformed metadata`() {
        val maximum = Int.MAX_VALUE.toString()
        val api = FakeIrohaToriiApi().apply {
            fanoutHeaders = Headers.headersOf(
                "x-iroha-fanout-routes-attempted", maximum,
                "x-iroha-fanout-routes-succeeded", "0",
                "x-iroha-fanout-routes-failed", maximum,
                "x-iroha-fanout-routes-denied", maximum,
                "x-iroha-fanout-routes-unavailable", maximum,
                "x-iroha-fanout-routes-not-found", maximum
            )
        }
        val client = RetrofitIrohaToriiClient(api)

        assertThrows(IrohaToriiResponseException::class.java) {
            runBlocking { client.accounts() }
        }
    }

    private class FakeIrohaToriiApi : IrohaToriiApi {
        var lastUrl: String? = null
            private set
        var lastJsonRpcRequest: IrohaMcpJsonRpcRequest? = null
            private set
        var nestedFanoutHeaders: Map<String, String> = mapOf(
            "x-iroha-fanout-routes-attempted" to "1",
            "x-iroha-fanout-routes-succeeded" to "1",
            "x-iroha-fanout-routes-failed" to "0",
            "x-iroha-fanout-routes-denied" to "0",
            "x-iroha-fanout-routes-unavailable" to "0",
            "x-iroha-fanout-routes-not-found" to "0"
        )
        var nestedContentType: String = "application/json; charset=utf-8"
        var outcomeHash: String = HASH
        var transactionHash: String = HASH
        var receiptHash: String = HASH
        var finalHash: String = HASH
        var terminalKind: IrohaPipelineTransactionStatusKind = IrohaPipelineTransactionStatusKind.Applied
        var responseJsonRpc: String? = "2.0"
        var responseId: (String) -> String? = { it }
        var includeResponseResult: Boolean = true
        var responseError: IrohaMcpJsonRpcError? = null
        var mcpHttpStatus: Int? = null
        var fanoutHeaders: Headers = Headers.headersOf(
            "Content-Type", "application/json; charset=utf-8",
            "x-iroha-fanout-routes-attempted", "1",
            "x-iroha-fanout-routes-succeeded", "1",
            "x-iroha-fanout-routes-failed", "0",
            "x-iroha-fanout-routes-denied", "0",
            "x-iroha-fanout-routes-unavailable", "0",
            "x-iroha-fanout-routes-not-found", "0"
        )

        override suspend fun health(url: String): String {
            lastUrl = url
            return "ok"
        }

        override suspend fun accounts(url: String): Response<IrohaAccountListResponse> {
            lastUrl = url
            return Response.success(
                IrohaAccountListResponse(
                    items = listOf(IrohaAccountListItem(id = ACCOUNT)),
                    hasMore = false,
                    countMode = "bounded"
                ),
                fanoutHeaders
            )
        }

        override suspend fun account(url: String): Response<IrohaAccountListItem> {
            lastUrl = url
            return Response.success(IrohaAccountListItem(id = ACCOUNT), fanoutHeaders)
        }

        override suspend fun accountAssets(url: String): Response<IrohaAccountAssetListResponse> {
            lastUrl = url
            return Response.success(
                IrohaAccountAssetListResponse(
                    items = listOf(
                        IrohaAccountAssetListItem(
                            accountId = ACCOUNT,
                            asset = ASSET_DEFINITION_ID,
                            quantity = "1",
                            scope = "global"
                        )
                    ),
                    hasMore = false,
                    countMode = "bounded"
                ),
                fanoutHeaders
            )
        }

        override suspend fun assetDefinitions(url: String): Response<IrohaAssetDefinitionListResponse> {
            lastUrl = url
            return Response.success(
                IrohaAssetDefinitionListResponse(
                    items = emptyList(),
                    hasMore = false,
                    countMode = "bounded"
                ),
                fanoutHeaders
            )
        }

        override suspend fun transactionStatus(url: String): Response<IrohaPipelineTransactionStatusResponse> {
            lastUrl = url
            return Response.success(
                IrohaPipelineTransactionStatusResponse(
                    hash = HASH,
                    status = IrohaPipelineTransactionStatus(
                        kind = IrohaPipelineTransactionStatusKind.Committed
                    ),
                    scope = "global",
                    resolvedFrom = "state"
                ),
                fanoutHeaders
            )
        }

        override suspend fun mcpCapabilities(url: String): Response<Map<String, Any?>> {
            lastUrl = url
            return mcpResponse(mapOf("ok" to true))
        }

        override suspend fun mcpJsonRpc(
            url: String,
            body: IrohaMcpJsonRpcRequest
        ): Response<IrohaMcpJsonRpcResponse> {
            lastUrl = url
            lastJsonRpcRequest = body
            val toolName = body.params?.get("name")
            val result = if (toolName == "iroha.transactions.submit_and_wait") {
                mapOf(
                    "isError" to false,
                    "structuredContent" to mapOf(
                        "status" to 200,
                        "hash" to outcomeHash,
                        "tx_hash" to transactionHash,
                        "terminal_kind" to terminalKind.name,
                        "terminal_statuses" to listOf("Applied"),
                        "attempts" to 1,
                        "elapsed_ms" to 0,
                        "submit" to mapOf(
                            "status" to 200,
                            "headers" to nestedFanoutHeaders,
                            "content_type" to nestedContentType,
                            "body" to mapOf(
                                "payload" to mapOf("entrypoint_hash" to receiptHash)
                            )
                        ),
                        "final_status" to mapOf(
                            "status" to 200,
                            "headers" to nestedFanoutHeaders,
                            "content_type" to nestedContentType,
                            "body" to mapOf(
                                "hash" to finalHash,
                                "status" to mapOf("kind" to terminalKind.name),
                                "scope" to "global",
                                "resolved_from" to "state"
                            )
                        )
                    )
                )
            } else {
                mapOf("ok" to true)
            }
            return mcpResponse(
                IrohaMcpJsonRpcResponse(
                    jsonrpc = responseJsonRpc,
                    id = responseId(body.id),
                    result = result.takeIf { includeResponseResult },
                    error = responseError
                )
            )
        }

        private fun <T> mcpResponse(body: T): Response<T> {
            val status = mcpHttpStatus ?: return Response.success(body, fanoutHeaders)
            val attackerAppliedBody = """
                {"status":200,"headers":{"x-iroha-fanout-routes-attempted":"1","x-iroha-fanout-routes-succeeded":"1","x-iroha-fanout-routes-failed":"0","x-iroha-fanout-routes-denied":"0","x-iroha-fanout-routes-unavailable":"0","x-iroha-fanout-routes-not-found":"0"},"body":{"terminal_kind":"Applied"}}
            """.trimIndent()
            val rawRedirect = okhttp3.Response.Builder()
                .request(
                    okhttp3.Request.Builder()
                        .url("https://taira.sora.org/v1/mcp")
                        .build()
                )
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(status)
                .message("redirect")
                .build()
            return Response.error(
                attackerAppliedBody.toResponseBody("application/json".toMediaType()),
                rawRedirect
            )
        }
    }

    private companion object {
        const val ACCOUNT = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_ACCOUNT = "testu%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        const val NEXUS_ACCOUNT = "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_NEXUS_ACCOUNT = "sorau%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        val HASH = "a".repeat(63) + "1"
        const val ASSET_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    }
}
