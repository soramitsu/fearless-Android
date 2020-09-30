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
class AssetLocal(
    val token: Asset.Token,
    @ColumnInfo(index = true) val accountAddress: String,
    val freeInPlanks: BigInteger,
    val reservedInPlanks: BigInteger,
    val miscFrozenInPlanks: BigInteger,
    val feeFrozenInPlanks: BigInteger,
    val dollarRate: BigDecimal?,
    val recentRateChange: BigDecimal?
)