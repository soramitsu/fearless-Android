package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList

class WcRelayClientTest {
    private val relay = WcTestRelay()
    private val clock = WcTestClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val delivered = CopyOnWriteArrayList<WcRelayMessage>()
    private val identity = WcClientIdentity(ByteArray(32) { 1 })
    private val topic = "ab".repeat(32)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `connects with the client auth jwt, project id, user agent and package`() {
        val client = client()
        client.watch(listOf(topic))

        eventually { relay.current != null }
        val url = URI(relay.urls.single())
        val query = url.rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals("wss://relay.one", "${url.scheme}://${url.host}")
        assertEquals(PROJECT_ID, query["projectId"])
        assertEquals("wc-2/test", query["ua"])
        assertEquals("true", query["useOnCloseEvent"])
        assertEquals("jp.co.soramitsu.fearless", query["packageName"])
        val payload = String(WcEncoding.unbase64Url(query.getValue("auth").split('.')[1]))
        assertTrue(payload.contains("\"iss\":\"${identity.clientId}\""))
        assertTrue(payload.contains("\"aud\":\"wss://relay.one\""))
    }

    @Test
    fun `fails over to the next relay url when one cannot be reached`() {
        relay.failNextConnects = 1
        val client = client(backoff = { 10 })
        client.watch(listOf(topic))

        eventually { relay.current != null }
        assertTrue(relay.urls[0].startsWith("wss://relay.one?"))
        assertTrue(relay.urls[1].startsWith("wss://relay.two?"))
        assertTrue(topic in relay.subscriptions)
    }

    @Test
    fun `acknowledges deliveries with the relay's exact id and hands them over in order`() {
        val client = client()
        runBlocking { client.subscribe(topic) }

        relay.push(topic, "first", attestation = "jwt")
        relay.push(topic, "second")

        eventually { delivered.size == 2 }
        assertEquals(listOf("first", "second"), delivered.map { it.message })
        assertEquals("jwt", delivered[0].attestation)
        assertEquals(listOf("18000000000000000001", "18000000000000000002"), relay.acknowledged.map { it.toString() })
    }

    @Test
    fun `deliveries for topics the wallet left are acknowledged and dropped`() {
        val client = client()
        val kept = "cd".repeat(32)
        runBlocking { client.subscribe(kept) }
        runBlocking { client.subscribe(topic) }
        runBlocking { client.unsubscribe(topic) }
        relay.subscriptions += topic

        relay.push(topic, "late")

        eventually { relay.acknowledged.size == 1 }
        assertTrue(delivered.isEmpty())
        assertFalse(client.isWatching(topic))
    }

    @Test
    fun `publishes with the given tag and ttl`() {
        val client = client()
        runBlocking { client.publish(topic, "message", ttlSeconds = 300, tag = 1109, prompt = true) }

        val published = relay.published.single()
        assertEquals(topic, published.topic)
        assertEquals(300, published.ttl)
        assertEquals(1109, published.tag)
        assertTrue(published.prompt)
    }

    @Test
    fun `publishing fails when no relay can be reached`() {
        relay.failNextConnects = Int.MAX_VALUE
        val client = client(backoff = { 10 })

        assertThrows(WcRelayException::class.java) { runBlocking { client.publish(topic, "m", 300, 1109) } }
    }

    @Test
    fun `the connection closes once the app stays in the background and reopens in the foreground`() {
        val client = client()
        client.watch(listOf(topic))
        eventually { relay.current != null }

        client.setForeground(false)
        clock.advanceSeconds(1)
        eventually { relay.current == null }

        client.setForeground(true)
        eventually { relay.current != null && topic in relay.subscriptions }
    }

    @Test
    fun `a dropped connection is reopened and every watched topic resubscribed`() {
        val client = client(backoff = { 10 })
        client.watch(listOf(topic, "cd".repeat(32)))
        eventually { relay.current != null }
        relay.subscriptions.clear()

        relay.dropConnection()

        eventually { relay.current != null && relay.subscriptions.size == 2 }
        assertTrue(relay.calls.count { it == WcRelayClient.METHOD_BATCH_SUBSCRIBE } >= 2)
    }

    @Test
    fun `backoff grows and stays capped`() {
        assertEquals(1_000, WcRelayClient.defaultBackoff(1))
        assertEquals(60_000, WcRelayClient.defaultBackoff(6))
        assertEquals(60_000, WcRelayClient.defaultBackoff(60))
    }

    @Test
    fun `a project id must be 32 hex characters`() {
        assertTrue(WcRelayConfig(PROJECT_ID, null, "ua").isValid)
        assertFalse(WcRelayConfig("", null, "ua").isValid)
        assertFalse(WcRelayConfig("x".repeat(32), null, "ua").isValid)
    }

    private fun client(backoff: (Int) -> Long = WcRelayClient::defaultBackoff) = WcRelayClient(
        transport = relay,
        config = WcRelayConfig(
            projectId = PROJECT_ID,
            packageName = "jp.co.soramitsu.fearless",
            userAgent = "wc-2/test",
            relayUrls = listOf("wss://relay.one", "wss://relay.two"),
            connectTimeoutMillis = 300,
            requestTimeoutMillis = 1_000,
            backgroundGraceMillis = 100
        ),
        identity = { identity },
        clock = clock,
        scope = scope,
        onMessage = { delivered += it },
        backoffMillis = backoff
    ).also { it.start() }

    private companion object {
        const val PROJECT_ID = "0123456789abcdef0123456789abcdef"
    }
}
