package jp.co.soramitsu.runtime.multiNetwork.connection

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import jp.co.soramitsu.common.domain.NetworkStateService
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.configurator.EthereumEnvironmentConfigurator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val CHAIN_ID = "31337"
private const val EVM_CHAIN_ID = 31_337L
private const val OLD_BLOCK = 0x11L
private const val NEW_BLOCK = 0x22L
private const val READS_AFTER_REFRESH = 3
private const val CONCURRENT_CALLERS = 8

// A late event from a retired connection follows its node's answer within milliseconds.
private const val LATE_EVENT_GRACE_MILLIS = 750L

// The pool waits one second between node attempts, so a retry that survived would land in this window.
private const val RETRY_WINDOW_MILLIS = 2_500L

/**
 * A signed catalog update can change an EVM chain's RPC URLs while the wallet is running.
 * ChainRegistry then calls setupConnection again with the chain as the catalog now lists it.
 * These tests make that call against loopback nodes and follow real eth_blockNumber reads.
 */
class EthereumConnectionPoolRefreshTest {
    private val networkState = NetworkStateService()
    private val pool = EthereumConnectionPool(networkState)
    private val nodes = mutableListOf<LoopbackEvmNode>()
    private val ignoreSelection: (String, String) -> Unit = { _, _ -> }

    @Before
    fun mockAndroidLoggingOnWorkerThreads() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun stopPoolAndNodesBeforeRestoringAndroidLogging() {
        try {
            pool.stop(CHAIN_ID)
            val failures = nodes.mapNotNull { runCatching { it.close() }.exceptionOrNull() }
            failures.firstOrNull()?.let { throw it }
        } finally {
            unmockkStatic(Log::class)
        }
    }

    @Test
    fun `unchanged ordered URLs keep the live connection through catalog replays`() {
        val first = node(OLD_BLOCK)
        val fallback = node(NEW_BLOCK)
        val selections = Selections()
        val catalog = chain(first.url, fallback.url)
        val live = pool.setupConnection(catalog, selections.callback)
        awaitBlock(OLD_BLOCK, "The first listed node did not serve the initial read")
        val client = live.web3j

        val replayed = pool.setupConnection(catalog.copy(nodes = catalog.nodes.map { it.copy() }), ignoreSelection)
        val relabelled = pool.setupConnection(
            catalog.copy(name = "Relabelled", rank = 7, nodes = catalog.nodes.map { it.copy(name = "Relabelled") }),
            ignoreSelection
        )
        // The registry also calls setup after a node-selection write, which changes only these flags.
        val reselected = pool.setupConnection(
            catalog.copy(nodes = catalog.nodes.map { it.copy(isActive = !it.isActive) }),
            ignoreSelection
        )

        assertSame("An identical catalog replay replaced the live connection", live, replayed)
        assertSame("A change of display names alone replaced the live connection", live, relabelled)
        assertSame("A node-selection write replaced the live connection", live, reselected)
        assertSame(live, pool.getOrNull(CHAIN_ID))
        assertSame("The live connection reconnected although its URLs did not change", client, live.web3j)
        assertEquals(OLD_BLOCK, readBlock())
        assertEquals("The fallback node was contacted although the first node works", 0, fallback.requests.get())
        awaitSelections(selections, first.url)
    }

    @Test
    fun `a list reorder that keeps the effective order keeps the live connection`() {
        val http = ChainNode(node(OLD_BLOCK).url, "http", isActive = true, isDefault = true)
        val unreachableWebSocket = ChainNode("wss://127.0.0.1:${closedPort()}/", "ws", isActive = false, isDefault = true)
        val selections = Selections()
        // The pool tries WebSocket nodes first wherever the catalog lists them, so both lists give one order.
        val live = pool.setupConnection(chain(listOf(http, unreachableWebSocket)), selections.callback)
        awaitBlock(OLD_BLOCK, "The HTTP node did not take over from the unreachable WebSocket node")
        val client = live.web3j

        val reordered = pool.setupConnection(chain(listOf(unreachableWebSocket, http)), ignoreSelection)

        assertSame("A reorder with the same effective order replaced the live connection", live, reordered)
        assertSame("The live connection reconnected although its effective order did not change", client, live.web3j)
        assertEquals(OLD_BLOCK, readBlock())
        awaitSelections(selections, http.url)
    }

