package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

class AssetPriceStatistics(
    val code: Int,
    @SerializedName("data")
    val content: Data?,
    @SerializedName("generated_at")
    val generatedAt: Long,
    val message: String
) {
    fun calculateRateChange(previous: AssetPriceStatistics): BigDecimal? {
        val recentPrice = content?.price ?: return null
        val previousPrice = previous.content?.price ?: return null

        val increase = recentPrice - previousPrice

        return increase / previousPrice * BigDecimal("100")
    }
}

class Data(
    val height: Int,
    val price: BigDecimal,
    val records: List<Record>,
    val time: Int
)

class Record(
    val height: Int,
    val price: BigDecimal,
    val time: Int
)