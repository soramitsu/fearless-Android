package co.jp.soramitsu.tonconnect.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class ConnectRequest(
    val manifestUrl: String,
    val items: List<Item>,
): Parcelable {

    val proofPayload: String?
        get() = items.filterIsInstance<Item.TonProof>().firstOrNull()?.payload

    @Parcelize
    sealed class Item: Parcelable {
        data object TonAddress: Item()

        data class TonProof(val payload: String): Item()
    }
    companion object {
        fun parse(data: String?): ConnectRequest {
            if (data.isNullOrBlank()) {
                throw TonConnectException.RequestParsingError(data)
            }

            try {
                val json = JSONObject(data)
                return parse(json)
            } catch (e: Exception) {
                throw TonConnectException.RequestParsingError(data)
            }
        }

        fun parse(json: JSONObject): ConnectRequest {
            val manifestUrl = json.optStringCompat("manifestUrl", "manifesturl")
            if (manifestUrl.isNullOrBlank()) {
                throw TonConnectException.RequestParsingError(json.toString())
            }

            return ConnectRequest(
                manifestUrl = parseManifestUrl(manifestUrl),
                items = parseItems(json.getJSONArray("items"))
            )
        }

        private fun parseManifestUrl(value: String): String {
            return value.removeSuffix("/")
        }

        private fun parseItems(array: JSONArray): List<Item> {
            val items = mutableListOf<Item>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                when (item.getString("name")) {
                    "ton_addr" -> items.add(Item.TonAddress)
                    "ton_proof" -> items.add(Item.TonProof(item.getString("payload")))
                }
            }

            return items.toList()
        }
    }
}