package jp.co.soramitsu.walletconnect.impl.data.legacy

import jp.co.soramitsu.walletconnect.impl.protocol.WcClientIdentity
import jp.co.soramitsu.walletconnect.impl.protocol.WcEncoding
import jp.co.soramitsu.walletconnect.impl.protocol.WcLimits
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredMetadata
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredPairing
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredProposalNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSession
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredSessionNamespace
import jp.co.soramitsu.walletconnect.impl.protocol.WcStoredState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What an import carried over; counts and reason codes only, so it is safe to log. */
internal data class ReownImportSummary(
    val sessions: Int,
    /** Of [sessions], those already expired: the client reports them as ended when it starts. */
    val expiredSessions: Int,
    val pairings: Int,
    val identity: Boolean,
    val skipped: List<ReownSkipReason>
) {
    /** Whether everything still usable came over; only then may the SDK's files be deleted. */
    val isComplete: Boolean get() = ReownSkipReason.LIMIT_REACHED !in skipped
}

/**
 * Merges what the removed Reown SDK kept ([ReownLegacySnapshot]) into the in-repo client's state, so
 * that every dApp the user had connected keeps working after the update:
 * - every keyed, complete session and pairing comes over, settled or not and expired or not. Expired ones
 *   are kept so that the client reports them when it starts, through its own expiry path, instead of
 *   their vanishing silently;
 * - a topic the client already has is never replaced, so running the import twice changes nothing;
 * - the client's limits apply, live records first; a live record left out over a limit makes the import
 *   incomplete, and the SDK's files are then kept;
 * - the relay identity is kept unless the client already made its own;
 * - proposals and requests that were in flight are not carried over.
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
        val legacySeed = snapshot.clientSeed?.takeIf { WcEncoding.isHex(it, WcClientIdentity.SEED_LENGTH) }
        val importedSeed = legacySeed.takeIf { current.clientSeed == null }
        val merged = current.copy(
            clientSeed = current.clientSeed ?: importedSeed,
            sessions = current.sessions + sessions,
            pairings = current.pairings + pairings
        )
        val summary = ReownImportSummary(
            sessions = sessions.size,
            expiredSessions = sessions.count { it.expiry <= nowSeconds },
            pairings = pairings.size,
            identity = importedSeed != null,
            skipped = skipped
        )
        return merged to summary
    }

    private fun importSessions(
        current: WcStoredState,
        legacy: List<ReownLegacySession>,
        pairingPeers: Map<String, ReownLegacyMetadata?>,
        nowSeconds: Long,
        skipped: MutableList<ReownSkipReason>
    ): List<WcStoredSession> {
        val known = current.sessions.map { it.topic }.toSet()
        val room = (WcLimits.MAX_SESSIONS - current.sessions.size).coerceAtLeast(0)
        val (kept, leftOut) = legacy.filter { it.topic !in known }.splitByRoom(room) { it.expiry }
        leftOut.forEach { skipped += leftOutReason(it.expiry, nowSeconds, ReownSkipReason.SESSION_EXPIRED) }
        return kept.map { session ->
            WcStoredSession(
                topic = session.topic,
                symKey = session.symKey,
                selfPublicKey = session.selfPublicKey,
                peerPublicKey = session.peerPublicKey,
                pairingTopic = session.pairingTopic,
                relayProtocol = session.relayProtocol,
                expiry = session.expiry,
                acknowledged = session.acknowledged,
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
        val room = (WcLimits.MAX_PAIRINGS - current.pairings.size).coerceAtLeast(0)
        val (kept, leftOut) = legacy.filter { it.topic !in known }.splitByRoom(room) { it.expiry }
        leftOut.forEach { skipped += leftOutReason(it.expiry, nowSeconds, ReownSkipReason.PAIRING_EXPIRED) }
        return kept.map { pairing ->
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

    // The records that fit in [room], newest expiry first, and those left over.
    private fun <T> List<T>.splitByRoom(room: Int, expiry: (T) -> Long): Pair<List<T>, List<T>> =
        sortedByDescending(expiry).let { it.take(room) to it.drop(room) }

    // A live record left out over a limit makes the import incomplete; an expired one does not.
    private fun leftOutReason(
        expiry: Long,
        nowSeconds: Long,
        expired: ReownSkipReason
    ): ReownSkipReason = if (expiry > nowSeconds) ReownSkipReason.LIMIT_REACHED else expired

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