    @Test
    fun `a changed URL replaces the live connection and the next read reaches the replacement`() {
        val old = node(OLD_BLOCK)
        val replacement = node(NEW_BLOCK)
        val oldSelections = Selections()
        val newSelections = Selections()
        val prior = pool.setupConnection(chain(old.url), oldSelections.callback)
        awaitBlock(OLD_BLOCK, "The original node did not serve the initial read")
        awaitSelections(oldSelections, old.url)

        // No stop and no new pool: the same call ChainRegistry makes for a modified chain.
        val refreshed = pool.setupConnection(chain(replacement.url), newSelections.callback)

        awaitBlock(NEW_BLOCK, "eth_blockNumber did not reach the replacement node without a restart")
        assertSame("The pool hands out a connection other than the one setup returned", refreshed, pool.getOrNull(CHAIN_ID))
        assertSame(refreshed, runBlocking { withTimeout(CONVERGENCE_MILLIS) { pool.await(CHAIN_ID) } })
        assertNotSame("The connection built for the retired URL is still the pooled one", prior, refreshed)

        val retiredRequests = old.requests.get()
        repeat(READS_AFTER_REFRESH) { assertEquals(NEW_BLOCK, readBlock()) }
        assertEquals("The retired node still received reads", retiredRequests, old.requests.get())

        old.close()
        assertEquals("A read failed once the retired node went offline", NEW_BLOCK, readBlock())
        awaitSelections(newSelections, replacement.url)
        assertEquals(listOf(CHAIN_ID to old.url), oldSelections.seen.toList())
        assertTrue("The refreshed chain is still reported as having a node problem", eventually { nodeIssue() == null })
    }

    @Test
    fun `a reordered URL list moves reads to the new first node`() {
        val demoted = node(OLD_BLOCK)
        val promoted = node(NEW_BLOCK)

        assertRefreshMovesReads(
            before = chain(demoted.url, promoted.url),
            after = chain(promoted.url, demoted.url),
            from = demoted,
            to = promoted
        )
    }

    @Test
    fun `a connected node that disappears from the list is dropped for the survivor`() {
        val retired = node(OLD_BLOCK)
        val survivor = node(NEW_BLOCK)

        assertRefreshMovesReads(
            before = chain(retired.url, survivor.url),
            after = chain(survivor.url),
            from = retired,
            to = survivor
        )
    }

    @Test
    fun `the EVM configurator applies a changed catalog list to the live pool`() {
        val old = node(OLD_BLOCK)
        val replacement = node(NEW_BLOCK)
        val configurator = EthereumEnvironmentConfigurator(pool)
        runBlocking { configurator.configure(chain(old.url)) }
        awaitBlock(OLD_BLOCK, "The original node did not serve the initial read")

        runBlocking { configurator.configure(chain(replacement.url)) }

        awaitBlock(NEW_BLOCK, "The registry's setup path did not move reads to the replacement node")
        old.close()
        assertEquals("A read failed once the retired node went offline", NEW_BLOCK, readBlock())
    }

