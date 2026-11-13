package jp.co.soramitsu.tonconnect.impl.data

import java.security.MessageDigest

/**
 * Replay protection for TON Connect bridge requests, kept per dApp client id.
 *
 * The bridge re-delivers every queued message (dApps use a 300 s TTL) to a
 * subscriber that does not resume after its last event. For each connection
 * the ledger keeps the last SSE event id received from the bridge, so a
 * subscription resumes after it and a re-delivered event is dropped, and the
 * ids of the most recent requests already handed to the wallet, so a request
 * is shown at most once even if it reaches the wallet again.
 *
 * State is bounded and survives restarts. The in-memory copy stays
 * authoritative for the process when a durable write fails, so a storage
 * fault never stops request delivery.
 */
class TonConnectBridgeEventLedger(private val storage: Storage) {

    interface Storage {
        fun read(): String?

        /** Returns whether [value] reached durable storage. */
        fun write(value: String): Boolean
    }

    private data class Entry(
        val bridgeKey: String,
        val lastEventId: Long,
        val requestIds: List<Long>
    )

    private val lock = Any()
    private var loadedEntries: LinkedHashMap<String, Entry>? = null

    /**
     * The `Last-Event-ID` to resume a subscription for [clientIds] on [bridgeUrl]:
     * the oldest last event among them, or null while any of them has none, so
     * that no queued request for a newer connection is skipped.
     */
    fun resumeEventId(bridgeUrl: String, clientIds: Collection<String>): Long? = synchronized(lock) {
        if (clientIds.isEmpty()) return@synchronized null
        val bridgeKey = bridgeKey(bridgeUrl)
        val entries = entries()
        val lastEventIds = clientIds.map { clientId ->
            entries[normalize(clientId) ?: return@synchronized null]
                ?.takeIf { it.bridgeKey == bridgeKey }
                ?.lastEventId
                ?: return@synchronized null
        }
        lastEventIds.minOrNull()
    }

    /** Whether [eventId] is newer than the last event recorded for [clientId] on [bridgeUrl]. */
    fun isNewEvent(
        bridgeUrl: String,
        clientId: String,
        eventId: Long
    ): Boolean = synchronized(lock) {
        val key = normalize(clientId) ?: return@synchronized true
        val entry = entries()[key] ?: return@synchronized true
        entry.bridgeKey != bridgeKey(bridgeUrl) || eventId > entry.lastEventId
    }

    /** Records an event that carries no deliverable request, such as an unreadable one. */
    fun recordEvent(
        bridgeUrl: String,
        clientId: String,
        eventId: Long
    ) {
        synchronized(lock) {
            val key = normalize(clientId) ?: return
            update(key, bridgeUrl, eventId, requestId = null)
        }
    }

    /**
     * Records [eventId] and claims [requestId] for [clientId]. Returns false when
     * the request was already handed to the wallet, which must then drop it.
     */
    fun admitRequest(
        bridgeUrl: String,
        clientId: String,
        eventId: Long,
        requestId: Long
    ): Boolean = synchronized(lock) {
        val key = normalize(clientId) ?: return@synchronized true
        val alreadyHandled = entries()[key]?.requestIds?.contains(requestId) == true
        update(key, bridgeUrl, eventId, requestId.takeUnless { alreadyHandled })
        !alreadyHandled
    }

    private fun update(
        key: String,
        bridgeUrl: String,
        eventId: Long,
        requestId: Long?
    ) {
        val entries = entries()
        val previous = entries.remove(key)
        val bridgeKey = bridgeKey(bridgeUrl)
        val lastEventId = previous
            ?.takeIf { it.bridgeKey == bridgeKey }
            ?.lastEventId
            ?.coerceAtLeast(eventId)
            ?: eventId
        val requestIds = (previous?.requestIds.orEmpty() + listOfNotNull(requestId)).takeLast(MAX_REQUEST_IDS)
        entries[key] = Entry(bridgeKey, lastEventId, requestIds)
        while (entries.size > MAX_CONNECTIONS) {
            entries.remove(entries.keys.first())
        }
        persist(entries)
    }

    private fun entries(): LinkedHashMap<String, Entry> {
        return loadedEntries
            ?: decode(runCatching { storage.read() }.getOrNull()).also { loadedEntries = it }
    }

    private fun persist(entries: Map<String, Entry>) {
        // A failed write keeps the in-memory state, which still protects this process.
        runCatching { storage.write(encode(entries)) }
    }

    private fun encode(entries: Map<String, Entry>): String = buildString {
        append(VERSION)
        entries.forEach { (clientId, entry) ->
            append(LINE_SEPARATOR)
            append(clientId).append(FIELD_SEPARATOR)
            append(entry.bridgeKey).append(FIELD_SEPARATOR)
            append(entry.lastEventId).append(FIELD_SEPARATOR)
            append(entry.requestIds.joinToString(ID_SEPARATOR))
        }
    }

    private fun decode(encoded: String?): LinkedHashMap<String, Entry> {
        val entries = LinkedHashMap<String, Entry>()
        if (encoded == null || encoded.length > MAX_ENCODED_CHARS) return entries
        val lines = encoded.split(LINE_SEPARATOR)
        if (lines.first() != VERSION) return entries
        lines.drop(1).takeLast(MAX_CONNECTIONS).forEach { line ->
            decodeEntry(line)?.let { (clientId, entry) -> entries[clientId] = entry }
        }
        return entries
    }

    private fun decodeEntry(line: String): Pair<String, Entry>? = runCatching {
        val fields = line.split(FIELD_SEPARATOR)
        require(fields.size == ENTRY_FIELDS)
        val clientId = requireNotNull(normalize(fields[CLIENT_ID_FIELD]))
        val bridgeKey = fields[BRIDGE_KEY_FIELD].also { require(BRIDGE_KEY_REGEX.matches(it)) }
        val lastEventId = fields[LAST_EVENT_ID_FIELD].toLong()
        val requestIds = fields[REQUEST_IDS_FIELD].takeIf { it.isNotEmpty() }
            ?.split(ID_SEPARATOR)
            ?.map(String::toLong)
            .orEmpty()
        clientId to Entry(bridgeKey, lastEventId, requestIds.takeLast(MAX_REQUEST_IDS))
    }.getOrNull()

    private fun normalize(clientId: String): String? {
        return clientId.lowercase().takeIf { CLIENT_ID_REGEX.matches(it) }
    }

    private fun bridgeKey(bridgeUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bridgeUrl.trim().toByteArray(Charsets.UTF_8))
        return digest.take(BRIDGE_KEY_BYTES).joinToString(separator = "") { byte ->
            (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }
    }

    companion object {
        const val MAX_CONNECTIONS = 32
        const val MAX_REQUEST_IDS = 32

        private const val VERSION = "v1"
        private const val LINE_SEPARATOR = "\n"
        private const val FIELD_SEPARATOR = "|"
        private const val ID_SEPARATOR = ","
        private const val ENTRY_FIELDS = 4
        private const val CLIENT_ID_FIELD = 0
        private const val BRIDGE_KEY_FIELD = 1
        private const val LAST_EVENT_ID_FIELD = 2
        private const val REQUEST_IDS_FIELD = 3
        private const val BRIDGE_KEY_BYTES = 8
        private const val BYTE_MASK = 0xFF
        private const val HEX_RADIX = 16
        private const val MAX_ENCODED_CHARS = 65_536
        private val CLIENT_ID_REGEX = Regex("^[0-9a-f]{1,128}$")
        private val BRIDGE_KEY_REGEX = Regex("^[0-9a-f]{16}$")
    }
}
