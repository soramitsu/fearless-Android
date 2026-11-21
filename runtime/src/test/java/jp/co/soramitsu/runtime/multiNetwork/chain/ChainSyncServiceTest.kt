package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.ChainAccountLocal
import jp.co.soramitsu.coredb.model.MetaAccountLocal
import jp.co.soramitsu.coredb.model.RelationJoinedMetaAccountInfo
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.coredb.recovery.selection.EndpointCatalogSnapshot
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.ext.withBitcoinTestnetAccountChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.whenever
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class ChainSyncServiceTest {

    private val REMOTE_CHAIN = ChainRemote(
        chainId = "0x00",
        rank = null,
        name = "Test",
        assets = listOf(
            ChainAssetRemote(
                id = "test",
                name = "test",
                precision = 10,
                priceId = "test",
                icon = "test",
                symbol = "test",
                staking = null,
                purchaseProviders = null,
                isUtility = null,
                type = null,
                currencyId = null,
                existentialDeposit = null,
                color = null,
                isNative = null,
                ethereumType = null,
                priceProvider = null,
                tonType = null,
                coinbaseUrl = null
            )
        ),
        nodes = listOf(
            ChainNodeRemote(
                url = "url",
                name = "test"
            )
        ),
        icon = "test",
        addressPrefix = 0,
        options = emptyList(),
        parentId = null,
        externalApi = null,
        minSupportedVersion = null,
        paraId = null,
        ecosystem = "Substrate"
    )

    private val LOCAL_CHAIN = mapChainToChainLocal(REMOTE_CHAIN.toChain())

    // Parsed the way the remote catalog is, so missing required fields stay null: no name, then no address prefix.
    private val MALFORMED_CHAINS = listOf(
        """{"chainId":"0x01","addressPrefix":0,"ecosystem":"Substrate","assets":[]}""",
        """{"chainId":"0x02","name":"No prefix","ecosystem":"Substrate","assets":[]}"""
    ).map { Gson().fromJson(it, ChainRemote::class.java) }

    @Mock
    lateinit var dao: ChainDao

    @Mock
    lateinit var metaAccountDao: MetaAccountDao

    @Mock
    lateinit var assetsDao: AssetDao

    @Mock
    lateinit var chainFetcher: ChainFetcher

    @Mock
    lateinit var contextManager: ContextManager

    lateinit var chainSyncService: ChainSyncService

    private var freshLocal = emptyList<JoinedChainInfo>()
    private var observedCatalog: EndpointCatalogSnapshot? = null
    private var onCatalog: ((List<JoinedChainInfo>, EndpointCatalogSnapshot) -> Unit)? = null

    @Before
    fun setup() = runBlocking<Unit> {
        chainSyncService = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager)
        `when`(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(emptyList())
        `when`(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
        whenever(dao.replaceCatalog(any(), any())).thenAnswer { invocation ->
            val plan = invocation.getArgument<(List<JoinedChainInfo>) -> EndpointCatalogSnapshot>(1)
            val next = plan(freshLocal.map { it.copy(nodes = emptyList()) })
            observedCatalog = next
            onCatalog?.invoke(freshLocal, next)
            val prior = freshLocal.flatMap { it.assets }.map { it.chainId to it.id }.toSet()
            val additions = next.chains.filter { it.chain.id !in next.preservedCatalogChains }
                .flatMap { it.assets }.filter { (it.chainId to it.id) !in prior }
            freshLocal = next.chains.map { item ->
                if (item.chain.id in next.preservedCatalogChains) {
                    freshLocal.first { it.chain.id == item.chain.id }
                } else if (item.chain.id in next.preservedNodeChains) {
                    item.copy(nodes = freshLocal.first { it.chain.id == item.chain.id }.nodes)
                } else item
            }
            additions
        }
    }

    @Test
    fun `testnet account aliases survive replacement as address-only rows without XCM discovery`() = runBlocking<Unit> {
        val aliases = setOf("bitcoin-testnet", "bitcoin:testnet", "BITCOIN:TESTNET", "BitCoin-TestNet")
        localReturns(emptyList<Chain>().withBitcoinTestnetAccountChain(aliases).map(::mapChainToChainLocal))
        remoteReturns(emptyList())
        val removed = expectDeleteChains()

        chainSyncService.syncUp()

        assertTrue(withTimeout(5_000) { removed.await() }.isEmpty())
        val retained = checkNotNull(observedCatalog).chains.filter { it.chain.id in aliases }
        assertEquals(aliases, retained.map { it.chain.id }.toSet())
        retained.forEach { assertTrue(it.assets.isEmpty()); assertTrue(it.nodes.isEmpty()) }
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `offline testnet preserves its stored catalog snapshot without duplicate parent rows`() = runBlocking<Unit> {
        val testnet = emptyList<Chain>().withBitcoinTestnetAccountChain(emptySet()).single()
        val local = mapChainToChainLocal(testnet.copy(name = "Stored testnet metadata"))
        localReturns(listOf(local))
        `when`(chainFetcher.getChains()).thenThrow(IllegalStateException("offline registry"))

        assertTrue(runCatching { chainSyncService.syncUp() }.isFailure)

        val snapshot = checkNotNull(observedCatalog)
        assertEquals(listOf(local), snapshot.chains.filter { it.chain.id == testnet.id })
        assertTrue(testnet.id in snapshot.preservedCatalogChains)
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `app owned native mainnets exist when fetched registry omits them`() = runBlocking<Unit> {
        localReturns(emptyList())
        remoteReturns(emptyList())
        val result = expectUpdateChains()

        chainSyncService.syncUp()

        assertEquals(setOf("bitcoin-mainnet", "solana-mainnet", "bitcoin-testnet"), withTimeout(5_000) { result.await() }.first.ids().toSet())
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `offline registry still adds native mainnets and retains cached networks without exposing XCM`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN))
        `when`(chainFetcher.getChains()).thenThrow(IllegalStateException("offline registry"))
        val result = expectUpdateChains()
        val removed = expectDeleteChains()

        assertTrue(runCatching { chainSyncService.syncUp() }.isFailure)

        assertEquals(setOf("bitcoin-mainnet", "solana-mainnet", "bitcoin-testnet"), withTimeout(5_000) { result.await() }.first.ids().toSet())
        assertTrue(withTimeout(5_000) { removed.await() }.isEmpty())
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `historical native aliases survive refresh and nodes are compared by chain plus URL`() = runBlocking<Unit> {
        val native = nativeWalletChains(setOf("solana-mainnet", "solana:mainnet")).map(::mapChainToChainLocal)
        // Both Solana ids use the same RPC URL; the alias must get its own row.
        localReturns(native.map { if (it.chain.id == "solana:mainnet") it.copy(nodes = emptyList()) else it })
        remoteReturns(emptyList())
        val removed = expectDeleteChains()
        val nodeAdds = CompletableDeferred<List<ChainNodeLocal>>()
        val priorObserver = onCatalog
        onCatalog = { old, next ->
            priorObserver?.invoke(old, next)
            val keys = old.flatMap { it.nodes }.map { it.chainId to it.url }.toSet()
            nodeAdds.complete(next.chains.flatMap { it.nodes }.filter { (it.chainId to it.url) !in keys })
        }

        chainSyncService.syncUp()

        assertTrue(withTimeout(5_000) { removed.await() }.isEmpty())
        assertEquals(listOf("solana:mainnet"), withTimeout(5_000) { nodeAdds.await() }.map { it.chainId })
    }

    @Test
    fun `native asset insertion uses its chain key and never the unrelated Substrate identity`() = runBlocking<Unit> {
        localReturns(emptyList())
        remoteReturns(emptyList())
        val rootKey = ByteArray(32) { 1 }
        val solanaKey = ByteArray(32) { 9 }
        val wallet = MetaAccountLocal(
            rootKey, CryptoType.SR25519, rootKey, null, null, null,
            "Public fixture", true, 0, false, null, true
        ).also { it.id = 1 }
        val joined = RelationJoinedMetaAccountInfo(wallet, listOf(
            ChainAccountLocal(1, "solana-mainnet", solanaKey, solanaKey, CryptoType.ED25519, "Public SOL", true)
        ), emptyList())
        `when`(metaAccountDao.getJoinedMetaAccountsInfo()).thenReturn(listOf(joined))
        val additions = CompletableDeferred<List<AssetLocal>>()
        whenever(assetsDao.insertAssets(any())).thenAnswer { invocation ->
            additions.complete(invocation.getArgument(0))
            Unit
        }

        chainSyncService.syncUp()

        val assets = withTimeout(5_000) { additions.await() }
        assertEquals(listOf("solana-mainnet"), assets.map { it.chainId })
        assertArrayEquals(solanaKey, assets.single().accountId)
    }

    @Test
    fun `should insert new chain`() = runBlocking<Unit> {
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = withTimeout(5_000) { updateResult.await() }

        assertEquals(listOf(REMOTE_CHAIN.chainId, "bitcoin-mainnet", "solana-mainnet", "bitcoin-testnet"), adds.ids())
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `should not insert the same chain`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN) + nativeWalletChains().withBitcoinTestnetAccountChain(emptySet()).map(::mapChainToChainLocal))
        remoteReturns(listOf(REMOTE_CHAIN))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = withTimeout(5_000) { updateResult.await() }
        assertTrue(adds.isEmpty())
        assertTrue(updates.none { it.id == REMOTE_CHAIN.chainId })
    }

    @Test
    fun `should update chain`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN) + nativeWalletChains().withBitcoinTestnetAccountChain(emptySet()).map(::mapChainToChainLocal))
        remoteReturns(listOf(REMOTE_CHAIN.copy(name = "new name")))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = withTimeout(5_000) { updateResult.await() }
        assertTrue(adds.isEmpty())
        assertEquals("new name", updates.single { it.id == REMOTE_CHAIN.chainId }.name)
    }

    @Test
    fun `should remove chain`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN) + nativeWalletChains().withBitcoinTestnetAccountChain(emptySet()).map(::mapChainToChainLocal))

        val secondChain = REMOTE_CHAIN.copy(chainId = "0x001")
        remoteReturns(listOf(secondChain))

        val updateResult = expectUpdateChains()
        val deleteResult = expectDeleteChains()

        chainSyncService.syncUp()

        val (adds, updates) = withTimeout(5_000) { updateResult.await() }
        val removed = withTimeout(5_000) { deleteResult.await() }

        assertEquals(listOf(secondChain.chainId), adds.ids())
        assertTrue(updates.none { it.id == REMOTE_CHAIN.chainId || it.id == secondChain.chainId })
        assertEquals(listOf(REMOTE_CHAIN.chainId), removed.ids())
    }

    @Test
    fun `persisted chains are never exposed before a current process sync`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN))

        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `successful sync exposes only the current process remote snapshot`() = runBlocking<Unit> {
        localReturns(emptyList())
        val secondChain = REMOTE_CHAIN.copy(chainId = "0x001")
        remoteReturns(listOf(REMOTE_CHAIN, secondChain))
        expectUpdateChains()

        chainSyncService.syncUp()

        assertEquals(
            listOf(REMOTE_CHAIN.chainId, secondChain.chainId),
            chainSyncService.getCurrentProcessXcmDiscoveryChains().map { it.id }
        )
    }

    @Test
    fun `failed refresh propagates and clears current process XCM discovery snapshot`() = runBlocking<Unit> {
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN))
        expectUpdateChains()
        chainSyncService.syncUp()
        assertEquals(
            listOf(REMOTE_CHAIN.chainId),
            chainSyncService.getCurrentProcessXcmDiscoveryChains().map { it.id }
        )

        `when`(chainFetcher.getChains()).thenThrow(IllegalStateException("registry unavailable"))
        val failure = runCatching { chainSyncService.syncUp() }

        assertTrue(failure.isFailure)
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `failed database application clears fetched XCM discovery snapshot`() = runBlocking<Unit> {
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN))
        onCatalog = { _, _ -> throw IllegalStateException("database unavailable") }

        val failure = runCatching { chainSyncService.syncUp() }

        assertTrue(failure.isFailure)
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `in flight refresh exposes empty XCM snapshot without blocking readers`() = runBlocking<Unit> {
        localReturns(emptyList())
        expectUpdateChains()
        val fetchStarted = CompletableDeferred<Unit>()
        val fetchResult = CompletableDeferred<List<ChainRemote>>()
        val blockingFetcher = object : ChainFetcher {
            override suspend fun getChains(): List<ChainRemote> {
                fetchStarted.complete(Unit)
                return fetchResult.await()
            }
        }
        val service = ChainSyncService(
            dao,
            blockingFetcher,
            metaAccountDao,
            assetsDao,
            contextManager
        )

        val sync = async { service.syncUp() }
        withTimeout(5_000) { fetchStarted.await() }

        withTimeout(500) {
            assertTrue(service.getCurrentProcessXcmDiscoveryChains().isEmpty())
        }
        fetchResult.complete(listOf(REMOTE_CHAIN))
        withTimeout(5_000) { sync.await() }
        assertEquals(
            listOf(REMOTE_CHAIN.chainId),
            service.getCurrentProcessXcmDiscoveryChains().map { it.id }
        )
    }

    @Test
    fun `offline fallback plans from latest transaction snapshot rather than prefetch rows`() = runBlocking<Unit> {
        localReturns(emptyList())
        `when`(chainFetcher.getChains()).thenAnswer {
            freshLocal = listOf(LOCAL_CHAIN)
            throw IllegalStateException("offline registry")
        }
        assertTrue(runCatching { chainSyncService.syncUp() }.isFailure)
        assertTrue(observedCatalog!!.chains.any { it.chain.id == REMOTE_CHAIN.chainId })
        assertTrue(REMOTE_CHAIN.chainId in observedCatalog!!.preservedNodeChains)
        assertTrue(observedCatalog!!.chains.first { it.chain.id == REMOTE_CHAIN.chainId }.nodes.isEmpty())
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `catalog commit failure cannot create wallet assets or publish fetched discovery`() = runBlocking<Unit> {
        remoteReturns(listOf(REMOTE_CHAIN))
        onCatalog = { _, _ -> throw IllegalStateException("commit failed") }
        assertTrue(runCatching { chainSyncService.syncUp() }.isFailure)
        assertTrue(org.mockito.Mockito.mockingDetails(assetsDao).invocations.none { it.method.name == "insertAssets" })
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `offline malformed cached metadata is preserved without domain conversion in either peer order`() = runBlocking<Unit> {
        val damaged = LOCAL_CHAIN.copy(
            chain = LOCAL_CHAIN.chain.copy(id = "damaged", remoteAssetsSource = "future-source", xcm = "{malformed"),
            nodes = LOCAL_CHAIN.nodes.map { it.copy(chainId = "damaged") },
            assets = LOCAL_CHAIN.assets.map { it.copy(chainId = "damaged", staking = "future-staking", purchaseProviders = "{bad") }
        )
        val failure = IllegalStateException("offline fixture")
        `when`(chainFetcher.getChains()).thenThrow(failure)
        for (ordered in listOf(listOf(LOCAL_CHAIN, damaged), listOf(damaged, LOCAL_CHAIN))) {
            localReturns(ordered)
            val thrown = runCatching { chainSyncService.syncUp() }.exceptionOrNull()
            org.junit.Assert.assertSame(failure, thrown)
            val plan = checkNotNull(observedCatalog)
            assertEquals(setOf(LOCAL_CHAIN.chain.id, "damaged"), plan.preservedCatalogChains)
            assertEquals(plan.preservedCatalogChains, plan.preservedNodeChains)
            assertEquals(ordered.map { it.copy(nodes = emptyList()) }, plan.chains.filter { it.chain.id in plan.preservedCatalogChains })
            assertTrue(plan.chains.filter { it.chain.id in plan.preservedCatalogChains }.all { it.nodes.isEmpty() })
            assertEquals(ordered, freshLocal.filter { it.chain.id in plan.preservedCatalogChains })
            assertTrue(plan.chains.any { it.chain.id == "bitcoin-mainnet" })
            assertTrue(plan.chains.any { it.chain.id == "solana-mainnet" })
            assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
        }
    }

    @Test
    fun `a malformed remote chain is skipped and logged while the others sync`() = runBlocking<Unit> {
        val logs = mutableListOf<String>()
        val service = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager) { logs += it }
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN) + MALFORMED_CHAINS)

        service.syncUp()

        assertEquals(listOf(REMOTE_CHAIN.chainId), service.getCurrentProcessXcmDiscoveryChains().map { it.id })
        assertTrue(logs.any { it.startsWith("skipped malformed remote chain 0x01") })
        assertTrue(logs.any { it.startsWith("skipped malformed remote chain 0x02") })
        assertTrue(checkNotNull(observedCatalog).chains.any { it.chain.id == REMOTE_CHAIN.chainId })
    }

    @Test
    fun `a malformed remote chain keeps its cached catalog row`() = runBlocking<Unit> {
        val cached = mapChainToChainLocal(REMOTE_CHAIN.copy(chainId = "0x01", name = "Cached").toChain())
        localReturns(listOf(cached))
        remoteReturns(listOf(REMOTE_CHAIN) + MALFORMED_CHAINS)

        chainSyncService.syncUp()

        val plan = checkNotNull(observedCatalog)
        assertTrue("0x01" in plan.preservedCatalogChains)
        assertEquals("Cached", plan.chains.single { it.chain.id == "0x01" }.chain.name)
    }

    @Test
    fun `a release build drops remote nodes that are not encrypted endpoints`() = runBlocking<Unit> {
        val logs = mutableListOf<String>()
        val service = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager, RemoteNodeUrlPolicy.Release) { logs += it }
        val nodes = listOf("ws://hostile.example", "wss://rpc.example", "http://hostile.example").map { ChainNodeRemote(url = it, name = it) }
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN.copy(nodes = nodes)))

        service.syncUp()

        val synced = checkNotNull(observedCatalog).chains.single { it.chain.id == REMOTE_CHAIN.chainId }
        assertEquals(listOf("wss://rpc.example"), synced.nodes.map { it.url })
        assertTrue(synced.nodes.single().isActive)
        assertTrue(logs.any { it.startsWith("dropped 2 node(s) of remote chain ${REMOTE_CHAIN.chainId}") })
    }

    @Test
    fun `a release build keeps the cached row of a remote chain with only plaintext nodes`() = runBlocking<Unit> {
        val service = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager, RemoteNodeUrlPolicy.Release)
        val cached = mapChainToChainLocal(REMOTE_CHAIN.copy(name = "Cached").toChain())
        localReturns(listOf(cached))
        remoteReturns(listOf(REMOTE_CHAIN.copy(name = "Redirected", nodes = listOf(ChainNodeRemote(url = "ws://hostile.example", name = "hostile")))))

        service.syncUp()

        val plan = checkNotNull(observedCatalog)
        assertTrue(REMOTE_CHAIN.chainId in plan.preservedCatalogChains)
        assertEquals("Cached", plan.chains.single { it.chain.id == REMOTE_CHAIN.chainId }.chain.name)
        assertTrue(service.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `a debug build keeps local development nodes`() = runBlocking<Unit> {
        val service = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager, RemoteNodeUrlPolicy.Debug)
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN.copy(nodes = listOf(ChainNodeRemote(url = "ws://127.0.0.1:9944", name = "local")))))

        service.syncUp()

        assertEquals(
            listOf("ws://127.0.0.1:9944"),
            checkNotNull(observedCatalog).chains.single { it.chain.id == REMOTE_CHAIN.chainId }.nodes.map { it.url }
        )
    }

    @Test
    fun `online refresh does not mark fetched catalog values preserved`() = runBlocking<Unit> {
        localReturns(listOf(LOCAL_CHAIN.copy(chain = LOCAL_CHAIN.chain.copy(xcm = null))))
        remoteReturns(listOf(REMOTE_CHAIN.copy(name = "Fresh registry")))
        chainSyncService.syncUp()
        val plan = checkNotNull(observedCatalog)
        assertTrue(plan.preservedCatalogChains.isEmpty())
        assertTrue(plan.preservedNodeChains.isEmpty())
        assertEquals("Fresh registry", plan.chains.single { it.chain.id == LOCAL_CHAIN.chain.id }.chain.name)
    }

    private suspend fun remoteReturns(chains: List<ChainRemote>) {
        `when`(chainFetcher.getChains()).thenReturn(chains)
    }

    private fun localReturns(chains: List<JoinedChainInfo>) {
        freshLocal = chains
    }

    // These caller tests inspect the complete atomic catalog plan. Physical DAO mutation,
    // selection preservation and rollback are exercised by the independent real-SQL cohort.
    private fun expectUpdateChains(): Deferred<Pair<List<ChainLocal>, List<ChainLocal>>> {
        val result = CompletableDeferred<Pair<List<ChainLocal>, List<ChainLocal>>>()
        val priorObserver = onCatalog
        onCatalog = { old, next ->
            priorObserver?.invoke(old, next)
            val oldById = old.associateBy { it.chain.id }
            result.complete(next.chains.filter { it.chain.id !in oldById }.map { it.chain } to
                next.chains.filter { it.chain.id in oldById && it.chain != oldById.getValue(it.chain.id).chain }.map { it.chain })
        }
        return result
    }

    private fun expectDeleteChains(): Deferred<List<ChainLocal>> {
        val result = CompletableDeferred<List<ChainLocal>>()
        val priorObserver = onCatalog
        onCatalog = { old, next ->
            priorObserver?.invoke(old, next)
            val ids = next.chains.map { it.chain.id }.toSet()
            result.complete(old.filter { it.chain.id !in ids }.map { it.chain })
        }
        return result
    }

    private fun List<ChainLocal>.ids() = map { it.id }
}
