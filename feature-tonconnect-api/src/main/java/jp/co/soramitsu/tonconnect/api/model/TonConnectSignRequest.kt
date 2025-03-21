package jp.co.soramitsu.tonconnect.api.model

import android.os.Parcelable
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.TonNetwork
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import org.ton.block.AddrStd

@Parcelize
data class TonConnectSignRequest(
    val fromValue: String?,
    val validUntil: Long,
    val messages: List<TonConnectRawMessage>,
    val network: TonNetwork
) : Parcelable {

    @IgnoredOnParcel
    val from: AddrStd?
        get() = fromValue?.let { AddrStd.parse(it) }

    constructor(json: JSONObject) : this(
        fromValue = parseFrom(json),
        validUntil = parseValidUnit(json),
        messages = parseMessages(json.getJSONArray("messages")),
        network = parseNetwork(json.opt("network"))
    )

    constructor(value: String) : this(JSONObject(value))

    constructor(value: Any) : this(value.toString())

    companion object {

        fun parse(array: JSONArray): List<TonConnectSignRequest> {
            val requests = mutableListOf<TonConnectSignRequest>()
            for (i in 0 until array.length()) {
                requests.add(TonConnectSignRequest(array.get(i)))
            }
            return requests.toList()
        }

        @Suppress("MagicNumber")
        private fun parseValidUnit(json: JSONObject): Long {
            val value = json.optLong("valid_until", json.optLong("validUntil", 0))
            if (value > 1_000_000_000_000) {
                return value / 1000
            }
            if (value > 1_000_000_000) {
                return value
            }
            return 0
        }

        private fun parseMessages(array: JSONArray): List<TonConnectRawMessage> {
            val messages = mutableListOf<TonConnectRawMessage>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                messages.add(TonConnectRawMessage(json))
            }
            return messages
        }

        private fun parseFrom(json: JSONObject): String? {
            return if (json.has("from")) {
                json.getString("from")
            } else if (json.has("source")) {
                json.getString("source")
            } else {
                null
            }
        }

        private fun parseNetwork(value: Any?): TonNetwork {
            if (value == null) {
                return TonNetwork.MAINNET
            }
            if (value is String) {
                return parseNetwork(value.toIntOrNull())
            }
            if (value !is Int) {
                return parseNetwork(value.toString())
            }
            return if (value == TonNetwork.TESTNET.value) {
                TonNetwork.TESTNET
            } else {
                TonNetwork.MAINNET
            }
        }
    }
}
