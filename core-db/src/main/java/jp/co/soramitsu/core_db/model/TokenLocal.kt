package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "tokens")
data class TokenLocal(
    @PrimaryKey
    val symbol: String,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?,
) {
    companion object {
        fun createEmpty(symbol: String): TokenLocal = TokenLocal(symbol, null, null)
    }

    enum class Type {
        KSM, DOT, WND, ROC
    }
}
