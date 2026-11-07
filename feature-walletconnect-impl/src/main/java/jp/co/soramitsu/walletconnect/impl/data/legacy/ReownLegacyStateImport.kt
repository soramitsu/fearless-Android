package jp.co.soramitsu.walletconnect.impl.data.legacy

import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import jp.co.soramitsu.walletconnect.impl.presentation.WalletConnectMethod
import jp.co.soramitsu.walletconnect.impl.protocol.WcClientIdentity
import jp.co.soramitsu.walletconnect.impl.protocol.WcEncoding
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcPayloads
import jp.co.soramitsu.walletconnect.impl.protocol.WcRpcOptionsTable
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredPairing
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredProposalNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredRequest
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSession
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSessionNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredVerify
import jp.co.soramitsu.walletconnect.impl.protocol.long
import jp.co.soramitsu.walletconnect.impl.protocol.string
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** What an import carried over; counts and reason codes only, so it is safe to log. */
internal data class ReownImportSummary(
    val sessions: Int,
    val pairings: Int,
    val requests: Int,
    val identity: Boolean,
    val skipped: List<ReownSkipReason>
)

/**
 * Merges what the removed Reown SDK kept ([ReownLegacySnapshot]) into the in-repo client's state, so
 * that every dApp the user had connected keeps working after the update:
 * - only sessions the SDK showed as connected come over: settled (acknowledged) and unexpired, as
 *   `WalletKit.getListOfActiveSessions()` listed them;
 * - a topic the client already has is never replaced, so running the import twice changes nothing;
 * - the client's own limits apply (sessions, pairings, requests per session, metadata sizes), and an
 *   unanswered request must name a chain and method its session approved, as a live request must;
 * - the relay identity is kept unless the client already made its own.
 */
internal object ReownLegacyStateImport {

    fun merge(
        current: WcStoredState,
        snapshot: ReownLegacySnapshot,
        nowSeconds: Long
    ): Pair<WcStoredState, ReownImportSummary> {
        val skipped = snapshot.skipped.toMutableList()
        val pairingPeers = snapshot.pairings.associate { it.topic to it.peer }
        val sessions = importSessions(current, snapshot.sessions, pairingPeers, nowSeconds, skipped)
        val pairings = importPairings(current, snapshot.pairings, nowSeconds, skipped)
        val allSessions = current.sessions + sessions
        val requests = importRequests(current, allSessions, snapshot.pendingRequests, nowSeconds, skipped)
        val importedSeed = snapshot.clientSeed?.takeIf { current.clientSeed == null && WcEncoding.isHex(it, WcClientIdentity.SEED_LENGTH) }
        val merged = current.copy(
            clientSeed = current.clientSeed ?: importedSeed,
            sessions = allSessions,
            pairings = current.pairings + pairings,
            requests = current.requests + requests
        )
        return merged to ReownImportSummary(sessions.size, pairings.size, requests.size, importedSeed != null, skipped)
    }

    private fun importSessions(
        current: WcStoredState,
        legacy: List<ReownLegacySession>,
        pairingPeers: Map<String, ReownLegacyMetadata?>,
        nowSeconds: Long,
        skipped: MutableList<ReownSkipReason>
    ): List<WcStoredSession> {
        val known = current.sessions.map { it.topic }.toSet()
        val usable = legacy.filter { it.topic !in known }.filter { session ->
            val problem = when {
                !session.acknowledged -> ReownSkipReason.SESSION_NOT_SETTLED
                session.expiry <= nowSeconds -> ReownSkipReason.SESSION_EXPIRED
                else -> null
            }
            problem?.let { skipped += it }
            problem == null
        }.sortedByDescending { it.expiry }
        val room = (WcLimits.MAX_SESSIONS - current.sessions.size).coerceAtLeast(0)
        repeat((usable.size - room).coerceAtLeast(0)) { skipped += ReownSkipReason.LIMIT_REACHED }
        return usable.take(room).map { session ->
            WcStoredSession(
                topic = session.topic,
                symKey = session.symKey,
                selfPublicKey = session.selfPublicKey,
                peerPublicKey = session.peerPublicKey,
                pairingTopic = session.pairingTopic,
                relayProtocol = session.relayProtocol,
                expiry = session.expiry,
                acknowledged = true,
                settleRequestId = null,
                namespaces = session.namespaces.mapValues { (_, namespace) -> namespace.toStored() },
                requiredNamespaces = session.requiredNamespaces.mapValues { (_, namespace) -> namespace.toStored() },
                optionalNamespaces = session.optionalNamespaces.mapValues { (_, namespace) -> namespace.toStored() },
                peer = storedMetadata(session.peer ?: pairingPeers[session.pairingTopic]),
                verify = null
            )
        }
    }

