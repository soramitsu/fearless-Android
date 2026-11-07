package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectProposal
import co.jp.soramitsu.walletconnect.model.WalletConnectRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectSession
import co.jp.soramitsu.walletconnect.model.WalletConnectSessionNamespace
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** What the client needs to know about this wallet. */
class WalletConnectClientConfig(
    /** WalletConnect Cloud project id (32 hex characters); the client stays off without a valid one. */
    val projectId: String,
    /** The app id, sent to the relay so a project allowlist can match it. */
    val packageName: String?,
    /** The wallet's own metadata, shown to dApps. */
    val metadata: WalletConnectMetadata,
    /** The JSON-RPC methods the wallet can sign; other session requests are refused before the user sees them. */
    val supportedMethods: Set<String>,
    val userAgent: String
)

/** Called by [WcIncomingHandler] when a dApp ends or confirms what the wallet keeps. */
internal interface WcSessionLifecycle {
    suspend fun deleteSession(topic: String)

    suspend fun deletePairing(topic: String)

    suspend fun onSettled(topic: String, accepted: Boolean)
}

/**
 * The wallet side of WalletConnect v2 (Sign API), written in this repository: pairing, session
 * proposals, settlement, requests and their answers, deletion, ping, update and extend, over the
 * WalletConnect relay. Keys and sessions live only in [WalletConnectStateStorage], encrypted.
 *
 * It signs nothing: every answer to a dApp's request comes from the app through [respond], after
 * the app's own checks.
 */
