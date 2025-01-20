package jp.co.soramitsu.coredb.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.shared_utils.runtime.AccountId
import java.math.BigInteger
import jp.co.soramitsu.common.utils.positiveOrNull

/*** This table is used for storing balances in database.
 *  freeInPlanks - has three states:
 *  null - loading is in progress
 *  -1 - error
 *  0 or positive number - free amount
 */
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
    val chainAccountName: String? = null,
    val status: String? = null
) {
    companion object {
        fun createEmpty(
            accountId: AccountId,
            id: String,
            chainId: String,
            metaId: Long,
            tokenPriceId: String?,
            enabled: Boolean? = null
        ) = AssetLocal(
            id = id,
            chainId = chainId,
            accountId = accountId,
            metaId = metaId,
            tokenPriceId = tokenPriceId,
            enabled = enabled
        )
    }

    val totalInPlanks: BigInteger
        get() = freeInPlanks.positiveOrNull().orZero() + reservedInPlanks.orZero()

    private val locked: BigInteger
        get() = maxOf(miscFrozenInPlanks.orZero(), feeFrozenInPlanks.orZero())

    val transferableInPlanks: BigInteger
        get() = maxOf(freeInPlanks.positiveOrNull().orZero() - locked, BigInteger.ZERO)
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

data class AssetBalanceUpdateItem(
    val metaId: Long,
    val chainId: String,
    val accountId: AccountId,
    val id: String,

    val freeInPlanks: BigInteger? = null,
    val reservedInPlanks: BigInteger? = null,
    val miscFrozenInPlanks: BigInteger? = null,
    val feeFrozenInPlanks: BigInteger? = null,
    val bondedInPlanks: BigInteger? = null,
    val redeemableInPlanks: BigInteger? = null,
    val unbondingInPlanks: BigInteger? = null,

    val status: String? = null
)