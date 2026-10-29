package jp.co.soramitsu.wallet.impl.data.historySource

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerClient
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountAssetListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountHistoryItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountHistoryPage
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaPipelineTransactionStatusResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes.IrohaToriiRouteException
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class IrohaHistorySourceTest {

    @Test
    fun `maps taira torii account history movements to transfer operations`() = runBlocking {
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(
                items = listOf(
                    movement(direction = "outgoing", amount = "25000", movementIndex = 1),
                    movement(direction = "incoming", amount = "1.5", txHash = TX_HASH_2.uppercase()),
                    movement(direction = "self", amount = "2", counterparty = null, txHash = TX_HASH_3),
                    movement(direction = "outgoing", amount = "3", txHash = TX_HASH_4, status = "FAILED", resultOk = false),
                    movement(type = "RAW_ON_CHAIN", direction = "affected", amount = null, counterparty = null),
                    movement(assetDefinitionId = "other-asset"),
                    movement(account = COUNTERPARTY),
                    movement(txHash = "hash:${TX_HASH.uppercase()}#2166"),
                    movement(direction = "incoming", counterparty = "i105counterparty"),
                    movement(movementIndex = null),
                    movement(timestampMs = null)
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

        val request = client.requests.single()
        assertEquals(ADDRESS, request.accountId)
        assertEquals(INDEXER_URL, request.baseUrl)
        assertEquals(25, request.limit)
        assertNull(request.cursor)
        assertEquals(
            "asset_definition_id in [\"$ASSET_ID\"] and type = \"TRANSFER\"",
            request.filter
        )
        assertEquals(UniversalWalletRegistry.taira, request.network)

        assertNull(page.nextCursor)
        assertEquals(listOf("$TX_HASH:1", "$TX_HASH_2:0", "$TX_HASH_3:0", "$TX_HASH_4:0"), page.items.map(Operation::id))
        page.items.forEach { operation ->
            assertEquals(ADDRESS, operation.address)
            assertEquals(TIMESTAMP_MS, operation.time)
            assertEquals(irohaAsset(), operation.chainAsset)
        }
        assertEquals(
            listOf(
                transfer(TX_HASH, "25000000000000000000000", sender = ADDRESS, receiver = COUNTERPARTY),
                transfer(TX_HASH_2, "1500000000000000000", sender = COUNTERPARTY, receiver = ADDRESS),
                transfer(TX_HASH_3, "2000000000000000000", sender = ADDRESS, receiver = ADDRESS),
                transfer(TX_HASH_4, "3000000000000000000", sender = ADDRESS, receiver = COUNTERPARTY, status = Operation.Status.FAILED)
            ),
            page.items.map(Operation::type)
        )
    }

    @Test
    fun `passes torii cursors through and caps taira page size`() = runBlocking {
        val client = FakeIrohaToriiClient(IrohaAccountHistoryPage(items = listOf(movement()), nextCursor = "AQ-next_2"))
        val source = IrohaHistorySource(client, INDEXER_URL)

        val page = source.getOperations(
            pageSize = IrohaToriiRoutes.MAX_LIMIT + 10,
            cursor = "AQ-0aPCF_1",
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        val request = client.requests.single()
        assertEquals("AQ-0aPCF_1", request.cursor)
        assertEquals(IrohaToriiRoutes.MAX_LIMIT, request.limit)
        assertEquals("AQ-next_2", page.nextCursor)
        assertEquals(1, page.items.size)
    }

    @Test
    fun `returns an empty last page for an account without transfers`() = runBlocking {
        val client = FakeIrohaToriiClient(IrohaAccountHistoryPage(items = emptyList(), nextCursor = null))
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

        assertEquals(1, client.requests.size)
        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
    }

    @Test
    fun `follows empty continuation pages until transfers or the end of history`() = runBlocking {
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(nextCursor = "c1"),
            IrohaAccountHistoryPage(items = listOf(movement(type = "RAW_ON_CHAIN")), nextCursor = "c2"),
            IrohaAccountHistoryPage(items = listOf(movement()), nextCursor = "")
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

        assertEquals(listOf(null, "c1", "c2"), client.requests.map(HistoryRequest::cursor))
        assertEquals(listOf("$TX_HASH:0"), page.items.map(Operation::id))
        assertNull(page.nextCursor)
    }

    @Test
    fun `stops empty continuation pages at the bound and keeps the cursor instead of failing`() = runBlocking {
        // Torii scans about 500 blocks per request, so an account without recent transfers only
        // sees empty pages; the source stops after ten and returns the cursor to resume from.
        val client = FakeIrohaToriiClient(
            *Array(20) { index -> IrohaAccountHistoryPage(nextCursor = "c$index") }
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

        assertEquals(10, client.requests.size)
        assertTrue(page.items.isEmpty())
        assertEquals("c9", page.nextCursor)
    }

    @Test
    fun `resumes from a bounded cursor and finds older transfers`() = runBlocking {
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(nextCursor = "c10"),
            IrohaAccountHistoryPage(items = listOf(movement()), nextCursor = null)
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = "c9",
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset(),
            accountAddress = ADDRESS
        )

        assertEquals(listOf("c9", "c10"), client.requests.map(HistoryRequest::cursor))
        assertEquals(listOf("$TX_HASH:0"), page.items.map(Operation::id))
        assertNull(page.nextCursor)
    }

    @Test
    fun `rejects repeated and cyclic cursors before reporting duplicate history`() {
        listOf(
            arrayOf(IrohaAccountHistoryPage(items = listOf(movement()), nextCursor = "start")),
            arrayOf(IrohaAccountHistoryPage(nextCursor = "next"), IrohaAccountHistoryPage(nextCursor = "start"))
        ).forEach { responses ->
            val client = FakeIrohaToriiClient(*responses)
            assertThrows(IOException::class.java) {
                runBlocking {
                    IrohaHistorySource(client, INDEXER_URL).getOperations(
                        pageSize = 25,
                        cursor = "start",
                        filters = setOf(TransactionFilter.TRANSFER),
                        accountId = byteArrayOf(),
                        chain = irohaChain(),
                        chainAsset = irohaAsset(),
                        accountAddress = ADDRESS
                    )
                }
            }
            assertEquals(responses.size, client.requests.size)
        }
    }

    @Test
    fun `selects nexus network address and default torii url`() = runBlocking {
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(
                items = listOf(movement(account = NEXUS_ADDRESS, counterparty = NEXUS_COUNTERPARTY))
            )
        )
        val source = IrohaHistorySource(client, historyUrl = "")

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(UniversalWalletRegistry.nexus),
            chainAsset = irohaAsset(UniversalWalletRegistry.nexus),
            accountAddress = NEXUS_ADDRESS
        )

        val request = client.requests.single()
        assertEquals(UniversalWalletRegistry.nexus, request.network)
        assertEquals(NEXUS_ADDRESS, request.accountId)
        assertNull(request.baseUrl)
        assertEquals(
            transfer(TX_HASH, "1000000000000000000", sender = NEXUS_ADDRESS, receiver = NEXUS_COUNTERPARTY, myAddress = NEXUS_ADDRESS),
            page.items.single().type
        )
    }

    @Test
    fun `matches asset by id or currency id`() = runBlocking {
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(
                items = listOf(
                    movement(assetDefinitionId = ASSET_ID),
                    movement(assetDefinitionId = "xor#universal", txHash = TX_HASH_2)
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
            chainAsset = irohaAsset().copy(id = "xor#universal", currencyId = ASSET_ID),
            accountAddress = ADDRESS
        )

        assertEquals(
            "asset_definition_id in [\"xor#universal\", \"$ASSET_ID\"] and type = \"TRANSFER\"",
            client.requests.single().filter
        )
        assertEquals(listOf("$TX_HASH:0", "$TX_HASH_2:0"), page.items.map(Operation::id))
    }

    @Test
    fun `skips amounts that do not fit the asset precision`() = runBlocking {
        val amounts = listOf("12.30", "1.234", "0", "0.00", "-1", "1e5", " 1", "01", "1.", "9".repeat(161))
        val client = FakeIrohaToriiClient(
            IrohaAccountHistoryPage(
                items = amounts.mapIndexed { index, amount -> movement(amount = amount, movementIndex = index.toLong()) }
            )
        )
        val source = IrohaHistorySource(client, INDEXER_URL)

        val page = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(),
            chainAsset = irohaAsset().copy(precision = 2),
            accountAddress = ADDRESS
        )

        assertEquals(listOf(BigInteger.valueOf(1230)), page.items.map { (it.type as Operation.Type.Transfer).amount })
    }

    @Test
    fun `returns empty page without torii calls for unsupported filters addresses and limits`() = runBlocking {
        val client = FakeIrohaToriiClient(IrohaAccountHistoryPage(items = listOf(movement())))
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
        val wrongNetworkAddress = source.getOperations(
            pageSize = 25,
            cursor = null,
            filters = setOf(TransactionFilter.TRANSFER),
            accountId = byteArrayOf(),
            chain = irohaChain(UniversalWalletRegistry.nexus),
            chainAsset = irohaAsset(UniversalWalletRegistry.nexus),
            accountAddress = ADDRESS
        )

        assertTrue(noTransfer.items.isEmpty())
        assertTrue(emptyLimit.items.isEmpty())
        assertTrue(malformedAddress.items.isEmpty())
        assertTrue(wrongNetworkAddress.items.isEmpty())
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `propagates unavailable history and malformed asset errors to preserve cached history`() {
        val client = FakeIrohaToriiClient(failure = IOException("HTTP 400 invalid_cursor"))
        assertThrows(IOException::class.java) {
            runBlocking {
                IrohaHistorySource(client, INDEXER_URL).getOperations(
                    pageSize = 25,
                    cursor = "3",
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset(),
                    accountAddress = ADDRESS
                )
            }
        }
        val invalidClient = FakeIrohaToriiClient()
        assertThrows(IrohaToriiRouteException::class.java) {
            runBlocking {
                IrohaHistorySource(invalidClient, INDEXER_URL).getOperations(
                    pageSize = 25,
                    cursor = null,
                    filters = setOf(TransactionFilter.TRANSFER),
                    accountId = byteArrayOf(),
                    chain = irohaChain(),
                    chainAsset = irohaAsset().copy(id = "bad asset"),
                    accountAddress = ADDRESS
                )
            }
        }
        assertEquals(1, client.requests.size)
        assertTrue(invalidClient.requests.isEmpty())
    }

    @Test
    fun `propagates cancellation instead of reporting empty history`() {
        val source = IrohaHistorySource(FakeIrohaToriiClient(failure = CancellationException("left screen")), INDEXER_URL)

        assertThrows(CancellationException::class.java) {
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

    private data class HistoryRequest(
        val accountId: String,
        val baseUrl: String?,
        val limit: Int?,
        val cursor: String?,
        val filter: String?,
        val network: UniversalWalletRegistry.IrohaNetwork
    )

    private class FakeIrohaToriiClient(
        vararg pages: IrohaAccountHistoryPage,
        private val failure: Throwable? = null
    ) : IrohaToriiClient {
        private val pages = ArrayDeque(pages.toList())
        val requests = mutableListOf<HistoryRequest>()

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

        override suspend fun accountHistory(
            accountId: String,
            baseUrl: String?,
            limit: Int?,
            cursor: String?,
            filter: String?,
            network: UniversalWalletRegistry.IrohaNetwork
        ): IrohaAccountHistoryPage {
            requests += HistoryRequest(accountId, baseUrl, limit, cursor, filter, network)
            failure?.let { throw it }

            return pages.removeFirstOrNull() ?: IrohaAccountHistoryPage()
        }

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
    }

    private companion object {
        const val INDEXER_URL = "https://taira.sora.org"
        const val ASSET_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val TIMESTAMP_MS = 1_792_667_418_176L
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        const val COUNTERPARTY_MNEMONIC = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        const val TX_HASH = "886e30365349ab6380c728a9be08682784f37dfa3de57adbdf0cfb7e07e585b5"
        val TX_HASH_2 = "a".repeat(63) + "1"
        val TX_HASH_3 = "b".repeat(63) + "3"
        val TX_HASH_4 = "c".repeat(63) + "5"
        val ADDRESS = address(MNEMONIC, UniversalWalletRegistry.taira)
        val COUNTERPARTY = address(COUNTERPARTY_MNEMONIC, UniversalWalletRegistry.taira)
        val NEXUS_ADDRESS = address(MNEMONIC, UniversalWalletRegistry.nexus)
        val NEXUS_COUNTERPARTY = address(COUNTERPARTY_MNEMONIC, UniversalWalletRegistry.nexus)

        fun address(mnemonic: String, network: UniversalWalletRegistry.IrohaNetwork): String {
            return IrohaKeyDerivation.deriveAddress(
                mnemonic = mnemonic,
                chainDiscriminant = network.chainDiscriminant
            ).i105
        }

        fun movement(
            type: String = "TRANSFER",
            direction: String = "outgoing",
            account: String = ADDRESS,
            counterparty: String? = COUNTERPARTY,
            assetDefinitionId: String = ASSET_ID,
            amount: String? = "1",
            txHash: String = TX_HASH,
            movementIndex: Long? = 0,
            timestampMs: Long? = TIMESTAMP_MS,
            status: String = "SUCCESS",
            resultOk: Boolean = true
        ): IrohaAccountHistoryItem {
            return IrohaAccountHistoryItem(
                id = "$txHash:$account:$movementIndex",
                type = type,
                status = status,
                resultOk = resultOk,
                direction = direction,
                accountId = account,
                counterpartyAccountId = counterparty,
                assetId = "$assetDefinitionId#$account",
                assetDefinitionId = assetDefinitionId,
                amount = amount,
                txHash = txHash,
                timestampMs = timestampMs,
                blockHeight = 13,
                movementIndex = movementIndex
            )
        }

        fun transfer(
            hash: String,
            amount: String,
            sender: String,
            receiver: String,
            status: Operation.Status = Operation.Status.COMPLETED,
            myAddress: String = ADDRESS
        ): Operation.Type.Transfer {
            return Operation.Type.Transfer(
                hash = hash,
                myAddress = myAddress,
                amount = BigInteger(amount),
                receiver = receiver,
                sender = sender,
                status = status,
                fee = BigInteger.ZERO
            )
        }

        fun irohaChain(network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira): Chain {
            return Chain(
                id = network.id,
                paraId = null,
                rank = null,
                name = network.id,
                minSupportedVersion = null,
                assets = listOf(irohaAsset(network)),
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
                isTestNet = network == UniversalWalletRegistry.taira,
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

        fun irohaAsset(network: UniversalWalletRegistry.IrohaNetwork = UniversalWalletRegistry.taira): Asset {
            return Asset(
                id = ASSET_ID,
                name = "XOR",
                symbol = "XOR",
                iconUrl = "",
                chainId = network.id,
                chainName = network.id,
                chainIcon = null,
                isTestNet = network == UniversalWalletRegistry.taira,
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