@Suppress("TooManyFunctions", "LargeClass")
class WalletConnectClient internal constructor(
    storage: WalletConnectStateStorage,
    transport: WcRelayTransport,
    verifyKeySource: WcVerifyKeySource,
    private val config: WalletConnectClientConfig,
    private val clock: WcClock,
    private val scope: CoroutineScope,
    relayUrls: List<String> = listOf(WcRelayConfig.DEFAULT_RELAY_URL, WcRelayConfig.FAILOVER_RELAY_URL)
) : WcSessionLifecycle {
    private val store = WcStateStore(storage, clock)
    private val ids = WcIdGenerator(clock)
    private val relayConfig = WcRelayConfig(config.projectId, config.packageName, config.userAgent, relayUrls)
    private val identityLock = Mutex()
    private val ready = CompletableDeferred<Boolean>()
    private val _events = MutableSharedFlow<WalletConnectEvent>(extraBufferCapacity = EVENT_BUFFER, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val _sessions = MutableStateFlow<List<WalletConnectSession>>(emptyList())
    private val relay: WcRelayClient = WcRelayClient(transport, relayConfig, ::identity, clock, scope, onMessage = { incoming.onMessage(it) })
    private val publisher: WcPublisher = WcPublisher(relay, store, ids)
    private val incoming: WcIncomingHandler = WcIncomingHandler(
        store = store,
        publisher = publisher,
        verifier = WcVerifier(verifyKeySource, clock),
        clock = clock,
        supportedMethods = config.supportedMethods,
        sessions = this,
        emit = ::emit
    )

    @Volatile
    private var started = false

    @Volatile
    private var cachedIdentity: WcClientIdentity? = null

    val events: SharedFlow<WalletConnectEvent> = _events.asSharedFlow()

    /** The open sessions, updated whenever one is approved, settled, changed or deleted. */
    val sessions: StateFlow<List<WalletConnectSession>> = _sessions.asStateFlow()

    val isConfigured: Boolean get() = relayConfig.isValid

    /** Loads the stored state and reconnects the stored sessions. Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val loaded = store.load()
            if (loaded) {
                sweepExpired()
                publishSessions()
                if (isConfigured) {
                    relay.watch(activeTopics())
                    relay.start()
                }
            }
            ready.complete(loaded)
            while (loaded && isActive) {
                delay(SWEEP_INTERVAL_MILLIS)
                runCatchingNonCancellation { sweepExpired() }
            }
        }
        scope.launch {
            relay.connected.collect { emit(WalletConnectEvent.ConnectionChanged(it)) }
        }
    }

    /** The app moved to the foreground or background; the relay connection follows it. */
    fun setForeground(isForeground: Boolean) = relay.setForeground(isForeground)

    /** Pairs with the dApp of a `wc:` URI; its session proposal then arrives as an event. */
    suspend fun pair(uri: String) = withContext(Dispatchers.IO) {
        requireUsable()
        val pairing = WcPairingUri.parse(uri, clock.nowSeconds())
        store.update { state ->
            val existing = state.pairings.firstOrNull { it.topic == pairing.topic }
            check(existing == null || !existing.active) { "This dApp is already connected" }
            check(existing != null || state.pairings.size < WcLimits.MAX_PAIRINGS) { "Too many WalletConnect pairings" }
            val stored = WcStoredPairing(
                topic = pairing.topic,
                symKey = WcEncoding.hex(pairing.symKey),
                relayProtocol = pairing.relayProtocol,
                expiry = pairing.expiryTimestamp ?: (clock.nowSeconds() + WcRpcOptionsTable.PAIRING_INACTIVE_TTL_SECONDS),
                active = false
            )
            state.copy(pairings = state.pairings.filterNot { it.topic == pairing.topic } + stored) to Unit
        }
        relay.subscribe(pairing.topic)
    }

    /**
     * Approves proposal [proposalId] with exactly [namespaces]: each namespace, chain, method and
     * event must have been proposed, and every account must be on an approved chain.
     */
    suspend fun approveSession(proposalId: Long, namespaces: Map<String, WalletConnectSessionNamespace>): WalletConnectSession = withContext(Dispatchers.IO) {
        requireUsable()
        val proposal = pendingProposal(proposalId)
        check(!proposal.verify.isScam) { "The dApp is flagged as a scam" }
        val approved = namespaces.mapValues { WcStoredSessionNamespace.of(it.value) }
        checkApprovable(proposal, approved)
        val pairing = checkNotNull(store.state.pairings.firstOrNull { it.topic == proposal.pairingTopic }) {
            "The connection request has expired"
        }
        check(store.state.sessions.size < WcLimits.MAX_SESSIONS) { "Too many WalletConnect sessions" }

        val selfKeys = WcCrypto.generateKeyPair()
        val symKey = WcCrypto.deriveSymKey(selfKeys.privateKey, WcEncoding.unhex(proposal.proposerPublicKey))
        selfKeys.privateKey.fill(0)
        val sessionTopic = WcCrypto.topicOf(symKey)
        val expiry = clock.nowSeconds() + WcRpcOptionsTable.SESSION_TTL_SECONDS
        val settleId = ids.next()
        val selfPublicKey = WcEncoding.hex(selfKeys.publicKey)
        val session = WcStoredSession(
            topic = sessionTopic,
            symKey = WcEncoding.hex(symKey),
            selfPublicKey = selfPublicKey,
            peerPublicKey = proposal.proposerPublicKey,
            pairingTopic = proposal.pairingTopic,
            relayProtocol = proposal.relayProtocol,
            expiry = expiry,
            acknowledged = false,
            settleRequestId = settleId,
            namespaces = approved,
            requiredNamespaces = proposal.requiredNamespaces,
            optionalNamespaces = proposal.optionalNamespaces,
            peer = proposal.proposer,
            verify = proposal.verify
        )
        store.mutate { it.copy(sessions = it.sessions + session) }
        try {
            relay.subscribe(sessionTopic)
            val relayJson = buildJsonObject { put("protocol", proposal.relayProtocol) }
            val proposalResult = buildJsonObject {
                put("relay", relayJson)
                put("responderPublicKey", selfPublicKey)
            }
            publisher.send(pairing.topic, WcJsonRpc.Result(proposal.id, proposalResult), WcRpcOptionsTable.proposeResponse)
            val settle = buildJsonObject {
                put("relay", relayJson)
                put("namespaces", WcPayloads.sessionNamespacesJson(approved))
                put("controller", WcIncomingHandler.controllerJson(selfPublicKey, WcStoredMetadata.of(config.metadata)))
                put("expiry", expiry)
            }
            publisher.send(sessionTopic, WcJsonRpc.Request(settleId, WcMethods.SESSION_SETTLE, settle), WcRpcOptionsTable.settleRequest, symKey)
        } catch (failure: Exception) {
            store.mutate { state -> state.copy(sessions = state.sessions.filterNot { it.topic == sessionTopic }) }
            runCatchingNonCancellation { relay.unsubscribe(sessionTopic) }
            throw failure
        } finally {
            symKey.fill(0)
        }
        val now = clock.nowSeconds()
        store.mutate { state ->
            state.copy(
                proposals = state.proposals.filterNot { it.id == proposal.id },
                pairings = state.pairings.map {
                    if (it.topic == proposal.pairingTopic) {
                        it.copy(active = true, expiry = now + WcRpcOptionsTable.PAIRING_ACTIVE_TTL_SECONDS, peer = proposal.proposer)
                    } else {
                        it
                    }
                }
            )
        }
        publishSessions()
        WcModels.session(session)
    }

    /** Answers proposal [proposalId] with an error and forgets it. */
    suspend fun rejectSession(proposalId: Long, code: Long = WcErrors.USER_REJECTED, message: String = "User rejected.") = withContext(Dispatchers.IO) {
        requireReady()
        val proposal = store.state.proposals.firstOrNull { it.id == proposalId } ?: return@withContext
        try {
            if (proposal.expiry > clock.nowSeconds()) {
                publisher.send(proposal.pairingTopic, WcJsonRpc.Error(proposal.id, code, message), WcRpcOptionsTable.proposeReject)
            }
        } finally {
            store.mutate { state -> state.copy(proposals = state.proposals.filterNot { it.id == proposalId }) }
        }
    }

    /** Sends [result] as the answer to pending request [requestId] of session [topic]. */
    suspend fun respond(topic: String, requestId: Long, result: JsonElement) =
        answer(topic, requestId, WcJsonRpc.Result(requestId, result))

    suspend fun respondError(topic: String, requestId: Long, code: Long, message: String) =
        answer(topic, requestId, WcJsonRpc.Error(requestId, code, message))

    private suspend fun answer(topic: String, requestId: Long, response: WcJsonRpc) = withContext(Dispatchers.IO) {
        requireReady()
        val request = checkNotNull(store.state.requests.firstOrNull { it.topic == topic && it.id == requestId }) {
            "The request is no longer pending"
        }
        if (request.expiry <= clock.nowSeconds()) {
            forgetRequest(request)
            error("The request has expired")
        }
        publisher.send(topic, response, WcRpcOptionsTable.sessionResponse)
        forgetRequest(request)
    }

    private suspend fun forgetRequest(request: WcStoredRequest) = store.mutate { state ->
        state.copy(
            requests = state.requests.filterNot { it.topic == request.topic && it.id == request.id },
            handled = state.handled + (WcIncomingHandler.handledRequestKey(request.topic, request.id) to
                clock.nowSeconds() + WcIncomingHandler.HANDLED_RETENTION_SECONDS)
        )
    }

    /** Ends session [topic]: tells the dApp when it can, and always forgets the session's keys here. */
    suspend fun disconnect(topic: String) = withContext(Dispatchers.IO) {
        requireReady()
        checkNotNull(store.state.sessions.firstOrNull { it.topic == topic }) { "The session is no longer active" }
        withTimeoutOrNull(DISCONNECT_NOTICE_TIMEOUT_MILLIS) {
            runCatchingNonCancellation {
                publisher.send(topic, WcJsonRpc.Request(ids.next(), WcMethods.SESSION_DELETE, userDisconnected()), WcRpcOptionsTable.deleteRequest)
            }
        }
        deleteSession(topic)
    }

    /** Emits a session event (`chainChanged`, `accountsChanged`) the session approved. */
    suspend fun emitEvent(topic: String, chainId: String, name: String, data: JsonElement) = withContext(Dispatchers.IO) {
        requireReady()
        val session = activeSession(topic)
        val namespace = session.namespaces[chainId.substringBefore(':')]
        require(namespace != null && chainId in WcPayloads.approvedChains(namespace) && name in namespace.events) {
            "The session did not approve this event"
        }
        val params = buildJsonObject {
            put("event", buildJsonObject {
                put("name", name)
                put("data", data)
            })
            put("chainId", chainId)
        }
        publisher.send(topic, WcJsonRpc.Request(ids.next(), WcMethods.SESSION_EVENT, params), WcRpcOptionsTable.eventRequest)
    }

    /** Replaces what session [topic] approves; only what the dApp proposed may be approved. */
    suspend fun updateSession(topic: String, namespaces: Map<String, WalletConnectSessionNamespace>) = withContext(Dispatchers.IO) {
        requireReady()
        val session = activeSession(topic)
        val approved = namespaces.mapValues { WcStoredSessionNamespace.of(it.value) }
        val asProposal = WcStoredProposal(
            id = 0, pairingTopic = session.pairingTopic, proposerPublicKey = session.peerPublicKey, proposer = session.peer,
            requiredNamespaces = session.requiredNamespaces, optionalNamespaces = session.optionalNamespaces,
            relayProtocol = session.relayProtocol, expiry = session.expiry, verify = WcStoredVerify.of(WalletConnectVerifyContext.unknown(null))
        )
        checkApprovable(asProposal, approved)
        val params = buildJsonObject { put("namespaces", WcPayloads.sessionNamespacesJson(approved)) }
        publisher.send(topic, WcJsonRpc.Request(ids.next(), WcMethods.SESSION_UPDATE, params), WcRpcOptionsTable.updateRequest)
        store.mutate { state -> state.copy(sessions = state.sessions.map { if (it.topic == topic) it.copy(namespaces = approved) else it }) }
        publishSessions()
    }

    /** Extends session [topic] by the default session lifetime from now. */
    suspend fun extendSession(topic: String) = withContext(Dispatchers.IO) {
        requireReady()
        activeSession(topic)
        val expiry = clock.nowSeconds() + WcRpcOptionsTable.SESSION_TTL_SECONDS
        val params = buildJsonObject { put("expiry", expiry) }
        publisher.send(topic, WcJsonRpc.Request(ids.next(), WcMethods.SESSION_EXTEND, params), WcRpcOptionsTable.extendRequest)
        store.mutate { state -> state.copy(sessions = state.sessions.map { if (it.topic == topic) it.copy(expiry = expiry) else it }) }
        publishSessions()
    }

    /** Pings the dApp of a session or pairing; true when it answered. */
    suspend fun ping(topic: String): Boolean = withContext(Dispatchers.IO) {
        requireReady()
        val isSession = store.state.sessions.any { it.topic == topic }
        val (method, options) = if (isSession) {
            WcMethods.SESSION_PING to WcRpcOptionsTable.sessionPingRequest
        } else {
            WcMethods.PAIRING_PING to WcRpcOptionsTable.pairingPingRequest
        }
        val answer = withTimeoutOrNull(PING_TIMEOUT_MILLIS) {
            publisher.request(topic, WcJsonRpc.Request(ids.next(), method, JsonObject(emptyMap())), options, PING_TIMEOUT_MILLIS)
        }
        answer is WcJsonRpc.Result
    }

    fun session(topic: String): WalletConnectSession? =
        store.state.sessions.firstOrNull { it.topic == topic && it.expiry > clock.nowSeconds() }?.let(WcModels::session)

    fun proposal(id: Long): WalletConnectProposal? =
        store.state.proposals.firstOrNull { it.id == id && it.expiry > clock.nowSeconds() }?.let(WcModels::proposal)

    fun proposalVerifyContext(id: Long): WalletConnectVerifyContext? =
        store.state.proposals.firstOrNull { it.id == id }?.verify?.toModel()

    /** The requests of session [topic] that still wait for an answer, oldest first. */
    fun pendingRequests(topic: String): List<WalletConnectRequest> {
        val state = store.state
        val session = state.sessions.firstOrNull { it.topic == topic } ?: return emptyList()
        val now = clock.nowSeconds()
        return state.requests.filter { it.topic == topic && it.expiry > now }.map { WcModels.request(it, session) }
    }

    fun verifyContext(topic: String, requestId: Long): WalletConnectVerifyContext? =
        store.state.requests.firstOrNull { it.topic == topic && it.id == requestId }?.verify?.toModel()

    override suspend fun deleteSession(topic: String) {
        val removed = store.update { state ->
            val exists = state.sessions.any { it.topic == topic }
            state.copy(
                sessions = state.sessions.filterNot { it.topic == topic },
                requests = state.requests.filterNot { it.topic == topic }
            ) to exists
        }
        runCatchingNonCancellation { relay.unsubscribe(topic) }
        if (removed) {
            publishSessions()
            emit(WalletConnectEvent.SessionDeleted(topic))
        }
    }

    override suspend fun deletePairing(topic: String) {
        store.mutate { state ->
            state.copy(
                pairings = state.pairings.filterNot { it.topic == topic },
                proposals = state.proposals.filterNot { it.pairingTopic == topic }
            )
        }
        runCatchingNonCancellation { relay.unsubscribe(topic) }
    }

    override suspend fun onSettled(topic: String, accepted: Boolean) {
        if (!accepted) {
            deleteSession(topic)
            return
        }
        store.mutate { state ->
            state.copy(sessions = state.sessions.map { if (it.topic == topic) it.copy(acknowledged = true, settleRequestId = null) else it })
        }
        publishSessions()
        emit(WalletConnectEvent.SessionSettled(topic))
    }

    private suspend fun sweepExpired() {
        val now = clock.nowSeconds()
        val state = store.state
        val expiredSessions = state.sessions.filter { it.expiry <= now }.map { it.topic }
        val expiredPairings = state.pairings.filter { it.expiry <= now }.map { it.topic }
        val stale = expiredSessions.isNotEmpty() || expiredPairings.isNotEmpty() ||
            state.proposals.any { it.expiry <= now } || state.requests.any { it.expiry <= now }
        if (!stale) return
        store.mutate { current ->
            current.copy(
                sessions = current.sessions.filterNot { it.topic in expiredSessions },
                pairings = current.pairings.filterNot { it.topic in expiredPairings },
                proposals = current.proposals.filter { it.expiry > now && it.pairingTopic !in expiredPairings },
                requests = current.requests.filter { it.expiry > now && it.topic !in expiredSessions }
            )
        }
        (expiredSessions + expiredPairings).forEach { topic -> runCatchingNonCancellation { relay.unsubscribe(topic) } }
        if (expiredSessions.isNotEmpty()) {
            publishSessions()
            expiredSessions.forEach { emit(WalletConnectEvent.SessionDeleted(it)) }
        }
    }

    private suspend fun identity(): WcClientIdentity {
        cachedIdentity?.let { return it }
        return identityLock.withLock {
            cachedIdentity ?: run {
                val seed = store.update { state ->
                    val existing = state.clientSeed
                    if (existing != null && WcEncoding.isHex(existing, WcClientIdentity.SEED_LENGTH)) {
                        state to existing
                    } else {
                        val created = WcEncoding.hex(WcCrypto.randomBytes(WcClientIdentity.SEED_LENGTH))
                        state.copy(clientSeed = created) to created
                    }
                }
                WcClientIdentity(WcEncoding.unhex(seed)).also { cachedIdentity = it }
            }
        }
    }

    // What the wallet approves must have been proposed: refused as an argument error before anything is sent.
    private fun checkApprovable(proposal: WcStoredProposal, namespaces: Map<String, WcStoredSessionNamespace>) {
        val refusal = runCatching { WcPayloads.checkApproval(proposal, namespaces) }.exceptionOrNull() ?: return
        throw IllegalArgumentException(refusal.message, refusal)
    }

    private fun activeSession(topic: String): WcStoredSession =
        checkNotNull(store.state.sessions.firstOrNull { it.topic == topic && it.expiry > clock.nowSeconds() }) {
            "The session is no longer active"
        }

    private fun pendingProposal(id: Long): WcStoredProposal =
        checkNotNull(store.state.proposals.firstOrNull { it.id == id && it.expiry > clock.nowSeconds() }) {
            "The connection request has expired"
        }

    private fun activeTopics(): List<String> = store.state.let { state -> state.pairings.map { it.topic } + state.sessions.map { it.topic } }

    private fun publishSessions() {
        val now = clock.nowSeconds()
        _sessions.value = store.state.sessions.filter { it.expiry > now }.map(WcModels::session)
    }

    private fun emit(event: WalletConnectEvent) {
        _events.tryEmit(event)
    }

    private suspend fun requireReady() {
        if (!ready.await()) throw WcStorageUnavailableException(null)
    }

    private suspend fun requireUsable() {
        check(isConfigured) { "WalletConnect is not configured in this build" }
        requireReady()
    }

    private fun userDisconnected() = buildJsonObject {
        put("code", WcErrors.USER_DISCONNECTED)
        put("message", "User disconnected.")
    }

    private companion object {
        const val EVENT_BUFFER = 64
        const val SWEEP_INTERVAL_MILLIS = 60_000L
        const val DISCONNECT_NOTICE_TIMEOUT_MILLIS = 10_000L
        const val PING_TIMEOUT_MILLIS = 30_000L
    }
}
