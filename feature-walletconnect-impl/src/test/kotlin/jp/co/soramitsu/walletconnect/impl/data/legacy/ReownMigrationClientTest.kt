package jp.co.soramitsu.walletconnect.impl.data.legacy

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import jp.co.soramitsu.walletconnect.impl.data.legacy.LegacyFixtures.section
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClient
import jp.co.soramitsu.walletconnect.impl.protocol.WalletConnectClientConfig
import jp.co.soramitsu.walletconnect.impl.protocol.WcClientIdentity
import jp.co.soramitsu.walletconnect.impl.protocol.WcJsonRpc
import jp.co.soramitsu.walletconnect.impl.protocol.WcMethods
import jp.co.soramitsu.walletconnect.impl.protocol.WcMemoryStorage
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestAttestor
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestClock
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestDapp
import jp.co.soramitsu.walletconnect.impl.protocol.WcTestRelay
import jp.co.soramitsu.walletconnect.impl.protocol.eventually
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The upgrade end to end, in process: what the Reown SDK stored (real SQLCipher fixtures) is imported,
 * the in-repo client starts on it, and the dApp on the other end of each migrated session keeps talking
 * to the wallet over the same topic and key, as if nothing had changed.
 */
class ReownMigrationClientTest {
    private val reown = section("reown")
    private val now = reown["now"].asLong
    private val sessions = reown.getAsJsonArray("sessions").map { it.asJsonObject }.associateBy { it["name"].asString }
    private val relay = WcTestRelay()
    private val clock = WcTestClock(millis = now * 1000)
    private val storage = WcMemoryStorage()
    private val scopes = mutableListOf<CoroutineScope>()
    private val events = CopyOnWriteArrayList<WalletConnectEvent>()

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `a migrated session reconnects and a new dApp request is answered over it`() {
        val client = migratedClient()
        val topic = topic("alpha")
        val dapp = WcTestDapp(relay, clock)

        eventually { topic in relay.subscriptions }
        val requestId = 1_794_000_000_999_001L
        dapp.send(topic, symKey("alpha"), personalSign(requestId))
        eventually { events.any { event -> event is WalletConnectEvent.SessionRequest && event.request.request.id == requestId } }
        runBlocking { client.respond(topic, requestId, JsonPrimitive("0xsignature")) }

        val answer = dapp.all(topic, symKey("alpha")).last() as WcJsonRpc.Result
        assertEquals(requestId, answer.id)
        assertEquals(JsonPrimitive("0xsignature"), answer.result)
    }

    @Test
    fun `every session the user saw is listed again, with its dApp`() {
        val client = migratedClient()

        eventually { client.sessions.value.size == 2 }
        val listed = client.sessions.value.associateBy { it.topic }
        assertEquals(setOf("alpha", "beta").map { sessions.getValue(it)["topic"].asString }.toSet(), listed.keys)
        assertEquals("Alpha Swap", listed.getValue(sessions.getValue("alpha")["topic"].asString).metaData!!.name)
        assertTrue(dappTopics().all { it in relay.subscriptions })
    }

    @Test
    fun `the request the SDK had not answered yet can still be answered`() {
        val client = migratedClient()
        val topic = topic("alpha")
        val dapp = WcTestDapp(relay, clock)

        eventually { client.pendingRequests(topic).isNotEmpty() }
        assertEquals(listOf(1794000000123456L), client.pendingRequests(topic).map { it.request.id })
        runBlocking { client.respond(topic, 1794000000123456L, JsonPrimitive("0xlate")) }

        val answer = dapp.all(topic, symKey("alpha")).last() as WcJsonRpc.Result
        assertEquals(1794000000123456L, answer.id)
    }

    @Test
    fun `the client signs in to the relay with the identity the SDK used`() {
        migratedClient()

        eventually { relay.urls.isNotEmpty() }
        val jwt = URI(relay.urls.first()).query.split("&").first { it.startsWith("auth=") }.removePrefix("auth=")
        val claims = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt.split(".")[1]))).jsonObject
        val expected = WcClientIdentity(reown["identitySeed"].asString.hexToBytes()).clientId
        assertEquals(expected, claims["iss"]!!.jsonPrimitive.content)
    }

    private fun dappTopics(): List<String> = listOf("alpha", "beta").map(::topic)

    private fun topic(name: String): String = sessions.getValue(name)["topic"].asString

    private fun symKey(name: String): ByteArray = sessions.getValue(name)["sym"].asString.hexToBytes()

    private fun personalSign(id: Long) = WcJsonRpc.Request(
        id,
        WcMethods.SESSION_REQUEST,
        buildJsonObject {
            put(
                "request",
                buildJsonObject {
                    put("method", "personal_sign")
                    put("params", WcTestDapp.paramsOf("""["0x68656c6c6f","0x0000000000000000000000000000000000000001"]"""))
                }
            )
            put("chainId", "eip155:1")
        }
    )

    private fun migratedClient(): WalletConnectClient {
        val imported = runBlocking {
            ReownSessionImporter(ReownFixtureSource(), MemoryImportLedger(), nowSeconds = { now }).importInto(storage)
        }
        check(imported is ReownImportResult.Imported) { "import failed: $imported" }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return WalletConnectClient(
            storage = storage,
            transport = relay,
            verifyKeySource = { WcTestAttestor().publicKey },
            config = WalletConnectClientConfig(
                projectId = "0123456789abcdef0123456789abcdef",
                packageName = "jp.co.soramitsu.fearless",
                metadata = WalletConnectMetadata("Fearless wallet", "Defi wallet", "https://fearlesswallet.io", emptyList()),
                supportedMethods = setOf("personal_sign", "eth_sendTransaction", "eth_signTypedData_v4"),
                userAgent = "wc-2/test"
            ),
            clock = clock,
            scope = scope,
            relayUrls = listOf("wss://relay.test")
        ).also { client ->
            scope.launch { client.events.collect { events += it } }
            client.start()
        }
    }
}
