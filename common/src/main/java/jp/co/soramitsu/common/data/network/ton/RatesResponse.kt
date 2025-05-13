package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

data class RatesResponse(
    val rates: Map<String, TokenRate>
)

data class TokenRate(
    val prices: Map<String, Double>,
    @SerializedName("diff_24h")
    val diff24h: Map<String, String>,
    @SerializedName("diff_7d")
    val diff7d: Map<String, String>,
    @SerializedName("diff_30d")
    val diff30d: Map<String, String>
)
