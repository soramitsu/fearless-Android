package jp.co.soramitsu.coredb.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger

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
    val enabled: Boolean? = null,
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

    private val locked: BigInteger
        get() = maxOf(miscFrozenInPlanks.orZero(), feeFrozenInPlanks.orZero())

    val transferableInPlanks: BigInteger
        get() = maxOf(freeInPlanks.orZero() - locked, BigInteger.ZERO)
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
