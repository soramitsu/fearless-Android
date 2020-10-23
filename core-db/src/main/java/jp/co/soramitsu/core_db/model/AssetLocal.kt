package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["token", "accountAddress"],
    foreignKeys = [ForeignKey(entity = AccountLocal::class,
        parentColumns = ["address"],
        childColumns = ["accountAddress"],
        onDelete = ForeignKey.CASCADE)]
)
data class AssetLocal(
    val token: Asset.Token,
    @ColumnInfo(index = true) val accountAddress: String,
    val freeInPlanks: BigInteger,
    val reservedInPlanks: BigInteger,
    val miscFrozenInPlanks: BigInteger,
    val feeFrozenInPlanks: BigInteger,
    val bondedInPlanks: BigInteger,
    val redeemableInPlanks: BigInteger,
    val unbondingInPlanks: BigInteger,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
) {
    companion object {
        fun createEmpty(
            token: Asset.Token,
            accountAddress: String
        ) = AssetLocal(
            token = token,
            accountAddress = accountAddress,
            freeInPlanks = BigInteger.ZERO,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = BigInteger.ZERO,
            redeemableInPlanks = BigInteger.ZERO,
            unbondingInPlanks = BigInteger.ZERO,
            dollarRate = null,
            recentRateChange = null
        )
    }
}