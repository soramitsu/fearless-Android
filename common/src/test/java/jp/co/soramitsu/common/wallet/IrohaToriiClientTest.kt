package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatus
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiApi
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionPayload
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionReceipt
import jp.co.soramitsu.common.data.network.iroha.RetrofitIrohaToriiClient
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec.IrohaAddressException
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
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
            asset = "xor#sora",
            scope = "global"
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/assets?limit=25&count_mode=bounded&asset=xor%23sora&scope=global",
            api.lastUrl
        )

        client.transactionStatus(HASH, scope = IrohaToriiRoutes.TransactionStatusScope.Global)
        assertEquals(
            "https://taira.sora.org/v1/pipeline/transactions/status?hash=$HASH&scope=global",
            api.lastUrl
        )
    }

    @Test
    fun `delegates mcp and transaction writes with explicit transport contracts`() = runBlocking {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        client.submitTransaction(byteArrayOf(1, 2, 3))
        assertEquals("https://taira.sora.org/v1/pipeline/transactions", api.lastUrl)
        assertEquals("application/x-norito", api.lastRequestBody?.contentType().toString())

        client.mcpJsonRpc(IrohaToriiRoutes.mcpJsonRpcRequest("tools/list", "1"))
        assertEquals("https://taira.sora.org/v1/mcp", api.lastUrl)
        assertEquals("tools/list", api.lastJsonRpcRequest?.method)
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

    private class FakeIrohaToriiApi : IrohaToriiApi {
        var lastUrl: String? = null
            private set
        var lastRequestBody: RequestBody? = null
            private set
        var lastJsonRpcRequest: IrohaMcpJsonRpcRequest? = null
            private set

        override suspend fun health(url: String): String {
            lastUrl = url
            return "ok"
        }

        override suspend fun accounts(url: String): IrohaAccountListResponse {
            lastUrl = url
            return IrohaAccountListResponse(
                items = listOf(IrohaAccountListItem(id = ACCOUNT)),
                hasMore = false,
                countMode = "bounded"
            )
        }

        override suspend fun account(url: String): IrohaAccountListItem {
            lastUrl = url
            return IrohaAccountListItem(id = ACCOUNT)
        }

        override suspend fun accountAssets(url: String): IrohaAccountAssetListResponse {
            lastUrl = url
            return IrohaAccountAssetListResponse(
                items = listOf(
                    IrohaAccountAssetListItem(
                        accountId = ACCOUNT,
                        asset = "xor#sora",
                        quantity = "1",
                        scope = "global"
                    )
                ),
                hasMore = false,
                countMode = "bounded"
            )
        }

        override suspend fun assetDefinitions(url: String): IrohaAssetDefinitionListResponse {
            lastUrl = url
            return IrohaAssetDefinitionListResponse(
                items = emptyList(),
                hasMore = false,
                countMode = "bounded"
            )
        }

        override suspend fun transactionStatus(url: String): IrohaPipelineTransactionStatusResponse {
            lastUrl = url
            return IrohaPipelineTransactionStatusResponse(
                hash = HASH,
                status = IrohaPipelineTransactionStatus(kind = "committed"),
                scope = "global",
                resolvedFrom = "pipeline"
            )
        }

        override suspend fun submitTransaction(
            url: String,
            body: RequestBody
        ): IrohaTransactionSubmissionReceipt {
            lastUrl = url
            lastRequestBody = body
            return IrohaTransactionSubmissionReceipt(
                payload = IrohaTransactionSubmissionPayload(
                    txHash = HASH,
                    entrypointHash = HASH,
                    submittedAtMs = 1L,
                    submittedAtHeight = 1L
                )
            )
        }

        override suspend fun mcpCapabilities(url: String): Map<String, Any?> {
            lastUrl = url
            return mapOf("ok" to true)
        }

        override suspend fun mcpJsonRpc(
            url: String,
            body: IrohaMcpJsonRpcRequest
        ): IrohaMcpJsonRpcResponse {
            lastUrl = url
            lastJsonRpcRequest = body
            return IrohaMcpJsonRpcResponse(id = body.id, result = mapOf("ok" to true))
        }
    }

    private companion object {
        const val ACCOUNT = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_ACCOUNT = "testu%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        const val NEXUS_ACCOUNT = "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_NEXUS_ACCOUNT = "sorau%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        val HASH = "a".repeat(64)
    }
}