    private fun importPairings(
        current: WcStoredState,
        legacy: List<ReownLegacyPairing>,
        nowSeconds: Long,
        skipped: MutableList<ReownSkipReason>
    ): List<WcStoredPairing> {
        val known = current.pairings.map { it.topic }.toSet()
        val usable = legacy.filter { it.topic !in known }.filter { pairing ->
            (pairing.expiry > nowSeconds).also { live -> if (!live) skipped += ReownSkipReason.PAIRING_EXPIRED }
        }.sortedByDescending { it.expiry }
        val room = (WcLimits.MAX_PAIRINGS - current.pairings.size).coerceAtLeast(0)
        repeat((usable.size - room).coerceAtLeast(0)) { skipped += ReownSkipReason.LIMIT_REACHED }
        return usable.take(room).map { pairing ->
            WcStoredPairing(
                topic = pairing.topic,
                symKey = pairing.symKey,
                relayProtocol = pairing.relayProtocol,
                expiry = pairing.expiry,
                active = pairing.active,
                peer = pairing.peer?.let(::storedMetadata)
            )
        }
    }

    private fun importRequests(
        current: WcStoredState,
        sessions: List<WcStoredSession>,
        legacy: List<ReownLegacyRequest>,
        nowSeconds: Long,
        skipped: MutableList<ReownSkipReason>
    ): List<WcStoredRequest> {
        val known = current.requests.map { it.topic to it.id }.toSet()
        val byTopic = sessions.associateBy { it.topic }
        val imported = mutableListOf<WcStoredRequest>()
        legacy.filter { (it.topic to it.id) !in known }.forEach { request ->
            val session = byTopic[request.topic]
            val pending = (current.requests + imported).count { it.topic == request.topic }
            val outcome = when {
                session == null -> RequestImport.Skipped(ReownSkipReason.REQUEST_NOT_ALLOWED)
                pending >= WcLimits.MAX_PENDING_REQUESTS_PER_SESSION -> RequestImport.Skipped(ReownSkipReason.LIMIT_REACHED)
                else -> storedRequest(request, session, nowSeconds)
            }
            when (outcome) {
                is RequestImport.Stored -> imported += outcome.request
                is RequestImport.Skipped -> skipped += outcome.reason
            }
        }
        return imported
    }

    private sealed interface RequestImport {
        class Stored(val request: WcStoredRequest) : RequestImport

        class Skipped(val reason: ReownSkipReason) : RequestImport
    }

