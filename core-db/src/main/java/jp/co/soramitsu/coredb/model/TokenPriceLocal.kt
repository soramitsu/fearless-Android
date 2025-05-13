package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "token_price", primaryKeys = ["priceId"])
data class TokenPriceLocal(
    val priceId: String,
    val fiatSymbol: String,
    val fiatRate: BigDecimal?,
    val recentRateChange: BigDecimal?
)