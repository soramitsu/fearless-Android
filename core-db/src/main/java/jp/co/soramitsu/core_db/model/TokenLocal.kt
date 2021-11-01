package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "tokens")
data class TokenLocal(
    @PrimaryKey
    val type: Type,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
) {
    companion object {
        fun createEmpty(type: Type): TokenLocal = TokenLocal(type, null, null)
    }

    enum class Type {
        KSM, DOT, WND, ROC
    }
}
