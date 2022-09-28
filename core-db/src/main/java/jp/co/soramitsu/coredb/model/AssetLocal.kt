package jp.co.soramitsu.coredb.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import java.math.BigInteger
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId

@Entity(
    tableName = "assets",
    primaryKeys = ["id", "chainId", "accountId", "metaId"],
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
    val id: String,
    @ColumnInfo(index = true) val chainId: String,
    val accountId: AccountId,
    @ColumnInfo(index = true) val metaId: Long,
    val tokenPriceId: String?,
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
            assetId: String,
            chainId: String,
            metaId: Long,
            priceId: String?
        ) = AssetLocal(
            id = assetId,
            chainId = chainId,
            accountId = accountId,
            metaId = metaId,
            tokenPriceId = priceId
        )
    }

    val totalInPlanks: BigInteger
        get() = freeInPlanks.orZero() + reservedInPlanks.orZero()
}

data class AssetUpdateItem(
    val metaId: Long,
    val chainId: String,
    val accountId: AccountId,
    val id: String,
    var sortIndex: Int,
    var enabled: Boolean,
    val tokenPriceId: String?
)
