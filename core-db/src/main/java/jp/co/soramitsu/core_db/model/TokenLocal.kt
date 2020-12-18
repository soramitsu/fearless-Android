package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

@Entity(tableName = "tokens")
data class TokenLocal(
    @PrimaryKey
    val type: Token.Type,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
) {
    companion object {
        fun createEmpty(type: Token.Type): TokenLocal = TokenLocal(type, null, null)
    }
}