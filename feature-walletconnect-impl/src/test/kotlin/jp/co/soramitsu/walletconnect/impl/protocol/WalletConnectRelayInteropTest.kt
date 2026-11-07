package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.walletconnect.impl.data.OkHttpVerifyKeySource
import jp.co.soramitsu.walletconnect.impl.presentation.Eip191
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.web3j.crypto.Credentials
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Live interoperability with the official JavaScript `@walletconnect/sign-client` as the dApp,
 * over the public relay. Test-only and skipped unless `WC_INTEROP_DAPP_SCRIPT`,
 * `WC_INTEROP_NODE_MODULES` and `WC_INTEROP_PROJECT_ID` are set and the relay is reachable.
 * The dApp script and its npm install live outside the repository.
 */
class WalletConnectRelayInteropTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dapp: Process? = null

    @After
    fun tearDown() {
        scope.cancel()
        dapp?.destroy()
    }

    private val events = CopyOnWriteArrayList<WalletConnectEvent>()

    @Test
    fun `the official sign-client pairs, gets personal_sign answered, sees a rejection and the disconnect`() {
        val (steps, client) = start(mode = "connect")
        val uri = steps.next("uri").text("uri")
        runBlocking { client.pair(uri) }
        val proposal = awaitEvent<WalletConnectEvent.SessionProposal>(events)
        assertTrue("personal_sign" in proposal.proposal.optionalNamespaces.getValue("eip155").methods)
        // A dApp running in Node has no browser origin to attest.
        assertEquals(WalletConnectValidation.UNKNOWN, proposal.verifyContext.validation)

        val approved = approve(client, proposal)
        val session = steps.next("session")
        assertEquals(approved.topic, session.text("topic"))
        assertEquals(ACCOUNT, session.text("account"))
        assertEquals("Fearless wallet interop", session.text("peer"))
        eventually { client.session(approved.topic)?.acknowledged == true }
        steps.next("ping")

        val request = awaitEvent<WalletConnectEvent.SessionRequest>(events).request
        assertEquals("personal_sign", request.request.method)
        val messageHex = WcJsonRpcCodec.json.parseToJsonElement(request.request.params).jsonArray[0].jsonPrimitive.content
        val signature = Eip191.personalSign(String(messageHex.fromHex(), Charsets.UTF_8), PRIVATE_KEY.fromHex())
        runBlocking { client.respond(approved.topic, request.request.id, JsonPrimitive(signature)) }
        val signed = steps.next("signature")
        assertTrue("the dApp recovered ${signed.text("recovered")}", signed.getValue("ok").jsonPrimitive.boolean)

        eventually { events.filterIsInstance<WalletConnectEvent.SessionRequest>().size == 2 }
        val second = events.filterIsInstance<WalletConnectEvent.SessionRequest>().last().request
        runBlocking { client.respondError(approved.topic, second.request.id, USER_REJECTED, "User rejected request") }
        val rejection = steps.next("rejection")
        assertTrue(rejection.getValue("ok").jsonPrimitive.boolean)
        assertEquals(USER_REJECTED, rejection.getValue("code").jsonPrimitive.long)

        runBlocking { client.disconnect(approved.topic) }
        assertTrue(steps.next("deleted").getValue("ok").jsonPrimitive.boolean)
        assertDappExited()
    }

    @Test
    fun `one-click sign-in is declined and the dApp's fallback proposal connects`() {
        val (steps, client) = start(mode = "authenticate")
        runBlocking { client.pair(steps.next("uri").text("uri")) }
        val proposal = awaitEvent<WalletConnectEvent.SessionProposal>(events)

        val approved = approve(client, proposal)
        val session = steps.next("session")
        assertEquals(approved.topic, session.text("topic"))
        assertEquals(ACCOUNT, session.text("account"))
        assertFalse(session.getValue("viaAuthentication").jsonPrimitive.boolean)

        runBlocking { client.disconnect(approved.topic) }
        assertTrue(steps.next("deleted").getValue("ok").jsonPrimitive.boolean)
        assertDappExited()
    }

    private fun start(mode: String): Pair<Steps, WalletConnectClient> {
        val script = System.getenv("WC_INTEROP_DAPP_SCRIPT")
        val modules = System.getenv("WC_INTEROP_NODE_MODULES")
        val projectId = System.getenv("WC_INTEROP_PROJECT_ID")
        assumeTrue("WC_INTEROP_* not set", script != null && modules != null && projectId != null)
        assumeTrue("the relay is not reachable", reachable("relay.walletconnect.org"))

        val steps = startDapp(requireNotNull(script), requireNotNull(modules), requireNotNull(projectId), mode)
        val client = WalletConnectClient(
            storage = WcMemoryStorage(),
            transport = OkHttpRelayTransport(OkHttpClient()),
            verifyKeySource = OkHttpVerifyKeySource(OkHttpClient()),
            config = WalletConnectClientConfig(
                projectId = projectId,
                packageName = null,
                metadata = WalletConnectMetadata("Fearless wallet interop", "JVM test", "https://fearlesswallet.io", emptyList()),
                supportedMethods = setOf("personal_sign"),
                userAgent = "wc-2/fearless-kotlin-interop/jvm"
            ),
            clock = WcClock.System,
            scope = scope
        )
        scope.launch { client.events.collect { events += it } }
        client.start()
        return steps to client
    }

    private fun approve(client: WalletConnectClient, proposal: WalletConnectEvent.SessionProposal) = runBlocking {
        client.approveSession(
            proposal.proposal.id,
            mapOf("eip155" to WalletConnectSessionNamespace(listOf("eip155:1"), listOf(ACCOUNT), listOf("personal_sign"), listOf("accountsChanged")))
        )
    }

    private fun assertDappExited() {
        assertTrue(requireNotNull(dapp).waitFor(30, TimeUnit.SECONDS))
        assertEquals(0, requireNotNull(dapp).exitValue())
    }

    private fun startDapp(script: String, modules: String, projectId: String, mode: String): Steps {
        val process = ProcessBuilder("node", script, modules)
            .directory(File(script).parentFile)
            .redirectErrorStream(true)
            .apply {
                environment()["WC_PROJECT_ID"] = projectId
                environment()["WC_INTEROP_MODE"] = mode
            }
            .start()
        dapp = process
        val queue = LinkedBlockingQueue<JsonObject>()
        thread(isDaemon = true) {
            process.inputStream.bufferedReader().forEachLine { line ->
                if (line.startsWith(PREFIX)) {
                    queue.put(requireNotNull(WcJsonRpcCodec.parseObject(line.removePrefix(PREFIX))))
                }
            }
        }
        return Steps(queue)
    }

    private class Steps(private val queue: LinkedBlockingQueue<JsonObject>) {
        fun next(step: String): JsonObject {
            val next = requireNotNull(queue.poll(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "the dApp did not reach $step" }
            check(next.text("step") == step) { "the dApp reported ${next.text("step")}: ${next["reason"]}" }
            return next
        }
    }

    private inline fun <reified T : WalletConnectEvent> awaitEvent(events: List<WalletConnectEvent>): T {
        val deadline = System.currentTimeMillis() + STEP_TIMEOUT_SECONDS * 1000
        while (events.none { it is T } && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return events.filterIsInstance<T>().firstOrNull() ?: error("no ${T::class.simpleName} arrived")
    }

    private fun reachable(host: String): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, 443), 5_000) }
    }.isSuccess

    private companion object {
        const val PREFIX = "WC_INTEROP "
        const val STEP_TIMEOUT_SECONDS = 90L
        const val USER_REJECTED = 4001L

        // Synthetic key from the web3.js documentation; never a wallet's key.
        const val PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        val ACCOUNT = "eip155:1:" + Credentials.create(PRIVATE_KEY).address

        fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content
    }
}
