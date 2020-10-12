package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal
import java.math.BigInteger

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = AccountLocal::class,
        parentColumns = ["address"],
        childColumns = ["accountAddress"],
        onDelete = ForeignKey.CASCADE)]
)
class TransactionLocal(
    @ColumnInfo(index = true) val accountAddress: String,
    @PrimaryKey val hash: String,
    val token: Asset.Token,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val feeInPlanks: BigInteger,
    val status: Transaction.Status,
    val isIncome: Boolean,
    val source: TransactionSource,
    val networkType: Node.NetworkType = token.networkType
)

enum class TransactionSource {
    BLOCKCHAIN, SUBSCAN, APP
}