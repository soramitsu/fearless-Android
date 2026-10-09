package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionSpec
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcError
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
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
import org.junit.Assert.assertThrows
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
                        "object" to "1.23"
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
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, client.lastDefinitionsLimit)
        assertEquals(0L, client.lastDefinitionsOffset)
        assertEquals(IrohaToriiRoutes.CountMode.Bounded, client.lastDefinitionsCountMode)
        assertEquals("tools/call", client.lastRequest?.method)
        assertEquals("iroha.instructions.list", client.lastRequest?.params?.get("name"))

        val arguments = client.lastRequest?.params?.get("arguments") as Map<*, *>
        assertEquals(ADDRESS, arguments["account"])
        assertEquals(ASSET_ID, arguments["asset_id"])
        assertEquals("Transfer", arguments["kind"])
        assertEquals(1, arguments["page"])
        assertEquals(25, arguments["per_page"])
        assertEquals("committed", arguments["transaction_status"])

        assertNull(page.nextCursor)
        assertEquals(1, page.items.size)

        val operation = page.items.single()
        assertEquals("$TX_HASH:0", operation.id)
        assertEquals(ADDRESS, operation.address)
        assertEquals(1_704_067_200_000L, operation.time)
        val transfer = operation.type as Operation.Type.Transfer
        assertEquals(TX_HASH, transfer.hash)
        assertEquals(ADDRESS, transfer.myAddress)
        assertEquals(BigInteger("1230000000"), transfer.amount)
        assertEquals(ADDRESS, transfer.sender)
        assertEquals(COUNTERPARTY, transfer.receiver)
        assertEquals(Operation.Status.COMPLETED, transfer.status)
        assertNull(transfer.fee)
    }

    @Test
    fun `rejects structured quantity amounts from real gson maps`() {
        @Suppress("UNCHECKED_CAST")
        val value = Gson().fromJson(
            """
            {
              "source": "$ASSET_ID#$COUNTERPARTY",
              "destination": "$ADDRESS",
              "object": {
                "mantissa": "456",
                "scale": 2
              }
            }
            """.trimIndent(),
            Map::class.java
        ) as Map<String, Any?>
        val source = IrohaHistorySource(
            FakeIrohaToriiClient(response = mcpResponse(instruction(value = value))),
            INDEXER_URL
        )

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    @Test
    fun `rejects numeric and malformed structured gson amounts`() {
        @Suppress("UNCHECKED_CAST")
        val value = Gson().fromJson(
            """
            {
              "source": "$ASSET_ID#$COUNTERPARTY",
              "destination": "$ADDRESS",
              "object": {
                "mantissa": "456",
                "scale": 2.5
              }
            }
            """.trimIndent(),
            Map::class.java
        ) as Map<String, Any?>
        val source = IrohaHistorySource(
            FakeIrohaToriiClient(response = mcpResponse(instruction(value = value))),
            INDEXER_URL
        )

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    @Test
    fun `uses supplied cursor and caps taira mcp page size`() = runBlocking {
        val client = FakeIrohaToriiClient(
            response = mcpResponse(instruction(), page = 3, perPage = 100, totalItems = 201)
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        source.getOperations(
            pageSize = IrohaToriiRoutes.MAX_LIMIT + 10,
            cursor = "3:3:201",
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        val arguments = client.lastRequest?.params?.get("arguments") as Map<*, *>
        assertEquals(3, arguments["page"])
        assertEquals(100, arguments["per_page"])
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
    fun `rejects noncanonical iroha chain identities`() {
        val client = FakeIrohaToriiClient(response = mcpResponse(instruction()))
        val source = IrohaHistorySource(client, INDEXER_URL)

        listOf(
            UniversalWalletRegistry.taira.id,
            UniversalWalletRegistry.taira.chainId.uppercase(),
            UniversalWalletRegistry.nexus.id,
            "unknown-iroha-chain"
        ).forEach { chainId ->
            assertThrows(IrohaHistoryReadException::class.java) {
                runBlocking {
                    source.getOperations(
                        pageSize = 25,
                        cursor = null,
                        filters = setOf(TransactionFilter.TRANSFER),
                        accountId = byteArrayOf(),
                        chain = irohaChain(chainId),
                        chainAsset = irohaAsset(),
                        accountAddress = ADDRESS
                    )
                }
            }
        }
        assertEquals(0, client.mcpCalls)
    }

    @Test
    fun `fails closed when torii mcp returns an error`() {
        val client = FakeIrohaToriiClient(
            response = IrohaMcpJsonRpcResponse(
                jsonrpc = "2.0",
                id = "history-1",
                error = IrohaMcpJsonRpcError(code = -32000, message = "index unavailable")
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }

        assertEquals(1, client.mcpCalls)
    }

    @Test
    fun `rejects history routes without complete fanout evidence`() {
        val client = FakeIrohaToriiClient(
            response = mcpResponse(instruction(), headers = emptyMap())
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }

        assertEquals(1, client.mcpCalls)
    }

    @Test
    fun `rejects flattened legacy mcp history results`() {
        val client = FakeIrohaToriiClient(
            response = IrohaMcpJsonRpcResponse(
                jsonrpc = "2.0",
                id = "history-1",
                result = mapOf("body" to mapOf("items" to listOf(instruction())))
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    @Test
    fun `rejects a missing torii asset scale before querying history`() {
        val client = FakeIrohaToriiClient(definitionScale = null)
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }

        assertEquals(0, client.mcpCalls)
    }

    @Test
    fun `requires explicit complete asset definition pagination`() {
        listOf<Boolean?>(null, true).forEach { hasMore ->
            val client = FakeIrohaToriiClient(definitionHasMore = hasMore)
            val source = IrohaHistorySource(client, INDEXER_URL)

            assertThrows(IrohaHistoryReadException::class.java) {
                runBlocking {
                    source.getOperations(
                        pageSize = 25,
                        cursor = null,
                        filters = setOf(TransactionFilter.TRANSFER),
                        accountId = byteArrayOf(),
                        chain = irohaChain(),
                        chainAsset = irohaAsset(),
                        accountAddress = ADDRESS
                    )
                }
            }

            assertEquals(0, client.mcpCalls)
        }

        val wrongMode = FakeIrohaToriiClient(definitionCountMode = "exact")
        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                IrohaHistorySource(wrongMode, INDEXER_URL).getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
        assertEquals(0, wrongMode.mcpCalls)
    }

    @Test
    fun `rejects asset sources that only contain the wallet address as a substring`() {
        val client = FakeIrohaToriiClient(
            response = mcpResponse(
                instruction(
                    value = mapOf(
                        "source" to "${ASSET_ID}#prefix${ADDRESS}suffix",
                        "destination" to COUNTERPARTY,
                        "object" to "1.23"
                    )
                )
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    @Test
    fun `rejects case mutated noncanonical i105 source accounts`() {
        val caseMutatedAddress = "T${ADDRESS.drop(1)}"
        val client = FakeIrohaToriiClient(
            response = mcpResponse(
                instruction(
                    value = mapOf(
                        "source" to "${ASSET_ID}#$caseMutatedAddress",
                        "destination" to COUNTERPARTY,
                        "object" to "1.23"
                    )
                )
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    @Test
    fun `rejects every noncanonical committed transaction status`() {
        listOf(null, "Pending", "Expired", "Commited").forEach { transactionStatus ->
            val client = FakeIrohaToriiClient(
                response = mcpResponse(instruction(transactionStatus = transactionStatus))
            )
            val source = IrohaHistorySource(client, INDEXER_URL)

            assertThrows(IrohaHistoryReadException::class.java) {
                runBlocking {
                    source.getOperations(
                        pageSize = 25,
                        cursor = null,
                        filters = setOf(TransactionFilter.TRANSFER),
                        accountId = byteArrayOf(),
                        chain = irohaChain(),
                        chainAsset = irohaAsset(),
                        accountAddress = ADDRESS
                    )
                }
            }
        }
    }

    @Test
    fun `rejects legacy and ambiguous top level history field aliases`() {
        val aliasedInstructions = listOf(
            instruction(transactionStatus = null).toMutableMap().apply {
                put("status", "Committed")
            },
            instruction().toMutableMap().apply {
                put("transactionStatus", "Committed")
            },
            instruction().toMutableMap().apply {
                put("transactionHash", "b".repeat(63) + "1")
            },
            instruction().toMutableMap().apply {
                put("createdAt", "2024-01-01T00:00:00Z")
            }
        )

        aliasedInstructions.forEach { aliasedInstruction ->
            val source = IrohaHistorySource(
                FakeIrohaToriiClient(response = mcpResponse(aliasedInstruction)),
                INDEXER_URL
            )

            assertThrows(IrohaHistoryReadException::class.java) {
                runBlocking {
                    source.getOperations(
                        pageSize = 25,
                        cursor = null,
                        filters = setOf(TransactionFilter.TRANSFER),
                        accountId = byteArrayOf(),
                        chain = irohaChain(),
                        chainAsset = irohaAsset(),
                        accountAddress = ADDRESS
                    )
                }
            }
        }
    }

    @Test
    fun `requires exact atomic batch mode and stable canonical identities`() = runBlocking {
        val entries = listOf(
            mapOf(
                "leg_id" to "outgoing",
                "from" to ADDRESS,
                "to" to COUNTERPARTY,
                "asset_definition" to ASSET_ID,
                "amount" to "1.25"
            ),
            mapOf(
                "leg_id" to "incoming",
                "from" to COUNTERPARTY,
                "to" to ADDRESS,
                "asset_definition" to ASSET_ID,
                "amount" to "2"
            )
        )
        val batch = instruction(
            value = mapOf(
                "mode" to mapOf("mode" to "Atomic", "value" to null),
                "entries" to entries
            ),
            variant = "AssetBatch",
            index = 2,
            transactionHash = TX_HASH_2
        )
        val singles = listOf(instruction(index = 0), instruction(index = 1))
        val page = IrohaHistorySource(
            FakeIrohaToriiClient(
                response = mcpResponse(singles.first(), items = singles + batch)
            ),
            INDEXER_URL
        ).getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        assertEquals(
            setOf("$TX_HASH:0", "$TX_HASH:1", "$TX_HASH_2:2:outgoing", "$TX_HASH_2:2:incoming"),
            page.items.map(Operation::id).toSet()
        )

        val invalidModes: List<Any?> = listOf(
            "Atomic",
            mapOf("mode" to "Independent", "value" to null),
            mapOf("mode" to "Atomic"),
            mapOf("mode" to "Atomic", "value" to null, "unexpected" to true)
        )
        invalidModes.forEach { mode ->
            assertHistoryRejected(
                mcpResponse(
                    instruction(
                        value = mapOf("mode" to mode, "entries" to entries),
                        variant = "AssetBatch"
                    )
                )
            )
        }

        val duplicateLegs = entries.map { it + ("leg_id" to "duplicate") }
        assertHistoryRejected(
            mcpResponse(
                instruction(
                    value = mapOf(
                        "mode" to mapOf("mode" to "Atomic", "value" to null),
                        "entries" to duplicateLegs
                    ),
                    variant = "AssetBatch"
                )
            )
        )

        val oversizedUtf8Leg = entries.first() + ("leg_id" to "é".repeat(129))
        assertHistoryRejected(
            mcpResponse(
                instruction(
                    value = mapOf(
                        "mode" to mapOf("mode" to "Atomic", "value" to null),
                        "entries" to listOf(oversizedUtf8Leg)
                    ),
                    variant = "AssetBatch"
                )
            )
        )
    }

    @Test
    fun `rejects noncanonical numeric spellings and values outside the iroha domain`() {
        val maximum = BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)
        val invalidAmounts: List<Any?> = listOf(
            1.25,
            "+1",
            "01",
            "-0",
            "1.0",
            "1.20",
            ".5",
            "1.",
            maximum.add(BigInteger.ONE).toString(),
            "9".repeat(10_000)
        )

        invalidAmounts.forEach { amount ->
            assertHistoryRejected(
                mcpResponse(
                    instruction(
                        value = mapOf(
                            "source" to "${ASSET_ID}#$ADDRESS",
                            "destination" to COUNTERPARTY,
                            "object" to amount
                        )
                    )
                )
            )
        }
    }

    @Test
    fun `rejects malformed canonical dto fields and rust raw identifier aliases`() {
        val invalidItems = mutableListOf<Map<String, Any?>>()
        fun mutated(key: String, value: Any?): Map<String, Any?> =
            instruction().toMutableMap().apply { put(key, value) }

        invalidItems += mutated("transaction_hash", TX_HASH.uppercase())
        invalidItems += mutated("transaction_hash", "0x$TX_HASH")
        invalidItems += mutated("transaction_hash", "a".repeat(64))
        invalidItems += mutated("created_at", 1_704_067_200)
        invalidItems += mutated("created_at", "2024-02-30T00:00:00Z")
        invalidItems += mutated("index", 9_007_199_254_740_992.0)
        invalidItems += mutated("block", 9_007_199_254_740_992.0)
        invalidItems += mutated("kind", "Mint")
        invalidItems += mutated("authority", "not-i105")
        invalidItems += instruction().toMutableMap().apply { put("unexpected", true) }
        invalidItems += instruction().toMutableMap().apply {
            val canonicalBox = remove("box")
            put("r#box", canonicalBox)
        }
        invalidItems += instruction(
            value = mapOf(
                "source" to "${ASSET_ID}#junk#$ADDRESS",
                "destination" to COUNTERPARTY,
                "object" to "1.25"
            )
        )

        invalidItems.forEach { assertHistoryRejected(mcpResponse(it)) }
    }

    @Test
    fun `requires coherent exact pagination and snapshot bound cursors`() {
        listOf("0", "3", "0:1:1", "2:1:1", "1:0:0", "999999999999999999999:1:1").forEach { cursor ->
            assertHistoryRejected(mcpResponse(instruction()), cursor = cursor)
        }

        assertHistoryRejected(
            mcpResponse(
                instruction(),
                paginationOverride = mapOf(
                    "page" to 1,
                    "per_page" to 25,
                    "total_pages" to 1
                )
            )
        )
        assertHistoryRejected(
            mcpResponse(
                instruction(),
                paginationOverride = mapOf(
                    "page" to 1,
                    "per_page" to 25,
                    "total_pages" to 1,
                    "total_items" to 1,
                    "unexpected" to true
                )
            )
        )
        assertHistoryRejected(mcpResponse(instruction(), bodyExtras = mapOf("unexpected" to true)))
        assertHistoryRejected(mcpResponse(instruction(), totalItems = 2))
        assertHistoryRejected(
            mcpResponse(instruction(), page = 2, totalItems = 27),
            cursor = "2:2:26"
        )
        assertHistoryRejected(
            mcpResponse(
                instruction(),
                paginationOverride = mapOf(
                    "page" to 9_007_199_254_740_992.0,
                    "per_page" to 25,
                    "total_pages" to 1,
                    "total_items" to 1
                )
            )
        )
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

    private fun assertHistoryRejected(
        response: IrohaMcpJsonRpcResponse,
        cursor: String? = null
    ) {
        val source = IrohaHistorySource(FakeIrohaToriiClient(response = response), INDEXER_URL)
        assertThrows(IrohaHistoryReadException::class.java) {
            runBlocking {
                source.getOperations(
                    pageSize = 25,
                    cursor = cursor,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
    }

    private class FakeIrohaToriiClient(
        private val response: IrohaMcpJsonRpcResponse = mcpResponse(instruction()),
        private val definitionScale: Int? = 9,
        private val definitionHasMore: Boolean? = false,
        private val definitionCountMode: String = "bounded"
    ) : IrohaToriiClient {
        var mcpCalls = 0
            private set
        var lastRequest: IrohaMcpJsonRpcRequest? = null
            private set
        var lastNetwork: UniversalWalletRegistry.IrohaNetwork? = null
            private set
        var lastBaseUrl: String? = null
            private set
        var lastDefinitionsLimit: Int? = null
            private set
        var lastDefinitionsOffset: Long? = null
            private set
        var lastDefinitionsCountMode: IrohaToriiRoutes.CountMode? = null
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

        override suspend fun assetDefinitions(
            baseUrl: String?,
            limit: Int?,
            offset: Long?,
            countMode: IrohaToriiRoutes.CountMode?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAssetDefinitionListResponse {
            lastDefinitionsLimit = limit
            lastDefinitionsOffset = offset
            lastDefinitionsCountMode = countMode
            return IrohaAssetDefinitionListResponse(
                items = listOf(
                    IrohaAssetDefinitionListItem(
                        id = ASSET_ID,
                        name = "XOR",
                        alias = null,
                        spec = IrohaAssetDefinitionSpec(scale = definitionScale)
                    )
                ),
                hasMore = definitionHasMore,
                countMode = definitionCountMode
            )
        }

        override suspend fun submitTransactionAndWait(
            noritoBytes: ByteArray,
            expectedHash: String,
            timeoutMillis: Long,
            pollIntervalMillis: Long,
            network: UniversalWalletRegistry.IrohaNetwork,
            baseUrl: String?
        ): jp.co.soramitsu.common.data.network.iroha.IrohaSubmitAndWaitOutcome =
            error("Unexpected Iroha submit call")

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
        const val ASSET_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val TX_HASH = "ab".repeat(31) + "a1"
        val TX_HASH_2 = "cd".repeat(31) + "e3"
        val ADDRESS = IrohaKeyDerivation.deriveAddress(
            mnemonic = MNEMONIC,
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105
        val COUNTERPARTY = IrohaKeyDerivation.deriveAddress(
            mnemonic = "legal winner thank year wave sausage worth useful legal winner thank yellow",
            chainDiscriminant = UniversalWalletRegistry.taira.chainDiscriminant
        ).i105

        fun mcpResponse(
            instruction: Map<String, Any?>,
            items: List<Map<String, Any?>> = listOf(instruction),
            headers: Map<String, String> = COMPLETE_FANOUT_HEADERS,
            page: Int = 1,
            perPage: Int = 25,
            totalItems: Long = items.size.toLong(),
            paginationOverride: Map<String, Any?>? = null,
            bodyExtras: Map<String, Any?> = emptyMap()
        ): IrohaMcpJsonRpcResponse {
            val totalPages = if (totalItems == 0L) 0L else ((totalItems - 1L) / perPage) + 1L
            val pagination = paginationOverride ?: mapOf(
                "page" to page,
                "per_page" to perPage,
                "total_pages" to totalPages,
                "total_items" to totalItems
            )
            return IrohaMcpJsonRpcResponse(
                jsonrpc = "2.0",
                id = "history-$page",
                result = mapOf(
                    "isError" to false,
                    "structuredContent" to mapOf(
                        "status" to 200,
                        "headers" to headers,
                        "content_type" to "application/json",
                        "body" to (mapOf(
                            "items" to items,
                            "pagination" to pagination
                        ) + bodyExtras)
                    )
                )
            )
        }

        val COMPLETE_FANOUT_HEADERS = mapOf(
            "x-iroha-fanout-routes-attempted" to "1",
            "x-iroha-fanout-routes-succeeded" to "1",
            "x-iroha-fanout-routes-failed" to "0",
            "x-iroha-fanout-routes-denied" to "0",
            "x-iroha-fanout-routes-unavailable" to "0",
            "x-iroha-fanout-routes-not-found" to "0"
        )

        fun instruction(
            value: Map<String, Any?> = mapOf(
                "source" to "${ASSET_ID}#$ADDRESS",
                "destination" to COUNTERPARTY,
                "object" to "1.23"
            ),
            transactionStatus: String? = "Committed",
            variant: String = "Asset",
            index: Int = 0,
            transactionHash: String = TX_HASH
        ): Map<String, Any?> {
            return mutableMapOf<String, Any?>(
                "authority" to ADDRESS,
                "transaction_hash" to transactionHash,
                "created_at" to "2024-01-01T00:00:00Z",
                "kind" to "Transfer",
                "block" to 1,
                "index" to index,
                "box" to mapOf(
                    "encoded" to "0x00",
                    "framed_sha256" to "0x${"00".repeat(32)}",
                    "json" to mapOf(
                        "kind" to "Transfer",
                        "payload" to mapOf(
                            "variant" to variant,
                            "value" to value
                        ),
                        "wire_id" to if (variant == "AssetBatch") "iroha.transfer_batch" else "iroha.transfer",
                        "encoded" to "00"
                    )
                )
            ).apply {
                transactionStatus?.let { put("transaction_status", it) }
            }
        }

        fun irohaChain(chainId: String = UniversalWalletRegistry.taira.chainId): Chain {
            val network = UniversalWalletRegistry.taira

            return Chain(
                id = chainId,
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
                chainId = UniversalWalletRegistry.taira.chainId,
                chainName = "Taira Testnet",
                chainIcon = null,
                isTestNet = true,
                priceId = null,
                precision = 9,
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
