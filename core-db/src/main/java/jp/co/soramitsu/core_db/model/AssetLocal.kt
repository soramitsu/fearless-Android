package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import java.math.BigInteger

@Entity(
    tableName = "assets",
    primaryKeys = ["tokenSymbol", "chainId", "accountId", "metaId"],
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
    val freeInPlanks: BigInteger? = null,
    val reservedInPlanks: BigInteger? = null,
    val miscFrozenInPlanks: BigInteger? = null,
    val feeFrozenInPlanks: BigInteger? = null,
    val bondedInPlanks: BigInteger? = null,
    val redeemableInPlanks: BigInteger? = null,
    val unbondingInPlanks: BigInteger? = null,
    val sortIndex: Int = Int.MAX_VALUE,
    val enabled: Boolean = true,
    val markedNotNeed: Boolean = false,
    val chainAccountName: String? = null
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
        )
    }
}

data class AssetUpdateItem(
    val metaId: Long,
    val chainId: String,
    val accountId: AccountId,
    val tokenSymbol: String,
    var sortIndex: Int,
    var enabled: Boolean
)
