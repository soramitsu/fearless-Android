package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import java.math.BigDecimal

@Entity(tableName = "token_price", primaryKeys = ["priceId"])
data class TokenPriceLocal(
    val priceId: String,
    val fiatSymbol: String,
    val fiatRate: BigDecimal?,
    val recentRateChange: BigDecimal?,
    /** NULL means the persisted quote has no verified currency identity. */
    val fiatCurrencyId: String? = null
)