    // The request as the client's handler would have stored it, or why it cannot be.
    private fun storedRequest(
        request: ReownLegacyRequest,
        session: WcStoredSession,
        nowSeconds: Long
    ): RequestImport {
        val params = parseObject(request.body)?.get("params") as? JsonObject
        val inner = params?.get("request") as? JsonObject
        val chainId = params?.string("chainId")?.takeIf(WcLimits::isCaip2)
        val method = inner?.string("method")
        val requestParams = (inner?.get("params") ?: JsonNull).toString()
        if (inner == null || chainId == null || method == null || requestParams.length > WcLimits.MAX_REQUEST_PARAMS_LENGTH) {
            return RequestImport.Skipped(ReownSkipReason.REQUEST_UNREADABLE)
        }
        val namespace = session.namespaces[chainId.substringBefore(':')]
        val allowed = namespace != null && chainId in WcPayloads.approvedChains(namespace) &&
            method in namespace.methods && method in SUPPORTED_METHODS
        // Requests without an expiryTimestamp got the SDK's default lifetime from when they were sent;
        // WalletConnect request ids are microsecond timestamps.
        val expiry = inner.long("expiryTimestamp") ?: (request.id / MICROSECONDS_PER_SECOND + WcRpcOptionsTable.REQUEST_TTL_SECONDS)
        return when {
            !allowed -> RequestImport.Skipped(ReownSkipReason.REQUEST_NOT_ALLOWED)
            expiry <= nowSeconds -> RequestImport.Skipped(ReownSkipReason.REQUEST_EXPIRED)
            else -> RequestImport.Stored(
                WcStoredRequest(
                    topic = session.topic,
                    id = request.id,
                    chainId = chainId,
                    method = method,
                    params = requestParams,
                    expiry = minOf(expiry, nowSeconds + WcRpcOptionsTable.REQUEST_MAX_EXPIRY_SECONDS),
                    verify = storedVerify(request.verify, session.peer.url)
                )
            )
        }
    }

    // Parser messages quote the input, which may hold a dApp's payload: they are dropped, never passed on.
    private fun parseObject(body: String): JsonObject? = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (ignored: SerializationException) {
        null
    } catch (ignored: IllegalArgumentException) {
        null
    }

    private fun storedVerify(verify: ReownLegacyVerify?, claimedUrl: String): WcStoredVerify {
        val validation = WalletConnectValidation.values().firstOrNull { it.name == verify?.validation } ?: WalletConnectValidation.UNKNOWN
        return WcStoredVerify(
            validation = validation.name,
            verifiedOrigin = verify?.origin?.takeIf { validation != WalletConnectValidation.UNKNOWN },
            claimedUrl = claimedUrl.takeIf { it.isNotEmpty() },
            isScam = verify?.isScam == true
        )
    }

    // The same bounds the client puts on metadata a dApp sends; a session without any keeps working, unnamed.
    private fun storedMetadata(metadata: ReownLegacyMetadata?): WcStoredMetadata = WcStoredMetadata(
        name = WcLimits.displayText(metadata?.name.orEmpty(), WcLimits.MAX_NAME_DISPLAY_LENGTH),
        description = WcLimits.displayText(metadata?.description.orEmpty(), WcLimits.MAX_DESCRIPTION_LENGTH),
        url = metadata?.url?.takeIf { it.length <= WcLimits.MAX_URL_LENGTH }.orEmpty(),
        icons = metadata?.icons.orEmpty()
            .filter { it.length <= WcLimits.MAX_URL_LENGTH && (it.startsWith("https://") || it.startsWith("http://")) }
            .take(WcLimits.MAX_ICONS),
        redirect = metadata?.redirect?.takeIf { it.length <= WcLimits.MAX_URL_LENGTH }
    )

    private fun ReownLegacyProposalNamespace.toStored() = WcStoredProposalNamespace(chains, methods, events)

    private fun ReownLegacySessionNamespace.toStored() = WcStoredSessionNamespace(chains, accounts, methods, events)

    private const val MICROSECONDS_PER_SECOND = 1_000_000L
    private val SUPPORTED_METHODS = WalletConnectMethod.values().map { it.method }.toSet()
}

/** The client's state as its store reads and writes it (the same JSON settings). */
internal object WcStoredStateJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(state: WcStoredState): String = json.encodeToString(WcStoredState.serializer(), state)

    // As the store does: what cannot be read (or is of another version) counts as nothing stored.
    fun decodeOrEmpty(text: String?): WcStoredState {
        if (text == null) return WcStoredState()
        val decoded = try {
            json.decodeFromString(WcStoredState.serializer(), text)
        } catch (ignored: SerializationException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
        return decoded?.takeIf { it.version == WcStoredState.CURRENT_VERSION } ?: WcStoredState()
    }
}
