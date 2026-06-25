package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcError
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaTransactionSubmissionReceipt
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerClient
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistorySync
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaKeyDerivation
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import jp.co.soramitsu.xnetworking.lib.datasources.txhistory.api.adapters.HistoryInfoRemoteLoader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class IrohaHistorySourceTest {

    @Test
    fun `maps taira torii instruction entries to transfer operations`() = runBlocking {
        val client = FakeIrohaToriiClient(
            response = mcpResponse(
                instruction = instruction(
                    value = mapOf(
                        "source" to "${ASSET_ID}#$ADDRESS",
                        "destination" to COUNTERPARTY,
                        "object" to "123"
                    )
                )
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        assertEquals(1, client.mcpCalls)
        assertEquals(UniversalWalletRegistry.taira, client.lastNetwork)
        assertEquals(INDEXER_URL, client.lastBaseUrl)
        assertEquals("tools/call", client.lastRequest?.method)
        assertEquals("iroha.instructions.list", client.lastRequest?.params?.get("name"))

        val arguments = client.lastRequest?.params?.get("arguments") as Map<*, *>
        assertEquals(ADDRESS, arguments["account"])
        assertEquals(ASSET_ID, arguments["asset_id"])
        assertEquals("Transfer", arguments["kind"])
        assertEquals(0, arguments["page"])
        assertEquals(25, arguments["per_page"])
        assertEquals("committed", arguments["transaction_status"])

        assertNull(page.nextCursor)
        assertEquals(1, page.items.size)

        val operation = page.items.single()
        assertEquals(TX_HASH, operation.id)
        assertEquals(ADDRESS, operation.address)
        assertEquals(1_704_067_200_000L, operation.time)
        val transfer = operation.type as Operation.Type.Transfer
        assertEquals(TX_HASH, transfer.hash)
        assertEquals(ADDRESS, transfer.myAddress)
        assertEquals(BigInteger.valueOf(123), transfer.amount)
        assertEquals(ADDRESS, transfer.sender)
        assertEquals(COUNTERPARTY, transfer.receiver)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertEquals(BigInteger.ZERO, transfer.fee)
    }

    @Test
    fun `uses supplied cursor and caps taira mcp page size`() = runBlocking {
        val client = FakeIrohaToriiClient(response = mcpResponse(instruction()))
        val source = IrohaHistorySource(client, INDEXER_URL)

        source.getOperations(
            pageSize = IrohaToriiRoutes.MAX_LIMIT + 10,
            cursor = "3",
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        val arguments = client.lastRequest?.params?.get("arguments") as Map<*, *>
        assertEquals(3, arguments["page"])
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, arguments["per_page"])
    }

    @Test
    fun `returns empty page without torii calls for unsupported filters addresses and limits`() = runBlocking {
        val client = FakeIrohaToriiClient(response = mcpResponse(instruction()))
        val source = IrohaHistorySource(client, INDEXER_URL)

        val noTransfer = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = emptySet(),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )
        val emptyLimit = source.getOperations(
            pageSize = 0,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )
        val malformedAddress = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = "../bad"
        )

        assertTrue(noTransfer.items.isEmpty())
        assertTrue(emptyLimit.items.isEmpty())
        assertTrue(malformedAddress.items.isEmpty())
        assertEquals(0, client.mcpCalls)
    }

    @Test
    fun `fails closed when torii mcp returns an error`() = runBlocking {
        val client = FakeIrohaToriiClient(
            response = IrohaMcpJsonRpcResponse(
                id = "history-0",
                error = IrohaMcpJsonRpcError(code = -32000, message = "index unavailable")
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        assertEquals(1, client.mcpCalls)
        assertTrue(page.items.isEmpty())
    }

    @Test
    fun `provider routes iroha history type to iroha source`() {
        val provider = HistorySourceProvider(
            walletOperationsApi = mock(OperationsHistoryApi::class.java),
            chainRegistry = mock(ChainRegistry::class.java),
            historyInfoRemoteLoader = mock(HistoryInfoRemoteLoader::class.java),
            tonRemoteSource = mock(TonRemoteSource::class.java),
            bitcoinTransactionHistorySync = BitcoinTransactionHistorySync(mock(BitcoinIndexerClient::class.java)),
            solanaTransactionHistorySync = SolanaTransactionHistorySync(mock(SolanaIndexerClient::class.java)),
            irohaToriiClient = FakeIrohaToriiClient()
        )

        assertTrue(provider(INDEXER_URL, Chain.ExternalApi.Section.Type.IROHA) is IrohaHistorySource)
    }

    private class FakeIrohaToriiClient(
        private val response: IrohaMcpJsonRpcResponse = mcpResponse(instruction())
    ) : IrohaToriiClient {
        var mcpCalls = 0
            private set
        var lastRequest: IrohaMcpJsonRpcRequest? = null
            private set
        var lastNetwork: UniversalWalletRegistry.IrohaNetwork? = null
            private set
        var lastBaseUrl: String? = null
            private set

        override suspend fun health(baseUrl: String?): String = "ok"

        override suspend fun accounts(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?
        ): IrohaAccountListResponse = error("Unexpected Iroha accounts call")

        override suspend fun account(
            accountId: String,
            baseUrl: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountListItem = error("Unexpected Iroha account call")

        override suspend fun accountAssets(
            accountId: String,
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?,
            asset: String?,
            scope: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountAssetListResponse = error("Unexpected Iroha account-assets call")

        override suspend fun assetDefinitions(baseUrl: String?): IrohaAssetDefinitionListResponse {
            error("Unexpected Iroha asset-definitions call")
        }

        override suspend fun submitTransaction(
            noritoBytes: ByteArray,
            baseUrl: String?
        ): IrohaTransactionSubmissionReceipt = error("Unexpected Iroha submit call")

        override suspend fun transactionStatus(
            hash: String,
            baseUrl: String?,
            scope: IrohaToriiRoutes.TransactionStatusScope
        ): IrohaPipelineTransactionStatusResponse = error("Unexpected Iroha transaction-status call")

        override suspend fun mcpCapabilities(
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): Map<String, Any?> = error("Unexpected Iroha mcp capabilities call")

        override suspend fun mcpJsonRpc(
            request: IrohaMcpJsonRpcRequest,
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): IrohaMcpJsonRpcResponse {
            mcpCalls += 1
            lastRequest = request
            lastNetwork = network
            lastBaseUrl = baseUrl

            return response
        }
    }

    private companion object {
        const val INDEXER_URL = "https://taira.sora.org"
        const val ASSET_ID = "xor#sora"
        const val TX_HASH = "aa-aa-aa"
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val COUNTERPARTY = "i105counterparty"
        val ADDRESS = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105

        fun mcpResponse(instruction: Map<String, Any?>): IrohaMcpJsonRpcResponse {
            return IrohaMcpJsonRpcResponse(
                id = "history-0",
                result = mapOf(
                    "body" to mapOf(
                        "items" to listOf(instruction)
                    )
                )
            )
        }

        fun instruction(
            value: Map<String, Any?> = mapOf(
                "source" to "${ASSET_ID}#$ADDRESS",
                "destination" to COUNTERPARTY,
                "object" to "123"
            )
        ): Map<String, Any?> {
            return mapOf(
                "transaction_hash" to TX_HASH,
                "created_at" to "2024-01-01T00:00:00Z",
                "transaction_status" to "Committed",
                "box" to mapOf(
                    "json" to mapOf(
                        "payload" to mapOf(
                            "variant" to "Asset",
                            "value" to value
                        )
                    )
                )
            )
        }

        fun irohaChain(): Chain {
            val network = UniversalWalletRegistry.taira

            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = "Taira Testnet",
                minSupportedVersion = null,
                assets = listOf(irohaAsset()),
                nodes = emptyList(),
                explorers = emptyList(),
                externalApi = Chain.ExternalApi(
                    staking = null,
                    history = Chain.ExternalApi.Section(Chain.ExternalApi.Section.Type.IROHA, INDEXER_URL),
                    crowdloans = null
                ),
                icon = "",
                addressPrefix = 0,
                isEthereumBased = false,
                isTestNet = true,
                hasCrowdloans = false,
                parentId = null,
                supportStakingPool = false,
                isEthereumChain = false,
                chainlinkProvider = false,
                supportNft = false,
                isUsesAppId = false,
                identityChain = null,
                ecosystem = Ecosystem.Substrate,
                remoteAssetsSource = null
            )
        }

        fun irohaAsset(): Asset {
            return Asset(
                id = ASSET_ID,
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = UniversalWalletRegistry.taira.id,
                chainName = "Taira Testnet",
                chainIcon = null,
                isTestNet = true,
                priceId = null,
                precision = 18,
                staking = Asset.StakingType.UNSUPPORTED,
                purchaseProviders = null,
                supportStakingPool = false,
                isUtility = true,
                type = ChainAssetType.Normal,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = true
            )
        }
    }
}
