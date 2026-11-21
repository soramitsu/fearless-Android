package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// Reached only when a test is already failing; the host can be very busy.
private const val WAIT_SECONDS = 30L
private const val POLL_MILLIS = 1L

private fun awaitUntil(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS)
    while (!condition()) {
        check(System.nanoTime() < deadline) { "Condition not met in time" }
        Thread.sleep(POLL_MILLIS)
    }
}

/**
 * Kotlin's toList() reads a collection's size and, with one entry, then calls iterator().next(). On
 * a concurrent collection that throws if another thread removed the entry in between. Nothing in the
 * connection loop catches that exception, so it would close the app. These tests force that moment.
 */
class WcRelayClientRaceTest {
    private val relay = WcTestRelay()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error -> uncaught += error }
    )
    private val topic = "ab".repeat(32)
    private val client = WcRelayClient(
        transport = relay,
        config = WcRelayConfig(
            projectId = "0123456789abcdef0123456789abcdef",
            packageName = null,
            userAgent = "wc-2/test",
            relayUrls = listOf("wss://relay.one"),
            connectTimeoutMillis = TimeUnit.SECONDS.toMillis(WAIT_SECONDS),
            // Longer than awaitUntil waits: a request that fails in time did not fail by timing out.
            requestTimeoutMillis = TimeUnit.SECONDS.toMillis(2 * WAIT_SECONDS)
        ),
        identity = { WcClientIdentity(ByteArray(32) { 1 }) },
        clock = WcTestClock(),
        scope = scope,
        onMessage = {},
        backoffMillis = { 10 }
    )

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `leaving the only topic as a connection subscribes keeps the client running`() {
        leaveTheOnlyTopicDuringWatchedRead(1)
    }

    @Test
    fun `leaving the only topic as a connection fetches messages keeps the client running`() {
        leaveTheOnlyTopicDuringWatchedRead(2)
    }

    @Test
    fun `a connection closing as its only request finishes keeps the client running`() {
        val requests = CountReadHookMap<Long, CompletableDeferred<JsonElement>>()
        client.replaceField("pending", requests)
        val requestMayFinish = CountDownLatch(1)
        // The relay has answered by now, and the request is held here, still registered.
        relay.onPublish = {
            requests.onCountRead = {
                requestMayFinish.countDown()
                requests.awaitEmpty()
            }
            relay.dropConnection()
            assertTrue(requestMayFinish.await(WAIT_SECONDS, TimeUnit.SECONDS))
        }
        client.start()

        runBlocking { client.publish(topic, "message", ttlSeconds = 300, tag = 1109) }

        awaitUntil { requests.hookFinished }
        assertTheClientStillConnects()
    }

    @Test
    fun `a request waiting on a connection that closes fails at once`() {
        relay.unanswered += WcRelayClient.METHOD_PUBLISH
        client.start()
        val publishing = scope.async { client.publish(topic, "message", ttlSeconds = 300, tag = 1109) }

        // publish() tries twice, each time on a new connection.
        repeat(2) { attempt ->
            awaitUntil { relay.calls.count { it == WcRelayClient.METHOD_PUBLISH } == attempt + 1 }
            relay.dropConnection()
        }

        awaitUntil { publishing.isCompleted }
        val failure = assertThrows(WcRelayException::class.java) { runBlocking { publishing.await() } }
        assertEquals("The relay connection closed", failure.message)
        assertEquals(emptyList<Throwable>(), uncaught)
    }

    private fun leaveTheOnlyTopicDuringWatchedRead(read: Int) {
        val watched = CountReadHookMap<String, Boolean>(hookOnRead = read)
        client.replaceField("watched", watched.keySet(true))
        // Another thread, as when a dApp session ends while the connection is being set up.
        watched.onCountRead = { runBlocking(Dispatchers.IO) { client.unsubscribe(topic) } }
        client.watch(listOf(topic))

        client.start()

        // With no topic left the connection closes, whichever way the read went.
        awaitUntil { watched.hookFinished && relay.current == null }
        assertTheClientStillConnects()
    }

    private fun assertTheClientStillConnects() {
        val laterTopic = "cd".repeat(32)
        val later = scope.async { client.subscribe(laterTopic) }
        awaitUntil { later.isCompleted || uncaught.isNotEmpty() }
        uncaught.firstOrNull()?.let { throw AssertionError("The relay connection loop died", it) }
        runBlocking { later.await() }
        assertTrue(laterTopic in relay.subscriptions)
    }

    /** Runs a hook after the chosen count read and before the caller gets the count, so it goes stale. */
    private class CountReadHookMap<K : Any, V : Any>(private val hookOnRead: Int = 1) : ConcurrentHashMap<K, V>() {
        private val reads = AtomicInteger()

        @Volatile
        var onCountRead: (() -> Unit)? = null

        @Volatile
        var hookFinished = false

        override val size: Int
            get() = super.size.also { runHook() }

        override fun mappingCount(): Long = super.mappingCount().also { runHook() }

        fun awaitEmpty() = awaitUntil { isEmpty() }

        private fun runHook() {
            val hook = onCountRead ?: return
            if (reads.incrementAndGet() != hookOnRead) return
            onCountRead = null
            hook()
            hookFinished = true
        }
    }

    // Needs no test seam in production code.
    private fun WcRelayClient.replaceField(name: String, value: Any) {
        val field = WcRelayClient::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }
}
