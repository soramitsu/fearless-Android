package jp.co.soramitsu.runtime.multiNetwork.connection

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketFactory
import com.neovisionaries.ws.client.WebSocketListener
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.core.runtime.EndpointRuntimeStatus
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger
import jp.co.soramitsu.fearless_utils.wsrpc.recovery.Reconnector
import jp.co.soramitsu.fearless_utils.wsrpc.request.RequestExecutor
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Provider

class ConnectionPoolConvergenceTest {
    private class Peer(val url: String, val socket: WebSocket) {
        lateinit var listener: WebSocketListener
        val ready = CountDownLatch(1)
    }
    private class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun hold() {
            entered.countDown()
        check(release.await(5, TimeUnit.SECONDS)) { "fixture release deadline" }
        }
        fun await() { check(entered.await(5, TimeUnit.SECONDS)) { "fixture entry deadline" } }
    }
    private class Fixture(
        requirement: ChainConnection.ExternalRequirement = ChainConnection.ExternalRequirement.ALLOWED
    ) : AutoCloseable {
        val latest = ConcurrentHashMap<String, JoinedChainInfo>()
        val peers = CopyOnWriteArrayList<Peer>()
        val network = mock(NetworkStateService::class.java)
        val permission = MutableStateFlow(requirement)

        @Volatile var beforeRead: (String) -> Unit = {}
        val dao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer(CALLS_REAL_METHODS))
        val pool: ConnectionPool
        init {
            runBlocking {
                doAnswer { call ->
                    val id = call.getArgument<String>(0)
                beforeRead(id)
                latest[id]
                }.`when`(dao).getJoinChainInfo(anyString())
            }
            val provider = Provider {
                val factory = mock(WebSocketFactory::class.java)
                `when`(factory.createSocket(anyString())).thenAnswer { call ->
                    val peer = Peer(call.getArgument(0), mock(WebSocket::class.java))
                    peers += peer
                    `when`(peer.socket.addListener(any(WebSocketListener::class.java))).thenAnswer {
                        peer.listener = it.getArgument(0)
                        peer.socket
                    }
                    `when`(peer.socket.connectAsynchronously()).thenAnswer {
                        peer.ready.countDown()
                    peer.socket
                    }
                    peer.socket
                }
                val logger = object : Logger { override fun log(message: String?) {}
                override fun log(throwable: Throwable?) {} }
                SocketService(Gson(), logger, factory, Reconnector(executor = mock(ScheduledExecutorService::class.java)), RequestExecutor(mock(ExecutorService::class.java)))
            }
            // Finish the initial requirement replay before a test can publish a socket transition.
            val initialRequirementApplied = CountDownLatch(1)
            val observedPermission = object : MutableStateFlow<ChainConnection.ExternalRequirement> by permission {
                @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
                override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ChainConnection.ExternalRequirement>): Nothing =
                    permission.collect(object : kotlinx.coroutines.flow.FlowCollector<ChainConnection.ExternalRequirement> {
                        override suspend fun emit(value: ChainConnection.ExternalRequirement) {
                            collector.emit(value)
                            initialRequirementApplied.countDown()
                        }
                    })
            }
            pool = ConnectionPool(provider, observedPermission, dao, network)
            check(initialRequirementApplied.await(5, TimeUnit.SECONDS)) { "fixture initial requirement deadline" }
        }
        suspend fun setup(id: String = "a") = pool.setupConnection(mapChainLocalToChain(checkNotNull(latest[id])))
        fun connected(peer: Peer = peers.last()) {
            check(peer.ready.await(5, TimeUnit.SECONDS)) { "fixture socket connect admission deadline" }
            peer.listener.onConnected(peer.socket, mutableMapOf())
        }
        override fun close() {
            // The application pool has process lifetime; the test cancels its private observation scope only.
            val field = ConnectionPool::class.java.getDeclaredField("scope")
            field.isAccessible = true
            runBlocking { (field.get(pool) as CoroutineScope).coroutineContext[Job]?.cancelAndJoin() }
            latest.keys.forEach { pool.getConnectionOrNull(it)?.finish() }
            dao.endpointMutationLane.retire()
        }
    }

    // Test-only invocation of the exact core final-grant boundary; production visibility stays internal.
    private fun grantFixtureFinal(
        lane: jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane,
        operation: jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane.Operation
    ) {
        val method = lane.javaClass.declaredMethods.single {
            it.name.substringBefore('$') == "grantFinal" && it.parameterTypes.toList() == listOf(
                jp.co.soramitsu.coredb.recovery.selection.EndpointMutationLane.Operation::class.java,
                Function0::class.java
            )
        }
        try {
            method.invoke(lane, operation, {})
        } catch (error: java.lang.reflect.InvocationTargetException) {
            throw error.targetException
        }
    }

    @Test fun staleCallerNodesNeverOverrideFreshDurableSelection() = runBlocking<Unit> {
        Fixture().use { f ->
            val stale = local()
            f.latest["a"] = local(selected = "second")
            val connection = f.pool.setupConnection(mapChainLocalToChain(stale))
            assertEquals("wss://a.invalid/second", connection.selectedNode!!.url)
            assertEquals(listOf("wss://a.invalid/second"), f.peers.map { it.url })
        }
    }

    @Test fun queuedReplacementSupersedesHeldSetupWithoutOpeningOldTransport() = runBlocking<Unit> {
        supervisorScope {
            Fixture().use { f ->
                f.latest["a"] = local()
                val gate = Gate()
                f.beforeRead = { gate.hold() }
                val first = async(Dispatchers.Default) { runCatching { f.setup() } }
                gate.await()
                f.latest["a"] = local(selected = "second")
                f.beforeRead = {}
                // Read-only test inspection waits until the second intent has entered the pool's desired map.
                val desired = ConnectionPool::class.java.getDeclaredField("desired").also {
                    it.isAccessible = true
                }.get(f.pool) as Map<*, *>
                val firstIntent = desired["a"]
                val second = async(Dispatchers.Default) { f.setup() }
                withTimeout(5000) { while (desired["a"] === firstIntent) yield() }
                gate.release.countDown()
                assertTrue(first.await().isFailure)
                assertEquals("wss://a.invalid/second", second.await().selectedNode!!.url)
                assertEquals(listOf("wss://a.invalid/second"), f.peers.map { it.url })
            }
        }
    }

    @Test fun removeImmediatelyRetiresHeldSetupBeforeCleanupCanAcquirePool() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val setup = async(Dispatchers.Default) { runCatching { f.setup() } }
            gate.await()
            f.pool.removeConnection("a")
            gate.release.countDown()
            assertTrue(setup.await().isFailure)
            withTimeout(5000) { while (f.pool.getConnectionOrNull("a") != null) yield() }
            assertTrue(f.peers.isEmpty())
        }
    }

    @Test fun mutationAdmittedDuringReadCannotPublishFreshRuntimeVersion() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val setup = async(Dispatchers.Default) { runCatching { f.setup() } }
            gate.await()
            f.dao.endpointMutationLane.admit(listOf("a"))
            gate.release.countDown()
            assertTrue(setup.await().isFailure)
            assertTrue(f.peers.isEmpty())
            assertNull(f.pool.getConnectionOrNull("a"))
        }
    }

    @Test fun latestPermissionWinsWhileOldSetupWaitsForDatabase() = runBlocking<Unit> {
        Fixture(ChainConnection.ExternalRequirement.STOPPED).use { f ->
            f.latest["a"] = local()
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val setup = async(Dispatchers.Default) { f.setup() }
            gate.await()
            f.permission.value = ChainConnection.ExternalRequirement.ALLOWED
            f.permission.value = ChainConnection.ExternalRequirement.FORBIDDEN
            gate.release.countDown()
            assertEquals(EndpointRuntimeStatus.Phase.PAUSED, setup.await().runtime.value.phase)
            assertTrue(f.peers.isEmpty())
        }
    }

    @Test fun allowedPermissionStartsOnlyLatestStoppedConfiguration() = runBlocking<Unit> {
        Fixture(ChainConnection.ExternalRequirement.STOPPED).use { f ->
            f.latest["a"] = local()
            f.setup()
            f.latest["a"] = local(selected = "second")
            val connection = f.setup()
            assertTrue(f.peers.isEmpty())
            f.permission.value = ChainConnection.ExternalRequirement.ALLOWED
            withTimeout(5000) { while (f.peers.isEmpty()) yield() }
            assertEquals(listOf("wss://a.invalid/second"), f.peers.map { it.url })
            f.connected()
            assertTrue(connection.runtime.value.isCurrentFor(connection.selectedNode!!))
        }
    }

    @Test fun twoChainsKeepIndependentLogicalAndPhysicalOwnership() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.latest["b"] = local("b")
            val a = f.setup("a")
            f.connected()
            val status = a.runtime.value
            val b = f.setup("b")
            f.connected()
            assertEquals(setOf("wss://a.invalid/first", "wss://b.invalid/first"), f.peers.map { it.url }.toSet())
            f.dao.endpointMutationLane.admit(listOf("b"))
            assertTrue(status.isCurrentFor(a.selectedNode!!))
            assertFalse(b.runtime.value.isCurrentFor(b.selectedNode!!))
            assertNotSame(a.socketService, b.socketService)
        }
    }

    @Test fun metadataAndInactiveListChangesRefreshExistingPhysicalConnection() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val old = connection.runtime.value
            f.latest["a"] = local().let {
                it.copy(chain = it.chain.copy(name = "Fresh"), nodes = it.nodes.map { n -> if (n.isActive)n.copy(name = "Renamed") else n })
            }
            assertSame(connection, f.setup())
            assertEquals(1, f.peers.size)
            assertEquals("Fresh", (connection.chain as jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain).name)
            assertEquals("Renamed", connection.selectedNode!!.name)
            assertFalse(old.isCurrentFor(connection.selectedNode!!))
            assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, connection.runtime.value.phase)
        }
    }

    @Test fun missingParentFailsAndMissingSelectionNeverStartsFallback() = runBlocking<Unit> {
        Fixture().use { f ->
            val stale = mapChainLocalToChain(local())
            assertTrue(runCatching { f.pool.setupConnection(stale) }.isFailure)
            f.latest["a"] = local().let { it.copy(nodes = it.nodes.map { n -> n.copy(isActive = false) }) }
            assertEquals(EndpointRuntimeStatus.Phase.UNAVAILABLE, f.setup().runtime.value.phase)
            assertTrue(f.peers.isEmpty())
        }
    }

    @Test fun changedEcosystemCannotOpenSubstrateTransportFromStaleCaller() = runBlocking<Unit> {
        Fixture().use { f ->
            val stale = mapChainLocalToChain(local())
            f.latest["a"] = local().let { it.copy(chain = it.chain.copy(ecosystem = "Ton")) }
            assertTrue(runCatching { f.pool.setupConnection(stale) }.isFailure)
            assertTrue(f.peers.isEmpty())
        }
    }

    @Test fun cancelledHeldReadCannotStartTransportAfterCancellation() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val setup = async(Dispatchers.Default) { f.setup() }
            gate.await()
            setup.cancel()
            gate.release.countDown()
            runCatching { setup.await() }
            assertTrue(f.peers.isEmpty())
            withTimeout(5000) { while (f.pool.getConnectionOrNull("a") != null) yield() }
        }
    }

    @Test fun missingFreshParentRetiresExistingPhysicalConnection() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.setup()
            f.connected()
            val peer = f.peers.single()
            val stale = mapChainLocalToChain(f.latest.remove("a")!!)
            assertTrue(runCatching { f.pool.setupConnection(stale) }.isFailure)
            withTimeout(5000) { while (f.pool.getConnectionOrNull("a") != null) yield() }
            verify(peer.socket, timeout(5000)).disconnect()
        }
    }

    @Test fun cancelledQueuedConfigurationRetiresPriorConnectionWithoutTouchingOtherChain() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.latest["b"] = local("b")
            f.setup("a")
            f.connected()
            val old = f.peers.single()
            val desired = ConnectionPool::class.java.getDeclaredField("desired").also {
                it.isAccessible = true
            }.get(f.pool) as Map<*, *>
            val firstIntent = desired["a"]
            val gate = Gate()
            f.beforeRead = { if (it == "b")gate.hold() }
            val other = async(Dispatchers.Default) { f.setup("b") }
            gate.await()
            val queued = async(Dispatchers.Default) { f.setup("a") }
            withTimeout(5000) { while (desired["a"] === firstIntent)yield() }
            queued.cancel()
            runCatching { queued.await() }
            gate.release.countDown()
            other.await()
            withTimeout(5000) { while (f.pool.getConnectionOrNull("a") != null)yield() }
            verify(old.socket, timeout(5000)).disconnect()
            assertNotNull(f.pool.getConnectionOrNull("b"))
        }
    }

    @Test fun statusTrackerPublishesFreshChainMetadataWithTheCurrentLogicalEndpoint() = runBlocking<Unit> {
        Fixture().use { f ->
            val tracker = jp.co.soramitsu.runtime.multiNetwork.ChainsStateTracker
            tracker.state.value = emptyMap()
            f.latest["a"] = local()
            f.setup()
            f.connected()
            withTimeout(5000) { while (tracker.state.value["a"]?.chain?.name != "a")yield() }
            f.latest["a"] = local().let { it.copy(chain = it.chain.copy(name = "Fresh")) }
            f.setup()
            withTimeout(5000) {
                while (tracker.state.value["a"]?.chain?.name != "Fresh" || tracker.state.value["a"]?.connectionStatus != jp.co.soramitsu.runtime.multiNetwork.ChainState.ConnectionStatus.Connected("wss://a.invalid/first"))yield()
            }
            assertEquals(1, f.peers.size)
            assertEquals(jp.co.soramitsu.runtime.multiNetwork.ChainState.ConnectionStatus.Connected("wss://a.invalid/first"), tracker.state.value.getValue("a").connectionStatus)
        }
    }

    private fun local(id: String = "a", selected: String = "first") = JoinedChainInfo(
        ChainLocal(id, null, null, null, id, null, "", null, 42, false, false, false, false, false, false, false, false, null, "Substrate", null, null, null, null),
        listOf("first", "second").map {
            ChainNodeLocal(id, "wss://$id.invalid/$it", it, it == selected, true)
        },
        emptyList(),
        emptyList()
    )

    @Test fun abandonedIntentWithoutRoomEmissionRequalifiesCurrentDemand() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val selected = connection.selectedNode!!
            assertTrue(connection.runtime.value.isCurrentFor(selected))
            val operation = f.dao.endpointMutationLane.admit(listOf("a"))
            assertFalse(connection.runtime.value.isCurrentFor(selected))
            f.dao.endpointMutationLane.abandon(operation)
            // No row changes and no replacement setup call: abandoning the action must not
            // leave the still-demanded durable selection permanently without current status.
            withTimeout(2_000) {
                while (!connection.runtime.value.isCurrentFor(selected) || connection.runtime.value.phase != EndpointRuntimeStatus.Phase.CONNECTED) yield()
            }
            assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, connection.runtime.value.phase)
            assertEquals(listOf("wss://a.invalid/first"), f.peers.map { it.url })
        }
    }

    @Test fun abandonedCatalogWithoutRoomEmissionRequalifiesBothExistingDemands() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.latest["b"] = local("b")
            val a = f.setup("a")
            f.connected()
            val b = f.setup("b")
            f.connected()
            assertTrue(a.runtime.value.isCurrentFor(a.selectedNode!!))
            assertTrue(b.runtime.value.isCurrentFor(b.selectedNode!!))
            val operation = f.dao.endpointMutationLane.admitCatalogReplacement()
            assertFalse(a.runtime.value.isCurrentFor(a.selectedNode!!))
            assertFalse(b.runtime.value.isCurrentFor(b.selectedNode!!))
            f.dao.endpointMutationLane.abandon(operation)
            withTimeout(2_000) {
                while (!a.runtime.value.isCurrentFor(a.selectedNode!!) || !b.runtime.value.isCurrentFor(b.selectedNode!!) || a.runtime.value.phase != EndpointRuntimeStatus.Phase.CONNECTED || b.runtime.value.phase != EndpointRuntimeStatus.Phase.CONNECTED) yield()
            }
            assertEquals(2, f.peers.size)
        }
    }

    @Test fun abandonedIntentWithoutDemandNeverCreatesTransport() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val operation = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(operation)
            assertNull(f.pool.getConnectionOrNull("a"))
            assertTrue(f.peers.isEmpty())
        }
    }

    @Test fun pendingIntentCannotRequalifyBeforeItSettles() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val read = CountDownLatch(1)
            f.beforeRead = { read.countDown() }
            val operation = f.dao.endpointMutationLane.admit(listOf("a"))
            check(read.await(5, TimeUnit.SECONDS))
            assertFalse(connection.runtime.value.isCurrentFor(connection.selectedNode!!))
            assertEquals(1, f.peers.size)
            f.dao.endpointMutationLane.abandon(operation)
            withTimeout(5000) { while (!connection.runtime.value.isCurrentFor(connection.selectedNode!!)) yield() }
            assertEquals(1, f.peers.size)
        }
    }

    @Test fun noWriteRequalificationPreservesStoppedAndForbiddenWithoutConnect() = runBlocking<Unit> {
        for (permission in listOf(ChainConnection.ExternalRequirement.STOPPED, ChainConnection.ExternalRequirement.FORBIDDEN)) {
            Fixture(permission).use { f ->
                f.latest["a"] = local()
                val connection = f.setup()
                val operation = f.dao.endpointMutationLane.admit(listOf("a"))
                f.dao.endpointMutationLane.abandon(operation)
                withTimeout(5000) {
                    while (!connection.runtime.value.isCurrentFor(connection.selectedNode!!) || connection.runtime.value.phase != EndpointRuntimeStatus.Phase.PAUSED) yield()
                }
                assertEquals(EndpointRuntimeStatus.Phase.PAUSED, connection.runtime.value.phase)
                assertTrue(f.peers.isEmpty())
            }
        }
    }

    @Test fun removalDuringHeldRefreshCannotReapplyOrCreateDemand() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.setup()
            f.connected()
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val operation = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(operation)
            gate.await()
            f.pool.removeConnection("a")
            f.beforeRead = {}
            gate.release.countDown()
            withTimeout(5000) { while (f.pool.getConnectionOrNull("a") != null) yield() }
            assertEquals(1, f.peers.size)
            verify(f.peers.single().socket, timeout(5000)).disconnect()
        }
    }

    @Test fun newerIntentDuringHeldRefreshKeepsHealthyPeerUntilFreshRead() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val healthyPeer = f.peers.single()
            val healthyState = connection.socketService.configurationSnapshot().state
            assertTrue(healthyState is jp.co.soramitsu.fearless_utils.wsrpc.state.SocketStateMachine.State.Connected)
            val gate = Gate()
            f.beforeRead = { gate.hold() }
            val first = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(first)
            gate.await()
            val second = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(second)
            assertSame(healthyPeer, f.peers.single())
            assertSame(healthyState, connection.socketService.configurationSnapshot().state)
            verify(healthyPeer.socket, never()).disconnect()
            f.beforeRead = {}
            gate.release.countDown()
            val acknowledged = withTimeout(5000) {
                connection.runtime.first {
                    it.isCurrentFor(connection.selectedNode!!) && it.applied && it.phase == EndpointRuntimeStatus.Phase.CONNECTED
                }
            }
            assertTrue(acknowledged.applied)
            assertSame(healthyPeer, f.peers.single())
            assertSame(healthyState, connection.socketService.configurationSnapshot().state)
            assertEquals(EndpointRuntimeStatus.Phase.CONNECTED, connection.runtime.value.phase)
            assertEquals(1, f.peers.size)
            verify(f.peers.single().socket, never()).disconnect()
        }
    }

    @Test fun completedPhysicalMutationRefreshesOnlyAfterWriterLaneRelease() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val lane = f.dao.endpointMutationLane
            val operation = lane.admit(listOf("a"))
            val gate = Gate()
            val commit = async(Dispatchers.Default) {
                lane.withOperation(operation) {
                    f.latest["a"] = local(selected = "second")
                    grantFixtureFinal(lane, operation)
                    gate.hold()
                }
            }
            gate.await()
            assertEquals(1, f.peers.size)
            assertFalse(connection.runtime.value.isCurrentFor(connection.selectedNode!!))
            gate.release.countDown()
            commit.await()
            withTimeout(5000) { while (f.peers.size != 2) yield() }
            f.connected()
            withTimeout(5000) { while (!connection.runtime.value.isCurrentFor(connection.selectedNode!!)) yield() }
            assertEquals("wss://a.invalid/second", connection.selectedNode!!.url)
            assertEquals(listOf("wss://a.invalid/first", "wss://a.invalid/second"), f.peers.map { it.url })
        }
    }

    @Test fun failedRefreshStopsOldPeerButLaterValidIntentCanRequalifyDemand() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            f.latest.remove("a")
            val first = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(first)
            withTimeout(5000) { while (connection.runtime.value.phase != EndpointRuntimeStatus.Phase.UNAVAILABLE) yield() }
            verify(f.peers.single().socket, timeout(5000)).disconnect()
            f.latest["a"] = local(selected = "second")
            val second = f.dao.endpointMutationLane.admit(listOf("a"))
            f.dao.endpointMutationLane.abandon(second)
            withTimeout(5000) { while (f.peers.size != 2) yield() }
            f.connected()
            withTimeout(5000) { while (!connection.runtime.value.isCurrentFor(connection.selectedNode!!)) yield() }
            assertEquals("wss://a.invalid/second", connection.selectedNode!!.url)
        }
    }

    @Test fun unrelatedIntentDoesNotRefreshHealthyChainOrOpenUndemandedChain() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val status = connection.runtime.value
            f.beforeRead = { error("Unrelated intent must not re-read a current demanded chain") }
            val operation = f.dao.endpointMutationLane.admit(listOf("b"))
            f.dao.endpointMutationLane.abandon(operation)
            assertTrue(status.isCurrentFor(connection.selectedNode!!))
            assertNull(f.pool.getConnectionOrNull("b"))
            assertEquals(1, f.peers.size)
        }
    }

    @Test fun laneRetirementStopsExistingPeerAndCannotReconnect() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            f.dao.endpointMutationLane.retire()
            withTimeout(5000) { while (connection.runtime.value.phase != EndpointRuntimeStatus.Phase.UNAVAILABLE) yield() }
            verify(f.peers.single().socket, timeout(5000)).disconnect()
            assertEquals(1, f.peers.size)
            assertTrue(runCatching { f.dao.endpointMutationLane.admit(listOf("a")) }.isFailure)
        }
    }

    @Test fun observingRuntimeWithoutDemandDoesNotReadOrStartAConnection() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val seen = CopyOnWriteArrayList<EndpointRuntimeStatus?>()
            val observation = launch(Dispatchers.Unconfined) { f.pool.runtimeStatus("a").collect { seen.add(it) } }
            try {
                withTimeout(5000) { while (seen.isEmpty()) yield() }
                assertNull(seen.last())
                assertNull(f.pool.getConnectionOrNull("a"))
                assertTrue(f.peers.isEmpty())
                verify(f.dao, never()).getJoinChainInfo(anyString())
                val intent = f.dao.endpointMutationLane.admit(listOf("a"))
                f.dao.endpointMutationLane.abandon(intent)
                yield()
                assertTrue(f.peers.isEmpty())
                assertNull(f.pool.getConnectionOrNull("a"))
            } finally {
                observation.cancelAndJoin()
            }
        }
    }

    @Test fun runtimeObserverFollowsExactAcknowledgmentAndDemandRemovalWithoutResurrection() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val seen = CopyOnWriteArrayList<EndpointRuntimeStatus?>()
            val observation = launch(Dispatchers.Unconfined) { f.pool.runtimeStatus("a").collect { seen.add(it) } }
            try {
                val connection = f.setup()
                f.connected()
                withTimeout(5000) { while (seen.lastOrNull()?.phase != EndpointRuntimeStatus.Phase.CONNECTED) yield() }
                assertTrue(seen.last()!!.isCurrentFor(connection.selectedNode!!))
                f.pool.removeConnection("a")
                withTimeout(5000) { while (seen.lastOrNull() != null || f.pool.getConnectionOrNull("a") != null) yield() }
                val intent = f.dao.endpointMutationLane.admit(listOf("a"))
                f.dao.endpointMutationLane.abandon(intent)
                yield()
                assertEquals(1, f.peers.size)
                assertNull(f.pool.getConnectionOrNull("a"))
            } finally {
                observation.cancelAndJoin()
            }
        }
    }

    @Test fun cancelingRuntimeObservationDoesNotRetireExistingDemand() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            val connection = f.setup()
            f.connected()
            val observed = withTimeout(5000) {
                f.pool.runtimeStatus("a").first { it?.phase == EndpointRuntimeStatus.Phase.CONNECTED }
            }
            assertTrue(observed!!.isCurrentFor(connection.selectedNode!!))
            assertSame(connection, f.pool.getConnectionOrNull("a"))
            assertEquals(1, f.peers.size)
            verify(f.peers.single().socket, never()).disconnect()
        }
    }

    @Test
    fun explicitUnknownFreshTargetCannotCreateSubstrateTransport() = runBlocking<Unit> {
        Fixture().use { f ->
            val caller = mapChainLocalToChain(local())
            f.latest["a"] = local().let { it.copy(chain = it.chain.copy(ecosystem = "FUTURE_UNSUPPORTED")) }
            val failure = runCatching { f.pool.setupConnection(caller) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(f.peers.isEmpty())
            assertNull(f.pool.getConnectionOrNull("a"))
        }
    }

    @Test
    fun unsupportedCommittedEcosystemRetiresTransportWithoutDisconnectingHealthyPeer() = runBlocking<Unit> {
        Fixture().use { f ->
            f.latest["a"] = local()
            f.latest["b"] = local("b")
            val retired = f.setup("a")
            f.connected()
            val retiredPeer = f.peers.last()
            val healthy = f.setup("b")
            f.connected()
            val healthyPeer = f.peers.last()
            val lane = f.dao.endpointMutationLane
            val operation = lane.admit(listOf("a"))
            lane.withOperation(operation) {
                f.latest["a"] = local().let { it.copy(chain = it.chain.copy(ecosystem = "FUTURE_UNSUPPORTED")) }
                grantFixtureFinal(lane, operation)
            }
            withTimeout(5000) {
                while (retired.runtime.value.phase != EndpointRuntimeStatus.Phase.UNAVAILABLE ||
                    healthy.runtime.value.phase != EndpointRuntimeStatus.Phase.CONNECTED) yield()
            }
            verify(retiredPeer.socket, timeout(5000)).disconnect()
            verify(healthyPeer.socket, never()).disconnect()
            assertSame(healthy, f.pool.getConnectionOrNull("b"))
            assertTrue(healthy.runtime.value.isCurrentFor(healthy.selectedNode!!))
            assertEquals(2, f.peers.size)
        }
    }
}
