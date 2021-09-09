package jp.co.soramitsu.common.data.network.coingecko

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class PriceInfo(
    @SerializedName("usd")
    val price: BigDecimal,
    @SerializedName("usd_24h_change")
    val rateChange: BigDecimal
)