    @Test
    fun `stop removes the connection and the next setup opens a fresh one`() {
        val old = node(OLD_BLOCK)
        val replacement = node(NEW_BLOCK)
        val stopped = pool.setupConnection(chain(old.url), ignoreSelection)
        awaitBlock(OLD_BLOCK, "The original node did not serve the initial read")

        pool.stop(CHAIN_ID)
        assertNull(pool.getOrNull(CHAIN_ID))
        val reopened = pool.setupConnection(chain(old.url), ignoreSelection)

        assertNotSame("Setup after stop returned the stopped connection", stopped, reopened)
        awaitBlock(OLD_BLOCK, "A connection reopened with the same URL did not serve reads")

        pool.stop(CHAIN_ID)
        val moved = pool.setupConnection(chain(replacement.url), ignoreSelection)

        assertNotSame(reopened, moved)
        awaitBlock(NEW_BLOCK, "A connection reopened with the replacement URL did not reach it")
    }

    @Test
    fun `a refresh during an unanswered probe silences the late answer of the retired node`() {
        val retired = node(OLD_BLOCK)
        val replacement = node(NEW_BLOCK)
        val retiredSelections = Selections()
        val currentSelections = Selections()
        retired.holdNextRequest()
        pool.setupConnection(chain(retired.url), retiredSelections.callback)
        assertTrue("The first probe never reached the original node", retired.awaitHeldRequest())

        pool.setupConnection(chain(replacement.url), currentSelections.callback)
        awaitBlock(NEW_BLOCK, "eth_blockNumber did not reach the replacement while the old probe was pending")
        awaitSelections(currentSelections, replacement.url)
        assertTrue(eventually { nodeIssue() == null })
        retired.release()
        assertTrue("The original node never finished with its held probe", retired.awaitSettled(1))
        Thread.sleep(LATE_EVENT_GRACE_MILLIS)

        assertEquals("The retired node was reported as selected after the refresh", emptyList<Any>(), retiredSelections.seen.toList())
        assertEquals(listOf(CHAIN_ID to replacement.url), currentSelections.seen.toList())
        assertEquals("The retired node was contacted again after its probe", 1, retired.requests.get())
        assertEquals(NEW_BLOCK, readBlock())
        assertNull(nodeIssue())
    }

    @Test
    fun `a refresh while the retired node is failing ends its retries and keeps the chain healthy`() {
        val retired = node(OLD_BLOCK).apply { healthy = false }
        val replacement = node(NEW_BLOCK)
        val retiredSelections = Selections()
        retired.holdNextRequest()
        pool.setupConnection(chain(retired.url), retiredSelections.callback)
        assertTrue("The first probe never reached the original node", retired.awaitHeldRequest())

        pool.setupConnection(chain(replacement.url), ignoreSelection)
        awaitBlock(NEW_BLOCK, "eth_blockNumber did not reach the replacement while the old probe was pending")
        assertTrue(eventually { nodeIssue() == null })
        retired.release()

        assertFalse("The retired node was probed again after the refresh", retired.awaitRequests(2, RETRY_WINDOW_MILLIS))
        assertNull("A failure of the retired node was reported against the refreshed chain", nodeIssue())
        assertEquals(emptyList<Any>(), retiredSelections.seen.toList())
        assertEquals(NEW_BLOCK, readBlock())
    }

    @Test
    fun `stop during an unanswered probe ends the connection without trying its fallback`() {
        val failing = node(OLD_BLOCK).apply { healthy = false }
        val fallback = node(NEW_BLOCK)
        val selections = Selections()
        failing.holdNextRequest()
        pool.setupConnection(chain(failing.url, fallback.url), selections.callback)
        assertTrue("The first probe never reached the first node", failing.awaitHeldRequest())

        pool.stop(CHAIN_ID)
        failing.release()

        assertFalse("A stopped connection moved on to its fallback node", fallback.awaitRequests(1, RETRY_WINDOW_MILLIS))
        assertEquals("A stopped connection probed its first node again", 1, failing.requests.get())
        assertEquals("A stopped connection reported a selected node", emptyList<Any>(), selections.seen.toList())
        assertNull(pool.getOrNull(CHAIN_ID))
    }

