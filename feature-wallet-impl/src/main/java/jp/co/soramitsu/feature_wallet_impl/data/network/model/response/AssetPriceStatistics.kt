package jp.co.soramitsu.feature_wallet_impl.data.network.model.response

import java.math.BigDecimal

class AssetPriceStatistics(
    val height: Int,
    val price: BigDecimal,
    val records: List<Record>,
    val time: Int
) {
    fun calculateRateChange(previous: AssetPriceStatistics?): BigDecimal? {
        if (previous == null) return null

        val recentPrice = price
        val previousPrice = previous.price

        val increase = recentPrice - previousPrice

        return increase / previousPrice * BigDecimal("100")
    }
}

class Record(
    val height: Int,
    val price: BigDecimal,
    val time: Int
)