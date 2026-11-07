package jp.co.soramitsu.walletconnect.impl.data.legacy

import java.security.MessageDigest

/**
 * What the removed Reown SDK (Fearless Android 4.3.1 and earlier) kept for the wallet, read back once on
 * upgrade so that every paired dApp keeps working: sessions with their keys, pairings and the relay
 * identity. Proposals and requests that were in flight are not part of it.
 */
internal data class ReownLegacySnapshot(
    /** The Ed25519 seed (hex) of the relay identity the SDK signed its relay sign-in with. */
    val clientSeed: String?,
    val pairings: List<ReownLegacyPairing>,
    val sessions: List<ReownLegacySession>,
    /** Why records were left behind: fixed codes only, never a topic, key or payload. */
    val skipped: List<ReownSkipReason>
)

/** Why a record the SDK kept was not carried over. Codes only, so they are safe in logs. */
internal enum class ReownSkipReason {
    /** No topic, no key, or a key that does not hash to the topic: the session cannot be used. */
    SESSION_INCOMPLETE,

    /** No namespaces: nothing was approved, so nothing can be requested over it. */
    SESSION_WITHOUT_NAMESPACES,

    /** Expired, and left out only because the client's limit was reached. */
    SESSION_EXPIRED,

    /** No topic or no key: the pairing cannot be used. */
    PAIRING_INCOMPLETE,

    /** Expired, and left out only because the client's limit was reached. */
    PAIRING_EXPIRED,

    /** The relay identity's key pair is not readable; the client makes a new one. */
    IDENTITY_UNREADABLE,

    /** Still usable but over the client's limit: the SDK's files are then kept. */
    LIMIT_REACHED
}

internal data class ReownLegacyMetadata(
    val name: String,
    val description: String,
    val url: String,
    val icons: List<String>,
    val redirect: String?
)

internal data class ReownLegacyPairing(
    val topic: String,
    val symKey: String,
    val relayProtocol: String,
    /** Unix seconds. */
    val expiry: Long,
    val active: Boolean,
    val peer: ReownLegacyMetadata?
)

internal data class ReownLegacySessionNamespace(
    val chains: List<String>?,
    val accounts: List<String>,
    val methods: List<String>,
    val events: List<String>
)

internal data class ReownLegacyProposalNamespace(
    val chains: List<String>?,
    val methods: List<String>,
    val events: List<String>
)

internal data class ReownLegacySession(
    val topic: String,
    val symKey: String,
    val selfPublicKey: String,
    val peerPublicKey: String,
    val pairingTopic: String,
    val relayProtocol: String,
    /** Unix seconds. */
    val expiry: Long,
    val acknowledged: Boolean,
    val namespaces: Map<String, ReownLegacySessionNamespace>,
    val requiredNamespaces: Map<String, ReownLegacyProposalNamespace>,
    val optionalNamespaces: Map<String, ReownLegacyProposalNamespace>,
    /** The dApp's metadata; null when the SDK had none stored (the session still works). */
    val peer: ReownLegacyMetadata?
)

/** The SDK's two databases (already decrypted) and its key chain (tag to hex value). */
internal class ReownLegacySources(
    val keyChain: Map<String, String>,
    val signDatabase: SqliteDatabaseImage?,
    val coreDatabase: SqliteDatabaseImage?
)

/**
 * Turns the SDK's tables and key-chain tags into a [ReownLegacySnapshot]. The layout is that of Reown
 * Kotlin 1.6.9 (and the WalletConnect Kotlin SDK before the rename, which used the same names):
 * - `WalletConnectV2.db`: SessionDao, NamespaceDao, ProposalNamespaceDao (required namespaces) and
 *   OptionalNamespaceDao, joined on session_id; list columns hold comma-separated values;
 * - `WalletConnectAndroidCore.db`: Pairing and MetaData (by sequence_topic and type PEER/SELF);
 * - key chain: a topic's symmetric key under the topic, `key_agreement/<topic>` = self + peer public keys,
 *   `key_did_keypair` = relay identity private + public key.
 *
 * Every keyed, complete session and pairing is read, settled or not and expired or not: the client
 * reports what expired once it starts, as it does for its own records. Only records that cannot be used
 * (no key, a key that does not match, no namespaces) are left behind, each with a reason code.
 */
internal class ReownLegacySnapshotReader {

    fun read(sources: ReownLegacySources): ReownLegacySnapshot {
        val skipped = mutableListOf<ReownSkipReason>()
        val peers = peerMetadata(sources.coreDatabase)
        return ReownLegacySnapshot(
            clientSeed = clientSeed(sources.keyChain, skipped),
            pairings = readPairings(sources, peers, skipped),
            sessions = readSessions(sources, peers, skipped),
            skipped = skipped
        )
    }

