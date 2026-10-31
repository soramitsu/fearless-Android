package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd

/**
 * A dApp `sendTransaction` request as the dApp sent it. Optional fields stay
 * null when absent so the signing policy can tell "not set" from "invalid".
 */
@Parcelize
data class TonConnectSignRequest(
    val fromValue: String?,
    /** Unix seconds, or null when the dApp set no expiry. Malformed values become 0 (expired). */
    val validUntil: Long?,
    val messages: List<TonConnectRawMessage>,
    /** The raw `network` value (for example "-239"), or null when the dApp sent none. */
    val networkValue: String?
) : Parcelable {

    @IgnoredOnParcel
    val from: AddrStd?
        get() = fromValue?.let { AddrStd.parse(it) }

    constructor(json: JSONObject) : this(
        fromValue = parseFrom(json),
        validUntil = parseValidUntil(json),
        messages = parseMessages(json.getJSONArray("messages")),
        networkValue = parseNetwork(json)
    )

    constructor(value: String) : this(JSONObject(value))

    constructor(value: Any) : this(value.toString())

    companion object {

        private val VALID_UNTIL_KEYS = listOf("valid_until", "validUntil")
        private const val NETWORK_KEY = "network"
        private const val MILLISECONDS_THRESHOLD = 1_000_000_000_000L
        private const val MILLIS_PER_SECOND = 1_000L

        /** Stored for a present but unreadable expiry, so that it is rejected as already expired. */
        internal const val INVALID_VALID_UNTIL = 0L

        fun parse(array: JSONArray): List<TonConnectSignRequest> {
            val requests = mutableListOf<TonConnectSignRequest>()
            for (i in 0 until array.length()) {
                requests.add(TonConnectSignRequest(array.get(i)))
            }
            return requests.toList()
        }

        private fun parseValidUntil(json: JSONObject): Long? {
            val key = VALID_UNTIL_KEYS.firstOrNull { json.has(it) && !json.isNull(it) } ?: return null
            return normalizeValidUntil(json.get(key))
        }

        /**
         * Converts a dApp `valid_until` to unix seconds. Millisecond timestamps are
         * accepted for compatibility; anything that is not a number is invalid.
         */
        internal fun normalizeValidUntil(raw: Any?): Long? {
            val value = when (raw) {
                null -> return null
                is Number -> raw.toLong()
                is String -> raw.trim().let { it.toLongOrNull() ?: it.toBigDecimalOrNull()?.toLong() }
                else -> null
            } ?: return INVALID_VALID_UNTIL
            return if (value > MILLISECONDS_THRESHOLD) value / MILLIS_PER_SECOND else value
        }

        private fun parseNetwork(json: JSONObject): String? {
            if (!json.has(NETWORK_KEY) || json.isNull(NETWORK_KEY)) return null
            return normalizeNetwork(json.get(NETWORK_KEY))
        }

        internal fun normalizeNetwork(raw: Any?): String? = raw?.toString()?.trim()

        private fun parseMessages(array: JSONArray): List<TonConnectRawMessage> {
            val messages = mutableListOf<TonConnectRawMessage>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                messages.add(TonConnectRawMessage(json))
            }
            return messages
        }

        private fun parseFrom(json: JSONObject): String? {
            return if (json.has("from") && !json.isNull("from")) {
                json.getString("from")
            } else if (json.has("source") && !json.isNull("source")) {
                json.getString("source")
            } else {
                null
            }
        }
    }
}
