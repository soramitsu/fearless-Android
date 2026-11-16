package jp.co.soramitsu.tonconnect.impl.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.coredb.model.ConnectionSource
import jp.co.soramitsu.coredb.model.TonConnectionLocal
import jp.co.soramitsu.fearless_utils.encrypt.ton.TonEd25519
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.Keys
import jp.co.soramitsu.fearless_utils.encrypt.xsalsa20poly1305.SecretBox
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.tonMainnetChainId
import jp.co.soramitsu.tonconnect.api.domain.TonConnectRepository
import jp.co.soramitsu.tonconnect.api.model.AppEntity
import jp.co.soramitsu.tonconnect.api.model.BridgeError
import jp.co.soramitsu.tonconnect.api.model.BridgeMethod
import jp.co.soramitsu.tonconnect.api.model.ConnectRequest
import jp.co.soramitsu.tonconnect.api.model.JsonBuilder
import jp.co.soramitsu.tonconnect.api.model.TonDappConnection
import jp.co.soramitsu.tonconnect.impl.data.TonConnectBridgeEventLedger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.fearless_utils.encrypt.keypair.Keypair as SigningKeypair

/** The QR (bridge) side of TON Connect: connect replies sent to the bridge and requests read from its event stream. */
@RunWith(RobolectricTestRunner::class)
class TonConnectBridgeEventsTest {
    private val seed = ByteArray(32) { it.toByte() }
    private val walletPublicKey = TonEd25519.publicKey(seed)
    private val dappPrivate = Keys.generatePrivateKey()
    private val dappPublic = Keys.generatePublicKey(dappPrivate)
    private val clientId = dappPublic.toHexString(false)
    private val sessionPrivate = Keys.generatePrivateKey()
    private val sessionPublic = Keys.generatePublicKey(sessionPrivate)

