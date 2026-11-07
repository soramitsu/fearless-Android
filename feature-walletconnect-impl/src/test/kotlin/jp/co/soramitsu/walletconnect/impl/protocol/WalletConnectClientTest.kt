package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectPairingExpiredException
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectUriException
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The wallet side of the protocol, end to end against an in-memory relay and a scripted dApp. */
class WalletConnectClientTest {
    private val relay = WcTestRelay()
    private val clock = WcTestClock()
    private val storage = WcMemoryStorage()
    private val scopes = mutableListOf<CoroutineScope>()
    private val events = CopyOnWriteArrayList<WalletConnectEvent>()
    private val dapp = WcTestDapp(relay, clock)
    private val attestor = WcTestAttestor()
    private val account = "eip155:1:0x8ba1f109551bd432803012645ac136ddd64dba72"

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
    }

    @Test
    fun `pairing subscribes the pairing topic and receives the proposal kept by the relay`() {
        val client = client()
        dapp.propose()

        runBlocking { client.pair(dapp.uri()) }

        assertTrue(dapp.pairingTopic in relay.subscriptions)
        val proposal = awaitEvent<WalletConnectEvent.SessionProposal>()
        assertEquals("Test dApp", proposal.proposal.name)
        assertEquals(listOf("eip155:1"), proposal.proposal.optionalNamespaces["eip155"]?.chains)
        assertEquals(WalletConnectValidation.UNKNOWN, proposal.verifyContext.validation)
        assertEquals("https://app.example.org", proposal.verifyContext.claimedUrl)
        assertTrue(relay.urls.single().startsWith("wss://relay.test?auth="))
    }

    @Test
    fun `approval answers the proposal and settles a session the dApp derives the same topic for`() {
        val client = client()
        val proposalId = pairAndPropose(client)

        val session = runBlocking { client.approveSession(proposalId, approvedNamespaces()) }

        val approval = dapp.readApproval()
        assertEquals(proposalId, approval.id)
        assertEquals(session.topic, dapp.sessionTopic)
        assertEquals(1101, relay.publishedOn(dapp.pairingTopic).last().tag)
        val settle = dapp.settleRequest()
        val params = settle.params.jsonObject
        assertEquals(account, params["namespaces"]!!.jsonObject["eip155"]!!.jsonObject["accounts"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("Fearless wallet", params["controller"]!!.jsonObject["metadata"]!!.jsonObject.str("name"))
        assertEquals(clock.nowSeconds() + WcRpcOptionsTable.SESSION_TTL_SECONDS, params["expiry"]!!.jsonPrimitive.long)
        assertEquals(1102, relay.publishedOn(session.topic).first().tag)
        assertFalse(client.session(session.topic)!!.acknowledged)

        dapp.acknowledgeSettle()

        eventually { client.session(session.topic)?.acknowledged == true }
        assertNull(client.proposal(proposalId))
        assertTrue(awaitEvent<WalletConnectEvent.SessionSettled>().topic == session.topic)
    }

    @Test
    fun `a settlement the dApp refuses removes the session`() {
        val client = client()
        val session = runBlocking { client.approveSession(pairAndPropose(client), approvedNamespaces()) }
        dapp.readApproval()

        dapp.acknowledgeSettle(accepted = false)

        eventually { client.session(session.topic) == null }
    }

    @Test
    fun `approval refuses anything the dApp did not propose`() {
        val client = client()
        val proposalId = pairAndPropose(client)
        val refused = listOf(
            mapOf("eip155" to namespace(chains = listOf("eip155:56"), accounts = listOf("eip155:56:0x8ba1f109551bd432803012645ac136ddd64dba72"))),
            mapOf("eip155" to namespace(methods = listOf("eth_sign"))),
            mapOf("eip155" to namespace(accounts = listOf("eip155:56:0x8ba1f109551bd432803012645ac136ddd64dba72"))),
            mapOf("polkadot" to namespace()),
            emptyMap()
        )
        refused.forEach { namespaces ->
            assertThrows(namespaces.toString(), IllegalArgumentException::class.java) {
                runBlocking { client.approveSession(proposalId, namespaces) }
            }
        }
        assertTrue(relay.publishedOn(dapp.pairingTopic).isEmpty())
    }

    @Test
    fun `rejection answers with the user rejected code and the reject tag`() {
        val client = client()
        val proposalId = pairAndPropose(client)

        runBlocking { client.rejectSession(proposalId) }

        val error = dapp.latest(dapp.pairingTopic, dapp.pairingSymKey) as WcJsonRpc.Error
        assertEquals(proposalId, error.id)
        assertEquals(WcErrors.USER_REJECTED, error.code)
        assertEquals(1120, relay.publishedOn(dapp.pairingTopic).last().tag)
        assertNull(client.proposal(proposalId))
    }

    @Test
    fun `a session request reaches the app and its answer reaches the dApp`() {
        val client = client()
        val topic = settledSession(client)

        val requestId = dapp.request("personal_sign", WcTestDapp.paramsOf("""["0x68656c6c6f","0x8ba1f109551bd432803012645ac136ddd64dba72"]"""))

        val event = awaitEvent<WalletConnectEvent.SessionRequest>()
        assertEquals(requestId, event.request.request.id)
        assertEquals("""["0x68656c6c6f","0x8ba1f109551bd432803012645ac136ddd64dba72"]""", event.request.request.params)
        assertEquals("eip155:1", event.request.chainId)
        assertEquals(listOf(requestId), client.pendingRequests(topic).map { it.request.id })

        runBlocking { client.respond(topic, requestId, JsonPrimitive("0xsignature")) }

        val answer = dapp.sessionMessages().last() as WcJsonRpc.Result
        assertEquals(requestId, answer.id)
        assertEquals(JsonPrimitive("0xsignature"), answer.result)
        assertEquals(1109, relay.publishedOn(topic).last().tag)
        assertTrue(client.pendingRequests(topic).isEmpty())
        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.respond(topic, requestId, JsonPrimitive("again")) }
        }
    }

    @Test
    fun `requests outside the session are refused before the user sees them`() {
        val client = client(supported = setOf("personal_sign", "eth_sendTransaction"))
        settledSession(client, methods = listOf("personal_sign", "eth_sendTransaction", "wallet_switchEthereumChain"))
        val cases = mapOf(
            dapp.request("eth_signTypedData_v4", WcTestDapp.paramsOf("[]")) to WcErrors.UNAUTHORIZED_METHOD,
            dapp.request("wallet_switchEthereumChain", WcTestDapp.paramsOf("[]")) to WcErrors.UNSUPPORTED_METHODS,
            dapp.request("personal_sign", WcTestDapp.paramsOf("[]"), chainId = "eip155:56") to WcErrors.UNSUPPORTED_CHAINS,
            dapp.request("personal_sign", WcTestDapp.paramsOf("[]"), chainId = "not a chain") to WcErrors.UNSUPPORTED_CHAINS,
            dapp.request("personal_sign", JsonPrimitive("x".repeat(WcLimits.MAX_REQUEST_PARAMS_LENGTH))) to WcErrors.INVALID_REQUEST,
            dapp.request("personal_sign", WcTestDapp.paramsOf("[]"), expiryTimestamp = clock.nowSeconds()) to WcErrors.SESSION_REQUEST_EXPIRED
        )

        eventually { dapp.sessionMessages().count { it is WcJsonRpc.Error } == cases.size }
        val errors = dapp.sessionMessages().filterIsInstance<WcJsonRpc.Error>().associate { it.id to it.code }
        assertEquals(cases, errors)
        assertTrue(events.none { it is WalletConnectEvent.SessionRequest })
    }

    @Test
    fun `a message delivered twice is handled once`() {
        val client = client()
        val topic = settledSession(client)
        val request = WcJsonRpc.Request(
            dapp.nextId(), WcMethods.SESSION_REQUEST,
            buildJsonObject {
                put(
                    "request",
                    buildJsonObject {
                        put("method", "personal_sign")
                        put("params", WcTestDapp.paramsOf("[]"))
                    }
                )
                put("chainId", "eip155:1")
            }
        )
        val message = WcCrypto.encodeType0(dapp.sessionSymKey!!, WcJsonRpcCodec.encode(request))

        relay.push(topic, message)
        relay.push(topic, message)
        // The same request re-encrypted (a new nonce) is the same request.
        dapp.onSession(request)

        eventually { events.count { it is WalletConnectEvent.SessionRequest } == 1 }
        Thread.sleep(200)
        assertEquals(1, events.count { it is WalletConnectEvent.SessionRequest })
        assertEquals(1, client.pendingRequests(topic).size)
    }

    @Test
    fun `pings are answered on sessions and pairings`() {
        val client = client()
        val topic = settledSession(client)

        dapp.onSession(WcJsonRpc.Request(dapp.nextId(), WcMethods.SESSION_PING, JsonObject(emptyMap())))
        dapp.send(dapp.pairingTopic, dapp.pairingSymKey, WcJsonRpc.Request(42, WcMethods.PAIRING_PING, JsonObject(emptyMap())))

        eventually { dapp.sessionMessages().last() is WcJsonRpc.Result }
        eventually { relay.publishedOn(dapp.pairingTopic).any { it.tag == 1003 } }
        assertEquals(1115, relay.publishedOn(topic).last().tag)
        assertEquals(JsonPrimitive(true), (dapp.latest(dapp.pairingTopic, dapp.pairingSymKey) as WcJsonRpc.Result).result)
    }

    @Test
    fun `the dApp deleting the session removes it and its pending requests`() {
        val client = client()
        val topic = settledSession(client)
        dapp.request("personal_sign", WcTestDapp.paramsOf("[]"))
        eventually { client.pendingRequests(topic).isNotEmpty() }

        dapp.onSession(
            WcJsonRpc.Request(
                dapp.nextId(), WcMethods.SESSION_DELETE,
                buildJsonObject {
                    put("code", 6000)
                    put("message", "User disconnected.")
                }
            )
        )

        assertEquals(topic, awaitEvent<WalletConnectEvent.SessionDeleted>().topic)
        assertNull(client.session(topic))
        assertTrue(client.pendingRequests(topic).isEmpty())
        assertFalse(topic in relay.subscriptions)
        assertEquals(1113, relay.publishedOn(topic).last().tag)
    }

    @Test
    fun `disconnecting tells the dApp and forgets the session`() {
        val client = client()
        val topic = settledSession(client)

        runBlocking { client.disconnect(topic) }

        val delete = dapp.sessionMessages().last() as WcJsonRpc.Request
        assertEquals(WcMethods.SESSION_DELETE, delete.method)
        assertEquals(WcErrors.USER_DISCONNECTED, delete.params.jsonObject["code"]!!.jsonPrimitive.long)
        assertEquals(1112, relay.publishedOn(topic).last().tag)
        assertNull(client.session(topic))
        assertTrue(client.sessions.value.isEmpty())
    }

    @Test
    fun `only the wallet may update or extend a session`() {
        val client = client()
        settledSession(client)

        val update = dapp.nextId()
        val extend = dapp.nextId()
        val updateParams = buildJsonObject { put("namespaces", JsonObject(emptyMap())) }
        val extendParams = buildJsonObject { put("expiry", clock.nowSeconds() + 999_999) }
        dapp.onSession(WcJsonRpc.Request(update, WcMethods.SESSION_UPDATE, updateParams))
        dapp.onSession(WcJsonRpc.Request(extend, WcMethods.SESSION_EXTEND, extendParams))

        eventually { dapp.sessionMessages().count { it is WcJsonRpc.Error } == 2 }
        val errors = dapp.sessionMessages().filterIsInstance<WcJsonRpc.Error>().associate { it.id to it.code }
        assertEquals(mapOf(update to WcErrors.UNAUTHORIZED_UPDATE_REQUEST, extend to WcErrors.UNAUTHORIZED_EXTEND_REQUEST), errors)
    }

    @Test
    fun `the wallet extends, updates and emits events on its sessions`() {
        val client = client()
        val topic = settledSession(client)
        clock.advanceSeconds(3600)

        runBlocking {
            client.extendSession(topic)
            client.updateSession(topic, mapOf("eip155" to namespace(methods = listOf("personal_sign"))))
            client.emitEvent(topic, "eip155:1", "accountsChanged", WcTestDapp.paramsOf("""["$account"]"""))
        }

        val requests = dapp.sessionMessages().filterIsInstance<WcJsonRpc.Request>().map { it.method }
        assertEquals(listOf(WcMethods.SESSION_SETTLE, WcMethods.SESSION_EXTEND, WcMethods.SESSION_UPDATE, WcMethods.SESSION_EVENT), requests)
        assertEquals(clock.nowSeconds() + WcRpcOptionsTable.SESSION_TTL_SECONDS, client.session(topic)!!.expiry)
        assertEquals(listOf("personal_sign"), client.session(topic)!!.namespaces["eip155"]!!.methods)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.emitEvent(topic, "eip155:56", "accountsChanged", JsonObject(emptyMap())) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { client.updateSession(topic, mapOf("eip155" to namespace(methods = listOf("eth_sign")))) }
        }
    }

    @Test
    fun `one click authentication is declined so the dApp falls back to its proposal`() {
        val client = client()
        runBlocking { client.pair(dapp.uri()) }
        val requester = WcCrypto.generateKeyPair()
        val id = dapp.nextId()

        dapp.send(
            dapp.pairingTopic, dapp.pairingSymKey,
            WcJsonRpc.Request(
                id, WcMethods.SESSION_AUTHENTICATE,
                buildJsonObject {
                    put(
                        "requester",
                        buildJsonObject {
                            put("publicKey", WcEncoding.hex(requester.publicKey))
                            put("metadata", buildJsonObject { put("name", "dApp") })
                        }
                    )
                    put("authPayload", buildJsonObject { put("domain", "app.example.org") })
                }
            )
        )

        val responseTopic = WcCrypto.topicOf(requester.publicKey)
        eventually { relay.publishedOn(responseTopic).isNotEmpty() }
        val published = relay.publishedOn(responseTopic).single()
        assertEquals(1118, published.tag)
        val envelope = WcCrypto.parseEnvelope(published.message)
        assertEquals(WcCrypto.TYPE_1, envelope.type)
        val error = WcJsonRpcCodec.decode(WcCrypto.decodeType1(requester.privateKey, envelope)) as WcJsonRpc.Error
        assertEquals(id, error.id)
        assertEquals(WcErrors.WC_METHOD_UNSUPPORTED, error.code)
    }

    @Test
    fun `the dApp deleting the pairing drops it and its proposals`() {
        val client = client()
        val proposalId = pairAndPropose(client)

        dapp.send(
            dapp.pairingTopic, dapp.pairingSymKey,
            WcJsonRpc.Request(
                dapp.nextId(), WcMethods.PAIRING_DELETE,
                buildJsonObject {
                    put("code", 6000)
                    put("message", "User disconnected.")
                }
            )
        )

        eventually { client.proposal(proposalId) == null }
        eventually { dapp.pairingTopic !in relay.subscriptions }
    }

    @Test
    fun `malformed or expired proposals are refused or ignored`() {
        val client = client()
        runBlocking { client.pair(dapp.uri()) }

        val expired = dapp.propose(expiry = clock.nowSeconds() - 1)
        val badChains = dapp.propose(optionalNamespaces = WcTestDapp.objectOf("""{"eip155":{"chains":["solana:1"],"methods":[],"events":[]}}"""))

        eventually { relay.publishedOn(dapp.pairingTopic).isNotEmpty() }
        val error = dapp.latest(dapp.pairingTopic, dapp.pairingSymKey) as WcJsonRpc.Error
        assertEquals(badChains, error.id)
        assertEquals(WcErrors.UNSUPPORTED_CHAINS, error.code)
        assertEquals(1121, relay.publishedOn(dapp.pairingTopic).last().tag)
        assertNull(client.proposal(expired))
        assertTrue(events.none { it is WalletConnectEvent.SessionProposal })
    }

    @Test
    fun `chain keyed namespaces are read as their namespace`() {
        val client = client()
        runBlocking { client.pair(dapp.uri()) }

        dapp.propose(
            optionalNamespaces = WcTestDapp.objectOf(
                """{"eip155:1":{"methods":["personal_sign"],"events":[]},""" +
                    """"eip155":{"chains":["eip155:56"],"methods":["eth_sign"],"events":["chainChanged"]}}"""
            )
        )

        val proposal = awaitEvent<WalletConnectEvent.SessionProposal>().proposal
        val namespace = proposal.optionalNamespaces.getValue("eip155")
        assertEquals(setOf("eip155:1", "eip155:56"), namespace.chains!!.toSet())
        assertEquals(setOf("personal_sign", "eth_sign"), namespace.methods.toSet())
    }

    @Test
    fun `a verified dApp's origin reaches the app with its proposal and requests`() {
        val client = client()
        dapp.attest = { message -> attestor.attest(message, origin = "https://app.example.org") }
        val topic = settledSession(client)
        val proposal = events.filterIsInstance<WalletConnectEvent.SessionProposal>().single()
        assertEquals(WalletConnectValidation.VALID, proposal.verifyContext.validation)
        assertEquals("https://app.example.org", proposal.verifyContext.verifiedOrigin)

        dapp.attest = { message -> attestor.attest(message, origin = "https://app.examp1e.org") }
        val requestId = dapp.request("personal_sign", WcTestDapp.paramsOf("[]"))

        val request = awaitEvent<WalletConnectEvent.SessionRequest>()
        assertEquals(WalletConnectValidation.INVALID, request.verifyContext.validation)
        assertEquals("https://app.examp1e.org", client.verifyContext(topic, requestId)?.verifiedOrigin)
    }

    @Test
    fun `a dApp Verify lists as a scam cannot be approved`() {
        val client = client()
        dapp.attest = { message -> attestor.attest(message, origin = "https://drainer.example", isScam = true, isVerified = false) }
        val id = pairAndPropose(client)

        assertTrue(awaitEvent<WalletConnectEvent.SessionProposal>().verifyContext.isScam)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { client.approveSession(id, approvedNamespaces()) }
        }
        assertTrue(relay.publishedOn(dapp.pairingTopic).isEmpty())
    }

    @Test
    fun `sessions survive a restart and are resubscribed`() {
        val client = client()
        val topic = settledSession(client)
        scopes.forEach { it.cancel() }
        relay.dropConnection()

        val restarted = client(fresh = false)

        eventually { restarted.session(topic) != null }
        eventually { topic in relay.subscriptions && relay.current != null }
        assertTrue(restarted.session(topic)!!.acknowledged)
        dapp.request("personal_sign", WcTestDapp.paramsOf("[]"))
        eventually { restarted.pendingRequests(topic).isNotEmpty() }
    }

    @Test
    fun `the client reconnects and resubscribes after the connection drops`() {
        val client = client()
        val topic = settledSession(client)
        val connectsBefore = relay.urls.size

        relay.dropConnection()

        eventually { relay.urls.size > connectsBefore && relay.current != null }
        eventually { relay.calls.count { it == WcRelayClient.METHOD_BATCH_SUBSCRIBE } >= 1 }
        dapp.request("personal_sign", WcTestDapp.paramsOf("[]"))
        eventually { client.pendingRequests(topic).isNotEmpty() }
    }

    @Test
    fun `expired sessions and pairings are removed`() {
        val client = client()
        val topic = settledSession(client)

        clock.advanceSeconds(WcRpcOptionsTable.SESSION_TTL_SECONDS + 1)

        assertNull(client.session(topic))
        assertTrue(client.pendingRequests(topic).isEmpty())
    }

    @Test
    fun `malformed, v1 and expired links are refused`() {
        val client = client()

        assertThrows(WalletConnectUriException::class.java) { runBlocking { client.pair("wc:abc@1?bridge=x&key=y") } }
        assertThrows(WalletConnectPairingExpiredException::class.java) {
            runBlocking { client.pair(dapp.uri(expiry = clock.nowSeconds())) }
        }
        assertTrue(relay.urls.isEmpty())
    }

    @Test
    fun `a paired dApp cannot be paired again once its session is approved`() {
        val client = client()
        settledSession(client)

        assertThrows(IllegalStateException::class.java) { runBlocking { client.pair(dapp.uri()) } }
    }

    @Test
    fun `nothing connects without a stored pairing or session`() {
        client()
        Thread.sleep(200)

        assertTrue(relay.urls.isEmpty())
    }

    @Test
    fun `without a project id the client refuses to pair`() {
        val client = client(projectId = "")

        assertThrows(IllegalStateException::class.java) { runBlocking { client.pair(dapp.uri()) } }
    }

    @Test
    fun `unreadable storage keeps the client off and the stored state untouched`() {
        storage.json = "sealed"
        storage.unreadable = true
        val client = client(fresh = false)

        assertThrows(WcStorageUnavailableException::class.java) { runBlocking { client.pair(dapp.uri()) } }
        assertEquals("sealed", storage.json)
    }

    @Test
    fun `stored state holds the session key only encrypted by the storage, never the private key`() {
        val client = client()
        val topic = settledSession(client)

        val stored = storage.json!!
        assertTrue(stored.contains(topic))
        assertFalse(stored.contains(WcEncoding.hex(dapp.keys.privateKey)))
        assertTrue(stored.contains(WcEncoding.hex(dapp.sessionSymKey!!)))
        assertFalse(stored.contains("privateKey"))
    }

    @Test
    fun `answers to the wallet's own pings complete them`() {
        val client = client()
        val topic = settledSession(client)
        relay.onPublish = { published ->
            if (published.topic == topic) {
                val rpc = WcJsonRpcCodec.decode(WcCrypto.decodeType0(dapp.sessionSymKey!!, WcCrypto.parseEnvelope(published.message)))
                if (rpc is WcJsonRpc.Request && rpc.method == WcMethods.SESSION_PING) {
                    Thread { dapp.onSession(WcJsonRpc.Result(rpc.id, JsonPrimitive(true))) }.start()
                }
            }
        }

        assertTrue(runBlocking { client.ping(topic) })
    }

    private fun client(
        fresh: Boolean = true,
        projectId: String = PROJECT_ID,
        supported: Set<String> = setOf("personal_sign", "eth_sendTransaction", "eth_signTypedData_v4")
    ): WalletConnectClient {
        if (fresh) storage.json = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }
        return WalletConnectClient(
            storage = storage,
            transport = relay,
            verifyKeySource = { attestor.publicKey },
            config = WalletConnectClientConfig(
                projectId = projectId,
                packageName = "jp.co.soramitsu.fearless",
                metadata = WalletConnectMetadata("Fearless wallet", "Defi wallet", "https://fearlesswallet.io", emptyList()),
                supportedMethods = supported,
                userAgent = "wc-2/test"
            ),
            clock = clock,
            scope = scope,
            relayUrls = listOf("wss://relay.test", "wss://relay-failover.test")
        ).also { client ->
            scope.launch { client.events.collect { events += it } }
            client.start()
        }
    }

    private fun pairAndPropose(client: WalletConnectClient): Long {
        runBlocking { client.pair(dapp.uri()) }
        val id = dapp.propose()
        eventually { client.proposal(id) != null }
        return id
    }

    private fun settledSession(
        client: WalletConnectClient,
        methods: List<String> = listOf("personal_sign", "eth_sendTransaction")
    ): String {
        runBlocking { client.pair(dapp.uri()) }
        val id = dapp.propose(optionalNamespaces = WcTestDapp.evmNamespaces(methods = methods))
        eventually { client.proposal(id) != null }
        val session = runBlocking { client.approveSession(id, approvedNamespaces(methods)) }
        dapp.readApproval()
        dapp.acknowledgeSettle()
        eventually { client.session(session.topic)?.acknowledged == true }
        return session.topic
    }

    private fun approvedNamespaces(methods: List<String> = listOf("personal_sign", "eth_sendTransaction")) =
        mapOf("eip155" to namespace(methods = methods))

    private fun namespace(
        chains: List<String> = listOf("eip155:1"),
        accounts: List<String> = listOf(account),
        methods: List<String> = listOf("personal_sign", "eth_sendTransaction")
    ) =
        WalletConnectSessionNamespace(chains = chains, accounts = accounts, methods = methods, events = listOf("accountsChanged"))

    private inline fun <reified T : WalletConnectEvent> awaitEvent(): T {
        eventually { events.any { it is T } }
        return events.filterIsInstance<T>().last()
    }

    private companion object {
        const val PROJECT_ID = "0123456789abcdef0123456789abcdef"
    }
}
