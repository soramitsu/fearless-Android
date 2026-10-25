package jp.co.soramitsu.runtime.multiNetwork

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainSyncService
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainToChainLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.NodeId
import jp.co.soramitsu.runtime.multiNetwork.chain.nativeWalletChains
import jp.co.soramitsu.runtime.multiNetwork.configurator.ChainEnvironmentConfiguratorProvider
import jp.co.soramitsu.runtime.multiNetwork.configurator.NativeWalletEnvironmentConfigurator
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

class NativeNodeSelectionTest {
    private val registries = mutableListOf<ChainRegistry>()

    @Before
    fun mockAndroidLoggingOnWorkerThreads() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
    }

    @After
    fun finishRegistryScopesBeforeRestoringAndroidLogging() {
        try {
            runBlocking {
                registries.forEach { it.scope.coroutineContext.job.cancelAndJoin() }
            }
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `editing a row removed before persistence cannot acknowledge success`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            assertTrue(runCatching {
                fixture.registry.updateNode(NodeId(fixture.chain.id to CUSTOM), "Changed", "$CUSTOM/new")
            }.isFailure)
        } finally {
            fixture.registry.scope.cancel()
        }
    }

    @Test
    fun `native selection commits the requested exact row before returning`() = runBlocking<Unit> {
        val fixture = Fixture()
        whenever(fixture.dao.selectNode(any(), any(), any())).thenAnswer { invocation ->
            val target = invocation.getArgument<String>(2)
            val updated = fixture.chain.copy(nodes = fixture.chain.nodes.map { it.copy(isActive = it.url == target) })
            fixture.rows.tryEmit(listOf(mapChainToChainLocal(updated)))
            Unit
        }
        try {
            fixture.registry.switchNode(NodeId(fixture.chain.id to CUSTOM))
            assertEquals(CUSTOM, fixture.registry.getChain(fixture.chain.id).nodes.single { it.isActive }.url)
        } finally {
            fixture.registry.scope.cancel()
        }
    }

    @Test
    fun `native selection awaits persistence and propagates failure without a socket`() = runBlocking<Unit> {
        val fixture = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var selected: String? = null
        whenever(fixture.dao.selectNode(any(), any(), any())).thenAnswer { invocation ->
            selected = invocation.getArgument(2)
            entered.complete(Unit)
            // Answer is invoked on the registry's IO dispatcher.
            runBlocking { release.await() }
            throw IllegalStateException("held persistence failure")
        }
        val result = async { runCatching { fixture.registry.switchNode(NodeId(fixture.chain.id to CUSTOM)) } }
        delay(150)
        val returnedEarly = result.isCompleted
        release.complete(Unit)
        val outcome = result.await()
        fixture.registry.scope.cancel()
        assertFalse("selection must wait for its write acknowledgement", returnedEarly)
        assertTrue(entered.isCompleted)
        assertEquals(CUSTOM, selected)
        assertTrue(outcome.isFailure)
    }

    @Test
    fun `native RPC selection and custom edits are observable chain changes`() {
        val fixture = Fixture()
        val selected = fixture.chain.copy(nodes = fixture.chain.nodes.map { it.copy(isActive = it.url == CUSTOM) })
        assertNotEquals(fixture.chain, selected)
        assertNotEquals(selected, selected.copy(nodes = selected.nodes.map {
            if (it.url == CUSTOM) it.copy(url = "$CUSTOM/edited") else it
        }))
        fixture.registry.scope.cancel()
    }

    @Test
    fun `registry publishes the newly selected native chain to balance subscribers`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            fixture.registry.syncUp()
            withTimeout(2_000) { fixture.registry.syncedChains.first { it.isNotEmpty() } }
            val selected = fixture.chain.copy(nodes = fixture.chain.nodes.map { it.copy(isActive = it.url == CUSTOM) })
            fixture.rows.emit(listOf(mapChainToChainLocal(selected)))
            val final = withTimeout(2_000) {
                fixture.registry.syncedChains.first { chains -> chains.singleOrNull()?.nodes?.singleOrNull { it.isActive }?.url == CUSTOM }
            }
            assertEquals(CUSTOM, final.single().nodes.single { it.isActive }.url)
        } finally {
            fixture.registry.scope.cancel()
        }
    }

    @Test
    fun `admission precedes queued work and ABA cannot recover old command identity`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            val first = fixture.registry.admitNodeMutation(fixture.chain.id)
            val second = fixture.registry.admitNodeMutation(fixture.chain.id)
            val third = fixture.registry.admitNodeMutation(fixture.chain.id)
            assertFalse(first.isLatest)
            assertFalse(second.isLatest)
            assertTrue(third.isLatest)
            assertTrue(runCatching { fixture.registry.switchNode(first, NodeId(fixture.chain.id to CUSTOM)) }.isFailure)
            org.mockito.kotlin.verify(fixture.dao, org.mockito.kotlin.never()).selectNode(any(), any(), any())
        } finally { fixture.registry.scope.cancel() }
    }

    @Test
    fun `foreign target cannot redirect an admitted mutation`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            val action = fixture.registry.admitNodeMutation("another-chain")
            assertTrue(runCatching { fixture.registry.switchNode(action, NodeId(fixture.chain.id to CUSTOM)) }.isFailure)
            assertTrue(runCatching { fixture.registry.updateNode(action, NodeId(fixture.chain.id to CUSTOM), "Name", CUSTOM) }.isFailure)
            org.mockito.kotlin.verify(fixture.dao, org.mockito.kotlin.never()).selectNode(any(), any(), any())
            assertTrue(org.mockito.Mockito.mockingDetails(fixture.dao).invocations.none { it.method.name == "updateNode" })
        } finally { fixture.registry.scope.cancel() }
    }

    @Test
    fun `cancellation and database retirement reject work before DAO entry`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            val cancelled = fixture.registry.admitNodeMutation(fixture.chain.id)
            cancelled.cancel()
            assertFalse(cancelled.isLatest)
            assertTrue(runCatching { fixture.registry.deleteNode(cancelled, NodeId(fixture.chain.id to CUSTOM)) }.isFailure)
            val retired = fixture.registry.admitNodeMutation(fixture.chain.id)
            fixture.dao.endpointMutationLane.retire()
            assertFalse(retired.isLatest)
            assertTrue(runCatching { fixture.registry.addNode(retired, fixture.chain.id, "New", CUSTOM) }.isFailure)
            org.mockito.kotlin.verify(fixture.dao, org.mockito.kotlin.never()).deleteNode(any(), any(), any())
            org.mockito.kotlin.verify(fixture.dao, org.mockito.kotlin.never()).insertChainNode(any(), any())
        } finally { fixture.registry.scope.cancel() }
    }

    @Test
    fun `catalog admission retires an explicit command without opening a transport`() = runBlocking<Unit> {
        val fixture = Fixture()
        try {
            val action = fixture.registry.admitNodeMutation(fixture.chain.id)
            fixture.dao.endpointMutationLane.admitCatalogReplacement()
            assertFalse(action.isLatest)
            assertTrue(runCatching { fixture.registry.switchNode(action, NodeId(fixture.chain.id to CUSTOM)) }.isFailure)
            org.mockito.kotlin.verify(fixture.dao, org.mockito.kotlin.never()).selectNode(any(), any(), any())
        } finally { fixture.registry.scope.cancel() }
    }

    private inner class Fixture {
        val chain = nativeWalletChains().single { it.id == "solana-mainnet" }.let {
            it.copy(nodes = it.nodes + ChainNode(CUSTOM, "Custom", isActive = false, isDefault = false))
        }
        val rows = MutableSharedFlow<List<jp.co.soramitsu.coredb.model.chain.JoinedChainInfo>>(replay = 1).also {
            it.tryEmit(listOf(mapChainToChainLocal(chain)))
        }
        val dao = mock(ChainDao::class.java)
        val registry: ChainRegistry
        init {
            whenever(dao.endpointMutationLane).thenReturn(EndpointMutationLane())
            whenever(dao.joinChainInfoFlow()).thenReturn(rows)
            val assets = mock(AssetReadOnlyCache::class.java)
            whenever(assets.observeChainIdsNeedingSync()).thenReturn(flowOf(emptySet()))
            val configurator = mock(ChainEnvironmentConfiguratorProvider::class.java)
            whenever(configurator.provide(any())).thenReturn(NativeWalletEnvironmentConfigurator())
            registry = ChainRegistry(
                mock(RuntimeProviderPool::class.java), mock(ConnectionPool::class.java),
                mock(RuntimeSubscriptionPool::class.java), dao, mock(ChainSyncService::class.java),
                mock(RuntimeSyncService::class.java), mock(NetworkStateService::class.java),
                mock(EthereumConnectionPool::class.java), assets, ChainsRepository(dao), configurator,
                mock(PortfolioVisibility::class.java), Dispatchers.IO
            )
            registries += registry
        }
    }

    companion object {
        private const val CUSTOM = "https://solana.example.invalid"
    }
}
