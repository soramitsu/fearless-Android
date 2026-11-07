package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectEvent
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Handles what dApps send: decrypts each relay message with its topic's key, drops repeats, checks
 * the call and answers it or hands it to the user. Runs on one coroutine, message by message.
 */
@Suppress("TooManyFunctions")
internal class WcIncomingHandler(
    private val store: WcStateStore,
    private val publisher: WcPublisher,
    private val verifier: WcVerifier,
    private val clock: WcClock,
    private val supportedMethods: Set<String>,
    private val sessions: WcSessionLifecycle,
    private val emit: (WalletConnectEvent) -> Unit
) {

    suspend fun onMessage(message: WcRelayMessage) {
        val messageKey = HANDLED_MESSAGE + WcCrypto.hashMessage(message.message)
        if (store.state.handled.containsKey(messageKey)) return
        val payload = decrypt(message) ?: return
        // Recorded before it is acted on: a message delivered again is never handled twice.
        store.mutate { it.copy(handled = it.handled + (messageKey to clock.nowSeconds() + HANDLED_RETENTION_SECONDS)) }
        val rpc = payload.takeIf { it.length <= WcLimits.MAX_PAYLOAD_LENGTH }?.let(WcJsonRpcCodec::decode) ?: return
        val isSession = store.state.sessions.any { it.topic == message.topic }
        when (rpc) {
            is WcJsonRpc.Request -> if (isSession) onSessionRequestMessage(message, rpc) else onPairingRequest(message, rpc)
            is WcJsonRpc.Result, is WcJsonRpc.Error -> onResponse(message.topic, rpc, isSession)
        }
    }

    private fun decrypt(message: WcRelayMessage): String? {
        val symKey = publisher.symKeyOf(message.topic) ?: return null
        return runCatching {
            val envelope = WcCrypto.parseEnvelope(message.message)
            // A wallet only ever receives type 0: type 1 is how wallets answer one-click authentication.
            if (envelope.type != WcCrypto.TYPE_0) null else WcCrypto.decodeType0(symKey, envelope)
        }.getOrNull()
    }

    private suspend fun onPairingRequest(message: WcRelayMessage, rpc: WcJsonRpc.Request) {
        val topic = message.topic
        when (rpc.method) {
            WcMethods.SESSION_PROPOSE -> onProposal(message, rpc)
            WcMethods.SESSION_AUTHENTICATE -> declineAuthentication(topic, rpc)
            WcMethods.PAIRING_PING -> reply(topic, WcJsonRpc.Result(rpc.id, JsonPrimitive(true)), WcRpcOptionsTable.pairingPingResponse)
            WcMethods.PAIRING_DELETE -> {
                reply(topic, WcJsonRpc.Result(rpc.id, JsonPrimitive(true)), WcRpcOptionsTable.pairingDeleteResponse)
                sessions.deletePairing(topic)
            }
            else -> reply(topic, unsupported(rpc), WcRpcOptionsTable.unregistered)
        }
    }

    private suspend fun onSessionRequestMessage(message: WcRelayMessage, rpc: WcJsonRpc.Request) {
        val topic = message.topic
        when (rpc.method) {
            WcMethods.SESSION_REQUEST -> onSessionRequest(message, rpc)
            WcMethods.SESSION_PING -> reply(topic, WcJsonRpc.Result(rpc.id, JsonPrimitive(true)), WcRpcOptionsTable.sessionPingResponse)
            WcMethods.SESSION_DELETE -> {
                reply(topic, WcJsonRpc.Result(rpc.id, JsonPrimitive(true)), WcRpcOptionsTable.deleteResponse)
                sessions.deleteSession(topic)
            }
            // Only the controller, this wallet, may change or extend a session.
            WcMethods.SESSION_UPDATE -> reply(
                topic,
                WcJsonRpc.Error(rpc.id, WcErrors.UNAUTHORIZED_UPDATE_REQUEST, "Unauthorized update request."),
                WcRpcOptionsTable.updateResponse
            )
            WcMethods.SESSION_EXTEND -> reply(
                topic,
                WcJsonRpc.Error(rpc.id, WcErrors.UNAUTHORIZED_EXTEND_REQUEST, "Unauthorized extend request."),
                WcRpcOptionsTable.extendResponse
            )
            // Events go from the wallet to the dApp; one from the dApp means nothing here.
            WcMethods.SESSION_EVENT, WcMethods.SESSION_SETTLE -> Unit
            else -> reply(topic, unsupported(rpc), WcRpcOptionsTable.unregistered)
        }
    }

    private suspend fun onProposal(message: WcRelayMessage, rpc: WcJsonRpc.Request) {
        val topic = message.topic
        if (store.state.proposals.any { it.id == rpc.id }) return
        val proposal = try {
            readProposal(topic, rpc)
        } catch (refused: WcProtocolException) {
            reply(topic, WcJsonRpc.Error(rpc.id, refused.code, refused.message.orEmpty()), WcRpcOptionsTable.proposeAutoReject)
            return
        } ?: return
        val verify = verifier.resolve(message.attestation, message.message, proposal.proposer.url)
        val stored = proposal.copy(verify = WcStoredVerify.of(verify))
        store.mutate { it.copy(proposals = it.proposals.filterNot { item -> item.id == stored.id } + stored) }
        emit(WalletConnectEvent.SessionProposal(WcModels.proposal(stored), verify))
    }

    /** The proposal, or null when it already expired (the dApp stopped waiting, so nothing is answered). */
    private fun readProposal(topic: String, rpc: WcJsonRpc.Request): WcStoredProposal? {
        val params = rpc.params as? JsonObject ?: refuse(WcErrors.INVALID_REQUEST, "Malformed proposal")
        val now = clock.nowSeconds()
        val expiry = params.long("expiryTimestamp") ?: (now + WcRpcOptionsTable.PROPOSAL_TTL_SECONDS)
        if (expiry <= now) return null
        if (store.state.proposals.size >= WcLimits.MAX_PENDING_PROPOSALS) {
            refuse(WcErrors.LIMIT_EXCEEDED, "Too many pending proposals")
        }
        val proposer = params["proposer"] as? JsonObject ?: refuse(WcErrors.INVALID_REQUEST, "Missing proposer")
        val proposerKey = proposer.string("publicKey")
        if (!WcEncoding.isHex(proposerKey, WcCrypto.KEY_LENGTH)) refuse(WcErrors.INVALID_REQUEST, "Invalid proposer key")
        val relays = params["relays"] as? JsonArray
        val protocols = relays?.mapNotNull { (it as? JsonObject)?.string("protocol") } ?: listOf(WcPairingUri.RELAY_PROTOCOL)
        if (WcPairingUri.RELAY_PROTOCOL !in protocols) refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported relay protocol")
        val required = WcPayloads.proposalNamespaces(params["requiredNamespaces"])
        val optional = WcPayloads.proposalNamespaces(params["optionalNamespaces"])
        if (required.isEmpty() && optional.isEmpty()) refuse(WcErrors.UNSUPPORTED_NAMESPACE_KEY, "No namespaces")
        return WcStoredProposal(
            id = rpc.id,
            pairingTopic = topic,
            proposerPublicKey = requireNotNull(proposerKey).lowercase(),
            proposer = WcPayloads.metadata(proposer["metadata"]),
            requiredNamespaces = required,
            optionalNamespaces = optional,
            relayProtocol = WcPairingUri.RELAY_PROTOCOL,
            expiry = minOf(expiry, now + MAX_PROPOSAL_LIFETIME_SECONDS),
            verify = WcStoredVerify.of(WalletConnectVerifyContext.unknown(null))
        )
    }

    /**
     * One-click authentication (CACAO sign-in) is declined with WC_METHOD_UNSUPPORTED: the dApp then
     * uses the session proposal it sends alongside, and the user approves that as usual.
     */
    private suspend fun declineAuthentication(topic: String, rpc: WcJsonRpc.Request) {
        val requesterKey = ((rpc.params as? JsonObject)?.get("requester") as? JsonObject)?.string("publicKey")
        val error = WcJsonRpc.Error(rpc.id, WcErrors.WC_METHOD_UNSUPPORTED, "Unsupported wc_ method. ${WcMethods.SESSION_AUTHENTICATE}")
        if (!WcEncoding.isHex(requesterKey, WcCrypto.KEY_LENGTH)) {
            reply(topic, error, WcRpcOptionsTable.authenticateReject)
            return
        }
        val receiver = WcEncoding.unhex(requireNotNull(requesterKey))
        runCatchingNonCancellation {
            publisher.sendType1(WcCrypto.topicOf(receiver), receiver, error, WcRpcOptionsTable.authenticateReject)
        }
    }

    @Suppress("ReturnCount")
    private suspend fun onSessionRequest(message: WcRelayMessage, rpc: WcJsonRpc.Request) {
        val topic = message.topic
        val session = store.state.sessions.firstOrNull { it.topic == topic } ?: return
        val requestKey = handledRequestKey(topic, rpc.id)
        if (store.state.handled.containsKey(requestKey) || store.state.requests.any { it.topic == topic && it.id == rpc.id }) return
        val stored = try {
            readSessionRequest(session, rpc)
        } catch (refused: WcProtocolException) {
            reply(topic, WcJsonRpc.Error(rpc.id, refused.code, refused.message.orEmpty()), WcRpcOptionsTable.sessionResponse)
            return
        }
        val verify = verifier.resolve(message.attestation, message.message, session.peer.url)
        val request = stored.copy(verify = WcStoredVerify.of(verify))
        store.mutate { it.copy(requests = it.requests + request) }
        emit(WalletConnectEvent.SessionRequest(WcModels.request(request, session), verify))
    }

    private fun readSessionRequest(session: WcStoredSession, rpc: WcJsonRpc.Request): WcStoredRequest {
        val params = rpc.params as? JsonObject ?: refuse(WcErrors.INVALID_REQUEST, "Malformed request")
        val chainId = params.string("chainId")?.takeIf(WcLimits::isCaip2)
            ?: refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported chains.")
        val request = params["request"] as? JsonObject ?: refuse(WcErrors.INVALID_REQUEST, "Malformed request")
        val method = request.string("method") ?: refuse(WcErrors.INVALID_REQUEST, "Malformed request")
        val requestParams = (request["params"] ?: JsonNull).toString()
        if (requestParams.length > WcLimits.MAX_REQUEST_PARAMS_LENGTH) refuse(WcErrors.INVALID_REQUEST, "Request too large")
        val namespace = session.namespaces[chainId.substringBefore(':')]
        if (namespace == null || chainId !in WcPayloads.approvedChains(namespace)) {
            refuse(WcErrors.UNSUPPORTED_CHAINS, "Unsupported chains.")
        }
        if (method !in namespace.methods) refuse(WcErrors.UNAUTHORIZED_METHOD, "Unauthorized method.")
        if (method !in supportedMethods) refuse(WcErrors.UNSUPPORTED_METHODS, "Unsupported methods.")
        val now = clock.nowSeconds()
        val expiry = request.long("expiryTimestamp") ?: (now + WcRpcOptionsTable.REQUEST_TTL_SECONDS)
        if (expiry <= now) refuse(WcErrors.SESSION_REQUEST_EXPIRED, "Session Request Expired.")
        if (store.state.requests.count { it.topic == session.topic } >= WcLimits.MAX_PENDING_REQUESTS_PER_SESSION) {
            refuse(WcErrors.LIMIT_EXCEEDED, "Too many pending requests")
        }
        return WcStoredRequest(
            topic = session.topic,
            id = rpc.id,
            chainId = chainId,
            method = method,
            params = requestParams,
            expiry = minOf(expiry, now + WcRpcOptionsTable.REQUEST_MAX_EXPIRY_SECONDS),
            verify = WcStoredVerify.of(WalletConnectVerifyContext.unknown(session.peer.url))
        )
    }

    private suspend fun onResponse(topic: String, rpc: WcJsonRpc, isSession: Boolean) {
        val session = store.state.sessions.firstOrNull { it.topic == topic }
        if (isSession && session != null && session.settleRequestId == rpc.id) {
            val accepted = rpc is WcJsonRpc.Result && (rpc.result as? JsonPrimitive)?.booleanOrNull != false
            sessions.onSettled(topic, accepted)
            return
        }
        publisher.complete(rpc)
    }

    private suspend fun reply(topic: String, message: WcJsonRpc, options: WcRpcOptions) {
        // Best effort: an answer that cannot be sent leaves the dApp to time out, never the wallet in error.
        runCatchingNonCancellation { publisher.send(topic, message, options) }
    }

    private fun unsupported(rpc: WcJsonRpc.Request) =
        WcJsonRpc.Error(rpc.id, WcErrors.WC_METHOD_UNSUPPORTED, "Unsupported wc_ method. ${rpc.method.take(MAX_METHOD_ECHO)}")

    companion object {
        const val HANDLED_MESSAGE = "m:"
        private const val HANDLED_REQUEST = "r:"
        const val HANDLED_RETENTION_SECONDS = 2 * 86_400L
        private const val MAX_PROPOSAL_LIFETIME_SECONDS = 86_400L
        private const val MAX_METHOD_ECHO = 64

        fun handledRequestKey(topic: String, id: Long) = "$HANDLED_REQUEST$topic:$id"

        /** The settle answer the wallet sends in `wc_sessionSettle`'s controller field. */
        fun controllerJson(publicKey: String, metadata: WcStoredMetadata) = buildJsonObject {
            put("publicKey", publicKey)
            put("metadata", WcModels.metadataJson(metadata))
        }
    }
}
