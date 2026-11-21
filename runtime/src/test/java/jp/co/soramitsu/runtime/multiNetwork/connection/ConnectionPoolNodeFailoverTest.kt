package jp.co.soramitsu.runtime.multiNetwork.connection

import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocket
import com.neovisionaries.ws.client.WebSocketError
import com.neovisionaries.ws.client.WebSocketException
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
import jp.co.soramitsu.runtime.storage.NodesSettingsStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.after
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.withSettings
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Provider

/** Auto select moves a demanded chain off a dead catalog node only through the endpoint mutation lane. */
class ConnectionPoolNodeFailoverTest {
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
    private class Fixture(nodes: List<ChainNodeLocal>) : AutoCloseable {
        // Chain "a"'s chain_nodes rows; a row's rowid is its position + 1, since rows are never reordered.
        @Volatile var rows: List<ChainNodeLocal> = nodes
        val peers = CopyOnWriteArrayList<Peer>()
        val reconnects = CopyOnWriteArrayList<Runnable>()
        @Volatile var autoSelect = true
        @Volatile var duringAutomaticWriter: () -> Unit = {}
        val automaticWriters = AtomicInteger()
        val settings = mock(NodesSettingsStorage::class.java)
        val network = mock(NetworkStateService::class.java)
        val permission = MutableStateFlow(ChainConnection.ExternalRequirement.ALLOWED)
        val dao: ChainDao = mock(ChainDao::class.java, withSettings().useConstructor().defaultAnswer { call ->
            fun rowId(index: Int) = if (index < 0) null else index + 1L
            when (call.method.name) {
                // Mockito leaves the coroutine continuation out of the arguments.
                "getJoinChainInfo" -> if (call.arguments.firstOrNull() == "a") info() else null
                "selectNextCatalogNodeTransaction" -> {
                    automaticWriters.incrementAndGet()
                    transaction { call.callRealMethod() }
                }
                "selectNodeTransaction" -> transaction { call.callRealMethod() }
                "selectedNodeRowId" -> rows.withIndex().filter { it.value.isActive }
                    .sortedWith(compareBy<IndexedValue<ChainNodeLocal>>({ it.value.isDefault }, { it.index }))
                    .firstOrNull()?.let { rowId(it.index) }
                "nodeRowId" -> rowId(rows.indexOfFirst { it.url == call.getArgument<String>(1) })
                "isCatalogNodeRowId" -> rows.getOrNull(call.getArgument<Long>(1).toInt() - 1)?.isDefault == true
                "nextCatalogNodeRowId" -> {
                    duringAutomaticWriter()
                    rows.withIndex().firstOrNull { it.value.isDefault && it.index + 1 > call.getArgument<Long>(1) }
                        ?.let { rowId(it.index) }
                }
                "firstDefaultNodeRowId" -> rowId(rows.indexOfFirst { it.isDefault })
                "setSelectedNodeRowId" -> {
                    val id = call.getArgument<Long?>(1)
                    rows = rows.mapIndexed { index, node -> node.copy(isActive = rowId(index) == id) }
                    Unit
                }
                "clearNodeSelection" -> { rows = rows.map { it.copy(isActive = false) }; Unit }
                "makeNodeSelected" -> {
                    rows = rows.map { if (it.url == call.getArgument<String>(1)) it.copy(isActive = true) else it }
                    Unit
                }
                else -> CALLS_REAL_METHODS.answer(call)
            }
        })
        val pool: ConnectionPool

        init {
            `when`(settings.getIsAutoSelectNodes(anyString())).thenAnswer { autoSelect }
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
                // The reconnect backoff fires only when a test calls retry().
                val timer = mock(ScheduledExecutorService::class.java)
                `when`(timer.schedule(any(Runnable::class.java), anyLong(), any(TimeUnit::class.java))).thenAnswer {
                    reconnects += it.getArgument<Runnable>(0)
                    mock(ScheduledFuture::class.java)
                }
                val logger = object : Logger { override fun log(message: String?) {}
                override fun log(throwable: Throwable?) {} }
                SocketService(Gson(), logger, factory, Reconnector(executor = timer), RequestExecutor(mock(ExecutorService::class.java)))
            }
            // Finish the initial requirement replay before a test can publish a socket transition.
            val initialRequirementApplied = CountDownLatch(1)
            val observedPermission = object : MutableStateFlow<ChainConnection.ExternalRequirement> by permission {
                @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
                override suspend fun collect(collector: FlowCollector<ChainConnection.ExternalRequirement>): Nothing =
                    permission.collect(object : FlowCollector<ChainConnection.ExternalRequirement> {
                        override suspend fun emit(value: ChainConnection.ExternalRequirement) {
                            collector.emit(value)
                            initialRequirementApplied.countDown()
                        }
                    })
            }
            pool = ConnectionPool(provider, observedPermission, dao, network, settings)
            check(initialRequirementApplied.await(5, TimeUnit.SECONDS)) { "fixture initial requirement deadline" }
        }

