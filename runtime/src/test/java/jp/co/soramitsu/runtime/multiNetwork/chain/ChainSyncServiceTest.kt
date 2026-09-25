package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainNodeRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import jp.co.soramitsu.testshared.any
import jp.co.soramitsu.testshared.whenever
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

    @Before
    fun setup() {
        chainSyncService = ChainSyncService(dao, chainFetcher, metaAccountDao, assetsDao, contextManager)
        `when`(metaAccountDao.getMetaAccounts()).thenReturn(emptyList())
    }

    @Test
    fun `should insert new chain`() = runBlocking {
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = updateResult.await()

        assertEquals(listOf(REMOTE_CHAIN.chainId), adds.ids())
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `should not insert the same chain`() = runBlocking {
        localReturns(listOf(LOCAL_CHAIN))
        remoteReturns(listOf(REMOTE_CHAIN))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = updateResult.await()
        assertTrue(adds.isEmpty())
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `should update chain`() = runBlocking {
        localReturns(listOf(LOCAL_CHAIN))
        remoteReturns(listOf(REMOTE_CHAIN.copy(name = "new name")))

        val updateResult = expectUpdateChains()

        chainSyncService.syncUp()

        val (adds, updates) = updateResult.await()
        assertTrue(adds.isEmpty())
        assertEquals(listOf(REMOTE_CHAIN.chainId), updates.ids())
    }

    @Test
    fun `should remove chain`() = runBlocking {
        localReturns(listOf(LOCAL_CHAIN))

        val secondChain = REMOTE_CHAIN.copy(chainId = "0x001")
        remoteReturns(listOf(secondChain))

        val updateResult = expectUpdateChains()
        val deleteResult = expectDeleteChains()

        chainSyncService.syncUp()

        val (adds, updates) = updateResult.await()
        val removed = deleteResult.await()

        assertEquals(listOf(secondChain.chainId), adds.ids())
        assertTrue(updates.isEmpty())
        assertEquals(listOf(REMOTE_CHAIN.chainId), removed.ids())
    }

    @Test
    fun `persisted chains are never exposed before a current process sync`() = runBlocking {
        localReturns(listOf(LOCAL_CHAIN))

        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `successful sync exposes only the current process remote snapshot`() = runBlocking {
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
    fun `failed refresh propagates and clears current process XCM discovery snapshot`() = runBlocking {
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
    fun `failed database application clears fetched XCM discovery snapshot`() = runBlocking {
        localReturns(emptyList())
        remoteReturns(listOf(REMOTE_CHAIN))
        whenever(dao.updateChains(any(), any())).thenThrow(IllegalStateException("database unavailable"))

        val failure = runCatching { chainSyncService.syncUp() }

        assertTrue(failure.isFailure)
        assertTrue(chainSyncService.getCurrentProcessXcmDiscoveryChains().isEmpty())
    }

    @Test
    fun `in flight refresh exposes empty XCM snapshot without blocking readers`() = runBlocking {
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
        fetchStarted.await()

        withTimeout(500) {
            assertTrue(service.getCurrentProcessXcmDiscoveryChains().isEmpty())
        }
        fetchResult.complete(listOf(REMOTE_CHAIN))
        sync.await()
        assertEquals(
            listOf(REMOTE_CHAIN.chainId),
            service.getCurrentProcessXcmDiscoveryChains().map { it.id }
        )
    }

    private suspend fun remoteReturns(chains: List<ChainRemote>) {
        `when`(chainFetcher.getChains()).thenReturn(chains)
    }

    private suspend fun localReturns(chains: List<JoinedChainInfo>) {
        `when`(dao.getJoinChainInfo()).thenReturn(chains)
    }

    private suspend fun expectUpdateChains(): Deferred<Pair<List<ChainLocal>, List<ChainLocal>>> {
        val result = CompletableDeferred<Pair<List<ChainLocal>, List<ChainLocal>>>()

        whenever(dao.updateChains(any(), any())).thenAnswer { invocation ->
            val adds = invocation.getArgument<List<ChainLocal>>(0)
            val updates = invocation.getArgument<List<ChainLocal>>(1)

            result.complete(adds to updates)

            Unit
        }

        return result
    }

    private suspend fun expectDeleteChains(): Deferred<List<ChainLocal>> {
        val result = CompletableDeferred<List<ChainLocal>>()

        whenever(dao.deleteChains(any())).thenAnswer { invocation ->
            val removed = invocation.getArgument<List<ChainLocal>>(0)
            result.complete(removed)

            Unit
        }

        return result
    }

    private fun List<ChainLocal>.ids() = map { it.id }
}
