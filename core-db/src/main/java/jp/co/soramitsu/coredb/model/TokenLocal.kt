package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "tokens")
data class TokenLocal(
    @PrimaryKey
    val assetId: String,
    val fiatRate: BigDecimal?,
    val fiatSymbol: String?,
    val recentRateChange: BigDecimal?
) {
    companion object {
        fun createEmpty(assetId: String): TokenLocal = TokenLocal(assetId, null, null, null)
    }
}