    private fun readSessions(
        sources: ReownLegacySources,
        peers: Map<String, ReownLegacyMetadata>,
        skipped: MutableList<ReownSkipReason>
    ): List<ReownLegacySession> {
        val database = sources.signDatabase ?: return emptyList()
        val namespaces = database.readTable(NAMESPACE_TABLE).orEmpty().groupBy { it.long(SESSION_ID) }
        val required = database.readTable(REQUIRED_NAMESPACE_TABLE).orEmpty().groupBy { it.long(SESSION_ID) }
        val optional = database.readTable(OPTIONAL_NAMESPACE_TABLE).orEmpty().groupBy { it.long(SESSION_ID) }
        return database.readTable(SESSION_TABLE).orEmpty().mapNotNull { row ->
            val id = row.long(ID)
            val session = sessionOf(row, sources.keyChain, peers[row.string(TOPIC)?.lowercase()])?.copy(
                namespaces = namespaces[id].orEmpty().associate { it.requireString(KEY) to sessionNamespaceOf(it) },
                requiredNamespaces = required[id].orEmpty().associate { it.requireString(KEY) to proposalNamespaceOf(it) },
                optionalNamespaces = optional[id].orEmpty().associate { it.requireString(KEY) to proposalNamespaceOf(it) }
            )
            val problem = when {
                session == null -> ReownSkipReason.SESSION_INCOMPLETE
                session.namespaces.isEmpty() -> ReownSkipReason.SESSION_WITHOUT_NAMESPACES
                else -> null
            }
            if (problem != null) skipped += problem
            session.takeIf { problem == null }
        }
    }

    private fun sessionOf(
        row: SqliteRow,
        keyChain: Map<String, String>,
        peer: ReownLegacyMetadata?
    ): ReownLegacySession? {
        val topic = row.string(TOPIC)?.lowercase() ?: return null
        val symKey = keyChain[topic]?.lowercase()?.takeIf { it.isKeyHex() && sha256Hex(it) == topic } ?: return null
        val agreement = keyChain["$KEY_AGREEMENT_PREFIX$topic"]?.takeIf { it.length == 2 * KEY_HEX_LENGTH }
        val selfPublicKey = row.string(SELF_PARTICIPANT) ?: agreement?.take(KEY_HEX_LENGTH)
        val peerPublicKey = row.string(PEER_PARTICIPANT)?.takeIf { it.isNotEmpty() } ?: agreement?.drop(KEY_HEX_LENGTH)
        val expiry = row.long(EXPIRY)
        if (selfPublicKey == null || peerPublicKey == null || expiry == null) return null
        return ReownLegacySession(
            topic = topic,
            symKey = symKey,
            selfPublicKey = selfPublicKey.lowercase(),
            peerPublicKey = peerPublicKey.lowercase(),
            pairingTopic = row.string(PAIRING_TOPIC).orEmpty(),
            relayProtocol = row.string(RELAY_PROTOCOL) ?: DEFAULT_RELAY_PROTOCOL,
            expiry = expiry,
            acknowledged = row.boolean(IS_ACKNOWLEDGED) ?: false,
            namespaces = emptyMap(),
            requiredNamespaces = emptyMap(),
            optionalNamespaces = emptyMap(),
            peer = peer
        )
    }

    private fun readPairings(
        sources: ReownLegacySources,
        peers: Map<String, ReownLegacyMetadata>,
        skipped: MutableList<ReownSkipReason>
    ): List<ReownLegacyPairing> = sources.coreDatabase?.readTable(PAIRING_TABLE).orEmpty().mapNotNull { row ->
        val topic = row.string(TOPIC)?.lowercase()
        val symKey = topic?.let { sources.keyChain[it]?.lowercase() }?.takeIf { it.isKeyHex() }
        val expiry = row.long(EXPIRY)
        if (topic == null || symKey == null || expiry == null) {
            skipped += ReownSkipReason.PAIRING_INCOMPLETE
            null
        } else {
            ReownLegacyPairing(
                topic = topic,
                symKey = symKey,
                relayProtocol = row.string(RELAY_PROTOCOL) ?: DEFAULT_RELAY_PROTOCOL,
                expiry = expiry,
                active = row.boolean(IS_ACTIVE) ?: false,
                peer = peers[topic]
            )
        }
    }