    @Test
    fun `concurrent setups with the refreshed list share one replacement connection`() {
        val old = node(OLD_BLOCK)
        val replacement = node(NEW_BLOCK)
        pool.setupConnection(chain(old.url), ignoreSelection)
        awaitBlock(OLD_BLOCK, "The original node did not serve the initial read")
        val refreshed = chain(replacement.url)
        val selections = Selections()
        val start = CountDownLatch(1)
        val callers = Executors.newFixedThreadPool(CONCURRENT_CALLERS)

        val connections = try {
            val pending = List(CONCURRENT_CALLERS) {
                callers.submit<EthereumChainConnection> {
                    start.await()
                    pool.setupConnection(refreshed, selections.callback)
                }
            }
            start.countDown()
            pending.map { it.get(CONVERGENCE_MILLIS, TimeUnit.MILLISECONDS) }
        } finally {
            callers.shutdownNow()
        }

        awaitBlock(NEW_BLOCK, "eth_blockNumber did not reach the replacement after concurrent setups")
        connections.forEach { assertSame("Concurrent callers received different connections", connections.first(), it) }
        assertSame(connections.first(), pool.getOrNull(CHAIN_ID))
        assertTrue(eventually { selections.seen.isNotEmpty() })
        Thread.sleep(LATE_EVENT_GRACE_MILLIS)
        assertEquals(
            "More than one connection was opened for the refreshed list",
            listOf(CHAIN_ID to replacement.url),
            selections.seen.toList()
        )
    }

    private fun assertRefreshMovesReads(
        before: Chain,
        after: Chain,
        from: LoopbackEvmNode,
        to: LoopbackEvmNode
    ) {
        pool.setupConnection(before, ignoreSelection)
        awaitBlock(OLD_BLOCK, "The first listed node did not serve the initial read")
        val selections = Selections()

        pool.setupConnection(after, selections.callback)

        awaitBlock(NEW_BLOCK, "eth_blockNumber did not move to the node the refreshed list puts first")
        val earlierRequests = from.requests.get()
        repeat(READS_AFTER_REFRESH) { assertEquals(NEW_BLOCK, readBlock()) }
        assertEquals("The node that lost first place still received reads", earlierRequests, from.requests.get())
        awaitSelections(selections, to.url)
        assertTrue("The refreshed chain is still reported as having a node problem", eventually { nodeIssue() == null })
    }

    private fun node(block: Long) = LoopbackEvmNode(block, EVM_CHAIN_ID).also { nodes += it }

    private fun closedPort() = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    private fun chain(vararg urls: String) = chain(
        urls.mapIndexed { index, url -> ChainNode(url, "rpc $index", isActive = index == 0, isDefault = true) }
    )

    private fun chain(nodes: List<ChainNode>) = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Catalog observer",
        minSupportedVersion = null,
        assets = emptyList(),
        nodes = nodes,
        explorers = emptyList(),
        externalApi = null,
        icon = "",
        addressPrefix = 42,
        isEthereumBased = false,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = true,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Ethereum,
        remoteAssetsSource = null
    )

    private fun readBlock(): Long? {
        val web3j = pool.getOrNull(CHAIN_ID)?.web3j ?: return null
        return runCatching { web3j.ethBlockNumber().send().blockNumber.toLong() }.getOrNull()
    }

    private fun awaitBlock(expected: Long, failure: String) {
        eventually { readBlock() == expected }
        assertEquals(failure, expected, readBlock())
    }

    private fun awaitSelections(selections: Selections, vararg urls: String) {
        val expected = urls.map { CHAIN_ID to it }
        eventually { selections.seen == expected }
        assertEquals("Unexpected nodes reported through onSelectedNodeChange", expected, selections.seen.toList())
    }

    private fun nodeIssue() = runBlocking { networkState.networkIssuesFlow.first() }[CHAIN_ID]

    /** Records what a connection reports through onSelectedNodeChange. */
    private class Selections {
        val seen = CopyOnWriteArrayList<Pair<String, String>>()
        val callback: (String, String) -> Unit = { chainId, url -> seen += chainId to url }
    }
}
