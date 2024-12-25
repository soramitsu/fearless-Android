package co.jp.soramitsu.tonconnect.model

import android.os.Parcelable
import jp.co.soramitsu.common.utils.safeParseCell
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject
import org.ton.block.AddrStd
import org.ton.block.Coins
import org.ton.block.StateInit
import org.ton.cell.Cell

@Parcelize
data class TonConnectRawMessage(
    val addressValue: String,
    val amount: Long,
    val stateInitValue: String?,
    val payloadValue: String?
) : Parcelable {

    @IgnoredOnParcel
    val address: AddrStd by lazy {
        AddrStd.parse(addressValue)
    }

    @IgnoredOnParcel
    val coins: Coins by lazy {
        Coins.ofNano(amount)
    }

    @IgnoredOnParcel
    val stateInit: StateInit? by lazy {
        stateInitValue?.toTlb()
    }

    @IgnoredOnParcel
    val payload: Cell by lazy {
        payloadValue?.safeParseCell() ?: Cell()
    }

    constructor(json: JSONObject) : this(
        json.getString("address"),
        parseAmount(json.get("amount")),
        json.optString("stateInit"),
        json.optString("payload")
    )

    private companion object {

        private fun parseAmount(value: Any): Long {
            if (value is Long) {
                return value
            }
            return value.toString().toLong()
        }
    }

}