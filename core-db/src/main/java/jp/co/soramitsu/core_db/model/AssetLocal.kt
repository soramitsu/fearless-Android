package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["tokenSymbol", "chainId", "accountId"],
    foreignKeys = [
        ForeignKey(
            entity = ChainLocal::class,
            parentColumns = ["id"],
            childColumns = ["chainId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AssetLocal(
    val tokenSymbol: String,
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
    val sortIndex: Int,
    val enabled: Boolean,
    val chainAccountName: String?
) {
    companion object {
        fun createEmpty(
            accountId: AccountId,
            symbol: String,
            chainId: String,
            metaId: Long
        ) = AssetLocal(
            tokenSymbol = symbol,
            chainId = chainId,
            accountId = accountId,
            metaId = metaId,
            freeInPlanks = BigInteger.ZERO,
            reservedInPlanks = BigInteger.ZERO,
            miscFrozenInPlanks = BigInteger.ZERO,
            feeFrozenInPlanks = BigInteger.ZERO,
            bondedInPlanks = BigInteger.ZERO,
            redeemableInPlanks = BigInteger.ZERO,
            unbondingInPlanks = BigInteger.ZERO,
            sortIndex = Int.MAX_VALUE,
            enabled = true,
            chainAccountName = null
        )
    }
}

data class AssetUpdateItem(
    val chainId: String,
    val accountId: AccountId,
    val tokenSymbol: String,
    var sortIndex: Int,
    var enabled: Boolean
)
