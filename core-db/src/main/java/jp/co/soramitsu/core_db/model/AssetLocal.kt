package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["token", "accountAddress"],
    foreignKeys = [
        ForeignKey(
            entity = TokenLocal::class,
            parentColumns = ["type"],
            childColumns = ["token"]
        )
    ]
)
data class AssetLocal(
    val token: TokenLocal.Type,
    @ColumnInfo(index = true) val accountAddress: String,
    val freeInPlanks: BigInteger,
    val reservedInPlanks: BigInteger,
    val miscFrozenInPlanks: BigInteger,
    val feeFrozenInPlanks: BigInteger,
    val bondedInPlanks: BigInteger,
    val redeemableInPlanks: BigInteger,
    val unbondingInPlanks: BigInteger
) {
    companion object {
        fun createEmpty(
            type: TokenLocal.Type,
            accountAddress: String
        ) = AssetLocal(
            token = type,
            accountAddress = accountAddress,
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