        fun info() = JoinedChainInfo(
            ChainLocal("a", null, null, null, "a", null, "", null, 42, false, false, false, false, false, false, false, false,
                null, "Substrate", null, null, null, null),
            rows, emptyList(), emptyList()
        )

        // Room's generated transaction rolls every statement back when the body throws.
        private fun transaction(body: () -> Any?): Any? {
            val before = rows
            return try {
                body()
            } catch (failure: Throwable) {
                rows = before
                throw failure
            }
        }

        suspend fun setup() = pool.setupConnection(mapChainLocalToChain(info()))
        val selectedUrl: String get() = rows.single { it.isActive }.url

        fun failed(peer: Peer = peers.last()) {
            check(peer.ready.await(5, TimeUnit.SECONDS)) { "fixture socket connect admission deadline" }
            peer.listener.onConnectError(peer.socket, WebSocketException(WebSocketError.SOCKET_CONNECT_ERROR, "synthetic refusal"))
        }
        fun connected(peer: Peer = peers.last()) {
            check(peer.ready.await(5, TimeUnit.SECONDS)) { "fixture socket connect admission deadline" }
            peer.listener.onConnected(peer.socket, mutableMapOf())
        }
        /** The node is dialed and fails [times] in a row, the backoff timer firing between attempts. */
        fun failRepeatedly(times: Int = NODE_SWITCH_FAILED_ATTEMPTS) {
            repeat(times) { attempt ->
                if (attempt > 0) reconnects.last().run()
                failed()
            }
        }
        suspend fun awaitDial(url: String) = withTimeout(5000) { while (peers.lastOrNull()?.url != url) yield() }
        suspend fun awaitConnectedTo(connection: ChainConnection, url: String) = withTimeout(5000) {
            while (connection.selectedNode?.url != url || !connection.runtime.value.isCurrentFor(connection.selectedNode!!) ||
                connection.runtime.value.phase != EndpointRuntimeStatus.Phase.CONNECTED) yield()
        }