    private fun peerMetadata(coreDatabase: SqliteDatabaseImage?): Map<String, ReownLegacyMetadata> =
        coreDatabase?.readTable(METADATA_TABLE).orEmpty()
            .filter { it.string(TYPE) == PEER_TYPE && it.string(SEQUENCE_TOPIC) != null }
            .associate { row ->
                row.requireString(SEQUENCE_TOPIC).lowercase() to ReownLegacyMetadata(
                    name = row.string(NAME).orEmpty(),
                    description = row.string(DESCRIPTION).orEmpty(),
                    url = row.string(URL).orEmpty(),
                    icons = row.string(ICONS).toList().orEmpty(),
                    redirect = row.string(NATIVE_REDIRECT)?.takeIf { it.isNotEmpty() }
                )
            }

    private fun clientSeed(keyChain: Map<String, String>, skipped: MutableList<ReownSkipReason>): String? {
        val pair = keyChain[CLIENT_ID_KEYPAIR_TAG] ?: return null
        val seed = pair.take(KEY_HEX_LENGTH).lowercase()
        if (pair.length != 2 * KEY_HEX_LENGTH || !seed.isKeyHex()) {
            skipped += ReownSkipReason.IDENTITY_UNREADABLE
            return null
        }
        return seed
    }

    private fun sessionNamespaceOf(row: SqliteRow) = ReownLegacySessionNamespace(
        chains = row.string(CHAINS).toList(),
        accounts = row.string(ACCOUNTS).toList().orEmpty(),
        methods = row.string(METHODS).toList().orEmpty(),
        events = row.string(EVENTS).toList().orEmpty()
    )

    private fun proposalNamespaceOf(row: SqliteRow) = ReownLegacyProposalNamespace(
        chains = row.string(CHAINS).toList(),
        methods = row.string(METHODS).toList().orEmpty(),
        events = row.string(EVENTS).toList().orEmpty()
    )

    companion object {
        const val SIGN_DATABASE = "WalletConnectV2.db"
        const val CORE_DATABASE = "WalletConnectAndroidCore.db"
        const val CLIENT_ID_KEYPAIR_TAG = "key_did_keypair"
        private const val KEY_AGREEMENT_PREFIX = "key_agreement/"
        private const val KEY_HEX_LENGTH = 64
        private const val DEFAULT_RELAY_PROTOCOL = "irn"
        private const val PEER_TYPE = "PEER"
        private const val SESSION_TABLE = "SessionDao"
        private const val NAMESPACE_TABLE = "NamespaceDao"
        private const val REQUIRED_NAMESPACE_TABLE = "ProposalNamespaceDao"
        private const val OPTIONAL_NAMESPACE_TABLE = "OptionalNamespaceDao"
        private const val PAIRING_TABLE = "Pairing"
        private const val METADATA_TABLE = "MetaData"
        private const val ID = "id"
        private const val SESSION_ID = "session_id"
        private const val KEY = "key"
        private const val TOPIC = "topic"
        private const val PAIRING_TOPIC = "pairingTopic"
        private const val EXPIRY = "expiry"
        private const val RELAY_PROTOCOL = "relay_protocol"
        private const val SELF_PARTICIPANT = "self_participant"
        private const val PEER_PARTICIPANT = "peer_participant"
        private const val IS_ACKNOWLEDGED = "is_acknowledged"
        private const val IS_ACTIVE = "is_active"
        private const val CHAINS = "chains"
        private const val ACCOUNTS = "accounts"
        private const val METHODS = "methods"
        private const val EVENTS = "events"
        private const val SEQUENCE_TOPIC = "sequence_topic"
        private const val TYPE = "type"
        private const val NAME = "name"
        private const val DESCRIPTION = "description"
        private const val URL = "url"
        private const val ICONS = "icons"
        private const val NATIVE_REDIRECT = "native"
    }
}

// The SDK's list column adapter: comma-separated values, blank for an empty list; SQL NULL stays null.
internal fun String?.toList(): List<String>? = when {
    this == null -> null
    isBlank() -> emptyList()
    else -> split(",")
}

internal fun sha256Hex(hex: String): String = MessageDigest.getInstance("SHA-256").digest(hex.hexToBytes()).toHex()

internal fun String.isKeyHex(): Boolean = length == 64 && all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

internal fun String.hexToBytes(): ByteArray =
    ByteArray(length / 2) { index -> substring(2 * index, 2 * index + 2).toInt(16).toByte() }

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

private fun SqliteRow.requireString(column: String): String =
    string(column) ?: throw LegacyDatabaseException("Missing $column")
