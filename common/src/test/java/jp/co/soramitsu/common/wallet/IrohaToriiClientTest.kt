package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountHistoryPage
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
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

        client.assetDefinitionsPage(
            limit = 500,
            offset = 500,
            countMode = IrohaToriiRoutes.CountMode.Bounded
        )
        assertEquals(
            "https://taira.sora.org/v1/assets/definitions?limit=500&offset=500&count_mode=bounded",
            api.lastUrl
        )

        client.transactionStatus(HASH, scope = IrohaToriiRoutes.TransactionStatusScope.Global)
        assertEquals(
            "https://taira.sora.org/v1/pipeline/transactions/status?hash=$HASH&scope=global",
            api.lastUrl
        )

        client.accountHistory(
            accountId = ACCOUNT,
            limit = 100,
            cursor = "AQ-0aPCF_x",
            filter = IrohaToriiRoutes.transferHistoryFilter(listOf("xor#sora"))
        )
        assertEquals(
            "https://taira.sora.org/v1/accounts/$ENCODED_ACCOUNT/history?limit=100&cursor=AQ-0aPCF_x" +
                "&filter=asset_definition_id%20in%20%5B%22xor%23sora%22%5D%20and%20type%20%3D%20%22TRANSFER%22",
            api.lastUrl
        )
    }

    @Test
    fun `delegates transaction writes with explicit transport contracts`() = runBlocking {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        client.submitTransaction(byteArrayOf(1, 2, 3))
        assertEquals("https://taira.sora.org/v1/pipeline/transactions", api.lastUrl)
        assertEquals("application/x-norito", api.lastRequestBody?.contentType().toString())
    }

    @Test
    fun `routes nexus history through registry torii url while allowing runtime override`() {
        val api = FakeIrohaToriiApi()
        val client = RetrofitIrohaToriiClient(api)

        assertFalse(UniversalWalletRegistry.nexus.enabledByDefault)

        runBlocking {
            client.accountHistory(NEXUS_ACCOUNT, limit = 1, network = UniversalWalletRegistry.nexus)
        }
        assertEquals("https://minamoto.sora.org/v1/accounts/$ENCODED_NEXUS_ACCOUNT/history?limit=1", api.lastUrl)

        runBlocking {
            client.accountHistory(
                accountId = NEXUS_ACCOUNT,
                baseUrl = "https://nexus.example",
                network = UniversalWalletRegistry.nexus
            )
        }
        assertEquals("https://nexus.example/v1/accounts/$ENCODED_NEXUS_ACCOUNT/history", api.lastUrl)
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
        assertThrows(IrohaAddressException::class.java) {
            runBlocking {
                client.accountHistory(NEXUS_ACCOUNT)
            }
        }
        assertThrows(IrohaAddressException::class.java) {
            runBlocking {
                client.accountHistory(ACCOUNT, network = UniversalWalletRegistry.nexus)
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

        override suspend fun accountHistory(url: String): IrohaAccountHistoryPage {
            lastUrl = url
            return IrohaAccountHistoryPage()
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
    }

    private companion object {
        const val ACCOUNT = "testuﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_ACCOUNT = "testu%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        const val NEXUS_ACCOUNT = "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val ENCODED_NEXUS_ACCOUNT = "sorau%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        val HASH = "a".repeat(64)
    }
}
