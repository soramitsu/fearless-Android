package jp.co.soramitsu.runtime.multiNetwork.configurator

import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.connection.ConnectionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProvider
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeProviderPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSubscriptionPool
import jp.co.soramitsu.runtime.multiNetwork.runtime.RuntimeSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class SubstrateFreshConfigurationTest {
    private val stale = mapChainLocalToChain(
        JoinedChainInfo(
        ChainLocal("a", null, null, null, "Old", null, "", null, 42, false, false, false, false, false, false, false, false, null, "Substrate", null, null, null, null), emptyList(), emptyList(), emptyList()
        )
    )
    private val fresh = stale.copy(name = "Fresh", addressPrefix = 69)
    private class Fixture {
        val pool = mock(ConnectionPool::class.java)
        val providers = mock(RuntimeProviderPool::class.java)
        val sync = mock(RuntimeSyncService::class.java)
        val subscriptions = mock(RuntimeSubscriptionPool::class.java)
        val connection = mock(ChainConnection::class.java)
        val configurator = SubstrateEnvironmentConfigurator(pool, providers, sync, subscriptions)
    }

    @Test fun runtimeCollaboratorsReceiveFreshReturnedChainSnapshot() = runBlocking<Unit> {
        val f = Fixture()
        doReturn(f.connection).`when`(f.pool).setupConnection(stale)
        `when`(f.connection.chain).thenReturn(fresh)
        f.configurator.configure(stale)
        verify(f.subscriptions).setupRuntimeSubscription(fresh, f.connection)
        verify(f.sync).registerChain(fresh)
        verify(f.providers).setupRuntimeProvider(fresh)
        verify(f.providers, never()).setupRuntimeProvider(stale)
    }

    @Test fun existingRuntimeStillReconcilesTransportBeforeEarlyReturn() = runBlocking<Unit> {
        val f = Fixture()
        doReturn(f.connection).`when`(f.pool).setupConnection(stale)
        `when`(f.connection.chain).thenReturn(fresh)
        val provider = mock(RuntimeProvider::class.java)
        `when`(f.providers.getRuntimeProviderOrNull("a")).thenReturn(provider)
        `when`(provider.getOrNull()).thenReturn(mock(RuntimeSnapshot::class.java))
        f.configurator.configure(stale)
        verify(f.pool).setupConnection(stale)
        verifyNoInteractions(f.subscriptions, f.sync)
        verify(f.providers, never()).setupRuntimeProvider(fresh)
    }

    @Test fun setupFailurePropagatesWithoutStartingRuntimeCollaborators() = runBlocking<Unit> {
        val f = Fixture()
        val expected = IllegalStateException("public fixture unavailable")
        doThrow(expected).`when`(f.pool).setupConnection(stale)
        try {
            f.configurator.configure(stale)
            fail("failure must propagate")
        } catch (
            e: IllegalStateException
        ) {
            assertSame(expected, e)
        }
        verifyNoInteractions(f.subscriptions, f.sync, f.providers)
    }

    @Test fun cancellationPropagatesWithoutStartingRuntimeCollaborators() = runBlocking<Unit> {
        val f = Fixture()
        val expected = CancellationException("public fixture cancellation")
        doThrow(expected).`when`(f.pool).setupConnection(stale)
        try {
            f.configurator.configure(stale)
            fail("cancellation must propagate")
        } catch (
            e: CancellationException
        ) {
            assertSame(expected, e)
        }
        verifyNoInteractions(f.subscriptions, f.sync, f.providers)
    }
}
