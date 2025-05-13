package jp.co.soramitsu.wallet.impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

data class AtletaHistoryResponse(
    val items: List<AtletaHistoryItem>,
    @SerializedName("next_page_params")
    val nextPageParams: NextPageParams? = null
)

data class AtletaHistoryItem(
    val timestamp: String,
    val fee: AtletaFee? = null,
    val result: String,
    val to: AtletaAddress,
    val from: AtletaAddress,
    val value: BigInteger,
    val hash: String
)

data class AtletaAddress(
    val hash: String
)

data class AtletaFee(
    val type: String,
    val value: BigInteger
)