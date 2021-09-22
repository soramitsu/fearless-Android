package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["symbol", "chainId", "accountId"],
)
data class AssetLocal(
    val symbol: String,
    val chainId: String,
    val accountId: AccountId,
    @ColumnInfo(index = true) val metaId: Long,
    val freeInPlanks: BigInteger,
    val reservedInPlanks: BigInteger,
    val miscFrozenInPlanks: BigInteger,
    val feeFrozenInPlanks: BigInteger,
    val bondedInPlanks: BigInteger,
    val redeemableInPlanks: BigInteger,
    val unbondingInPlanks: BigInteger,
) {
    companion object {
        fun createEmpty(
            accountId: AccountId,
            symbol: String,
            chainId: String,
            metaId: Long
        ) = AssetLocal(
            symbol = symbol,
            chainId = chainId,
            accountId = accountId,
            metaId = metaId,
            freeInPlanks = BigInteger.ZERO,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = BigInteger.ZERO,
            redeemableInPlanks = BigInteger.ZERO,
            unbondingInPlanks = BigInteger.ZERO
        )
    }
}