    private val wallet = mockk<MetaAccount> {
        every { id } returns 7L
        every { tonPublicKey } returns walletPublicKey
    }
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns wallet
        coEvery { allMetaAccounts() } returns listOf(wallet)
        every { selectedMetaAccountFlow() } returns flowOf(wallet)
    }
    private val chain = mockk<Chain> {
        every { id } returns tonMainnetChainId
        every { name } returns "TON"
        every { tonBridgeUrl } returns BRIDGE
    }
    private val chains = mockk<ChainsRepository> {
        coEvery { getChain(tonMainnetChainId) } returns chain
    }
    private val connection = TonDappConnection(TonConnectionLocal(7, clientId, "Bird Swap", "", "https://dapp.example", ConnectionSource.QR))
    private val repository = mockk<TonConnectRepository>(relaxed = true) {
        every { observeConnections(7, ConnectionSource.QR) } returns flowOf(listOf(connection))
        coEvery { getConnectionKeypair(connection.identity()) } returns Keypair(sessionPublic, sessionPrivate)
    }
    private val storage = MemoryStorage()
    private val streamRequests = CopyOnWriteArrayList<Request>()
    private val streams = ConcurrentLinkedQueue<String>()
    private val sseClient = OkHttpClient.Builder().addInterceptor { call ->
        streamRequests += call.request()
        Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(streams.poll().orEmpty().toResponseBody("text/event-stream".toMediaType()))
            .build()
    }.build()
    private val posted = CopyOnWriteArrayList<Request>()
    private val postedBodies = CopyOnWriteArrayList<String>()
    private val bridgeClient = OkHttpClient.Builder().addInterceptor { call ->
        posted += call.request()
        val buffer = Buffer()
        call.request().body!!.writeTo(buffer)
        postedBodies += buffer.readUtf8()
        Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("ok".toResponseBody()).build()
    }.build()

    private val interactor = TonConnectInteractorImpl(
        chains, accounts, mockk(), mockk(), repository, mockk(), mockk(), sseClient, bridgeClient,
        TonConnectBridgeEventLedger(storage)
    )

    @Test
    fun eachRequestIsDeliveredOnceAndAReconnectResumesAfterTheLastEvent(): Unit = runBlocking {
        streams += listOf(
            event(1, request(5)),
            "id: 2\nevent: heartbeat\ndata: {}\n\n",
            event(3, request(6), from = "f".repeat(64)),
            "id: 4\nevent: message\ndata: {\"from\":\"$clientId\",\"message\":\"bm90LWVuY3J5cHRlZA==\"}\n\n",
            event(5, request(5)),
            event(6, request(6))
        ).joinToString("")
        streams += event(7, request(7))

        val events = withTimeout(20_000) { interactor.eventsFlow().take(3).toList() }

        assertEquals(listOf(1L, 6L, 7L), events.map { it.eventId })
        assertEquals(listOf(5L, 6L, 7L), events.map { it.requestId })
        assertTrue(events.all { it.method == BridgeMethod.SEND_TRANSACTION && it.connection == connection })
        assertEquals(listOf("payload-5"), events.first().message.params)
        val first = streamRequests.first()
        assertEquals("$BRIDGE/events?client_id=${sessionPublic.toHexString(false)}", first.url.toString())
        assertEquals("text/event-stream", first.header("Accept"))
        assertNull(first.header("Last-Event-ID"))
        assertEquals("6", streamRequests[1].header("Last-Event-ID"))
        assertTrue(storage.value!!.contains(clientId))
    }

    @Test
    fun connectionsWithoutSessionKeysOpenNoStream(): Unit = runBlocking {
        coEvery { repository.getConnectionKeypair(connection.identity()) } returns null

        val events = withTimeout(10_000) { interactor.eventsFlow().toList() }

        assertTrue(events.isEmpty())
        assertTrue(streamRequests.isEmpty())
    }

    @Test
    fun aNetworkWithoutABridgeCannotStream(): Unit = runBlocking {
        every { chain.tonBridgeUrl } returns null

        val failure = runCatching { withTimeout(10_000) { interactor.eventsFlow().toList() } }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("Chain TON doesn't support Ton Connect", failure!!.message)
    }

    @Test
    fun qrConnectReplyIsEncryptedForTheDappAndStoredForTheBoundWallet(): Unit = runBlocking {
        val saved = slot<TonConnectionLocal>()
        val keys = slot<SigningKeypair>()
        coEvery { repository.saveConnection(capture(saved), capture(keys)) } returns Unit
        val reply = JsonBuilder.connectEventSuccess(walletPublicKey, null, null)

        val result = interactor.respondDappConnectRequest(clientId, connectRequest(), reply, APP)

        assertNotNull(result)
        assertEquals(ConnectionSource.QR, saved.captured.source)
        assertEquals(clientId, saved.captured.clientId)
        assertEquals(7L, saved.captured.metaId)
        assertEquals(APP.url, saved.captured.url)
        val request = posted.single()
        assertEquals(clientId, request.url.queryParameter("to"))
        assertEquals(keys.captured.publicKey.toHexString(false), request.url.queryParameter("client_id"))
        val delivered = decrypt(postedBodies.single(), keys.captured.publicKey)
        assertEquals("connect", delivered.getString("event"))
        assertEquals(reply.getLong("id"), delivered.getLong("id"))
    }

    @Test
    fun rejectedQrConnectionIsReportedToTheDappButNotStored(): Unit = runBlocking {
        val reply = JsonBuilder.connectEventError(BridgeError.USER_DECLINED_TRANSACTION)

        val result = interactor.respondDappConnectRequest(clientId, connectRequest(), reply, APP)

        assertNull(result)
        coVerify(exactly = 0) { repository.saveConnection(any(), any()) }
        assertEquals(1, posted.size)
        val sessionKey = posted.single().url.queryParameter("client_id")!!.hexBytes()
        val delivered = decrypt(postedBodies.single(), sessionKey)
        assertEquals("connect_error", delivered.getString("event"))
        assertEquals(BridgeError.USER_DECLINED_TRANSACTION.code, delivered.getJSONObject("payload").getInt("code"))
    }

    private fun event(id: Long, message: JSONObject, from: String = clientId): String {
        val plaintext = message.toString().toByteArray()
        val box = SecretBox(sessionPublic, dappPrivate)
        val nonce = box.nonce(plaintext)
        val encrypted = Base64.getEncoder().encodeToString(nonce + box.seal(nonce, plaintext))
        val data = JSONObject().put("from", from).put("message", encrypted)
        return "id: $id\nevent: message\ndata: $data\n\n"
    }

    private fun request(id: Long) =
        JSONObject().put("method", "sendTransaction").put("id", id.toString()).put("params", JSONArray().put("payload-$id"))

    private fun decrypt(body: String, sessionKey: ByteArray): JSONObject {
        val bytes = Base64.getDecoder().decode(body)
        val plaintext = SecretBox(sessionKey, dappPrivate).open(bytes.copyOfRange(0, 24), bytes.copyOfRange(24, bytes.size))
        return JSONObject(plaintext.toString(Charsets.UTF_8))
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun connectRequest() = ConnectRequest("https://dapp.example/manifest.json", listOf(ConnectRequest.Item.TonAddress))

    private class MemoryStorage : TonConnectBridgeEventLedger.Storage {
        @Volatile var value: String? = null

        override fun read(): String? = value

        override fun write(value: String): Boolean {
            this.value = value
            return true
        }
    }

    private companion object {
        const val BRIDGE = "https://bridge.fixture.invalid"
        val APP = AppEntity("https://dapp.example", "Bird Swap", "", null, null)
    }
}