        override fun close() {
            // The application pool has process lifetime; the test cancels its private observation scope only.
            val field = ConnectionPool::class.java.getDeclaredField("scope")
            field.isAccessible = true
            runBlocking { (field.get(pool) as CoroutineScope).coroutineContext[Job]?.cancelAndJoin() }
            pool.getConnectionOrNull("a")?.finish()
            dao.endpointMutationLane.retire()
        }
    }

    @Test fun deadFirstNodeSwitchesToTheNextCatalogNode() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            val connection = f.setup()
            f.failRepeatedly()
            f.awaitDial(SECOND)
            f.connected()
            f.awaitConnectedTo(connection, SECOND)
            assertEquals(SECOND, f.selectedUrl)
            assertEquals(listOf(FIRST, FIRST, FIRST, SECOND), f.peers.map { it.url })
            // A working replacement is kept.
            delay(200)
            assertEquals(1, f.automaticWriters.get())
            assertEquals(4, f.peers.size)
        }
    }

    @Test fun everyDeadCatalogNodeIsTriedInRowOrderAndTheListWraps() = runBlocking<Unit> {
        Fixture(nodes(SECOND)).use { f ->
            val connection = f.setup()
            for (next in listOf(THIRD, FIRST, SECOND)) {
                f.failRepeatedly()
                f.awaitDial(next)
                withTimeout(5000) { while (f.selectedUrl != next) yield() }
            }
            f.connected()
            f.awaitConnectedTo(connection, SECOND)
            assertEquals(3, f.automaticWriters.get())
        }
    }

    @Test fun fewerFailuresThanTheThresholdKeepTheNode() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            val connection = f.setup()
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS - 1)
            f.reconnects.last().run()
            f.connected()
            f.awaitConnectedTo(connection, FIRST)
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS - 1)
            verify(f.settings, after(300).never()).getIsAutoSelectNodes(anyString())
            assertEquals(FIRST, f.selectedUrl)
            assertEquals(0, f.automaticWriters.get())
        }
    }

    @Test fun autoSelectOffKeepsTheNodeTheUserChose() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            f.autoSelect = false
            f.setup()
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS + 1)
            // The pool considered the dead node and declined without admitting a write.
            verify(f.settings, timeout(5000).atLeastOnce()).getIsAutoSelectNodes("a")
            delay(200)
            assertEquals(FIRST, f.selectedUrl)
            assertEquals(0, f.automaticWriters.get())
            assertTrue(f.peers.all { it.url == FIRST })
        }
    }

    @Test fun aSelectedCustomNodeIsKeptWhileAutoSelectIsOn() = runBlocking<Unit> {
        Fixture(nodes(selected = null) + ChainNodeLocal("a", CUSTOM, "custom", true, false)).use { f ->
            f.setup()
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS + 1)
            verify(f.settings, after(300).never()).getIsAutoSelectNodes(anyString())
            assertEquals(CUSTOM, f.selectedUrl)
            assertEquals(0, f.automaticWriters.get())
            assertTrue(f.peers.all { it.url == CUSTOM })
        }
    }

    @Test fun aSelectionMadeDuringTheAutomaticWriteWins() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            val connection = f.setup()
            val gate = Gate()
            f.duringAutomaticWriter = { gate.hold() }
            f.failRepeatedly()
            gate.await()
            // The user picks a node while the automatic writer holds the lane.
            val explicit = f.dao.endpointMutationLane.admit(listOf("a"))
            val choice = async(Dispatchers.Default) { f.dao.selectNode(explicit, "a", THIRD) }
            gate.release.countDown()
            choice.await()
            f.awaitDial(THIRD)
            f.connected()
            f.awaitConnectedTo(connection, THIRD)
            assertEquals(THIRD, f.selectedUrl)
            assertEquals(1, f.automaticWriters.get())
            assertTrue(f.peers.none { it.url == SECOND })
        }
    }

    @Test fun aSelectionAdmittedBeforeTheThresholdPreventsTheAutomaticWrite() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            val connection = f.setup()
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS - 1)
            // The user picks a node. The pending intent retires the failing binding, so it is not even redialed.
            val explicit = f.dao.endpointMutationLane.admit(listOf("a"))
            f.reconnects.last().run()
            assertEquals(NODE_SWITCH_FAILED_ATTEMPTS - 1, f.peers.size)
            f.dao.selectNode(explicit, "a", THIRD)
            f.awaitDial(THIRD)
            f.connected()
            f.awaitConnectedTo(connection, THIRD)
            assertEquals(0, f.automaticWriters.get())
            assertTrue(f.peers.none { it.url == SECOND })
        }
    }

    @Test fun aPendingCatalogReplacementIsNeverRetiredByTheSwitch() = runBlocking<Unit> {
        Fixture(nodes(FIRST)).use { f ->
            val lane = f.dao.endpointMutationLane
            // A catalog sync is waiting for the writer while the chain connects from the cached catalog.
            val catalog = lane.admitCatalogReplacement()
            val connection = f.setup()
            f.failRepeatedly(NODE_SWITCH_FAILED_ATTEMPTS + 1)
            verify(f.settings, timeout(5000).atLeastOnce()).getIsAutoSelectNodes("a")
            delay(200)
            assertTrue(lane.isCurrent(catalog))
            assertEquals(0, f.automaticWriters.get())
            // Once the replacement settles, the refreshed binding counts again and fails over.
            lane.abandon(catalog)
            withTimeout(5000) { while (f.peers.size != NODE_SWITCH_FAILED_ATTEMPTS + 2) yield() }
            f.failRepeatedly()
            f.awaitDial(SECOND)
            f.connected()
            f.awaitConnectedTo(connection, SECOND)
            assertEquals(1, f.automaticWriters.get())
        }
    }

    private fun nodes(selected: String?) = listOf(FIRST, SECOND, THIRD).map {
        ChainNodeLocal("a", it, it.substringAfter("//"), it == selected, true)
    }

    private companion object {
        const val FIRST = "wss://first.invalid"
        const val SECOND = "wss://second.invalid"
        const val THIRD = "wss://third.invalid"
        const val CUSTOM = "wss://custom.invalid"
    }
}
