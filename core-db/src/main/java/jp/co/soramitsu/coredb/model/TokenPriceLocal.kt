package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "token_price")
data class TokenPriceLocal(
    @PrimaryKey
    val priceId: String,
    val fiatRate: BigDecimal?,
    val fiatSymbol: String?,
    val recentRateChange: BigDecimal?
) {
    companion object {
        fun createEmpty(priceId: String): TokenPriceLocal = TokenPriceLocal(priceId, null, null, null)
    }
}
