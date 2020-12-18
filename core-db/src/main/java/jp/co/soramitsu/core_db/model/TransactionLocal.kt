package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.feature_account_api.domain.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import java.math.BigDecimal
import java.math.BigInteger

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = AccountLocal::class,
        parentColumns = ["address"],
        childColumns = ["accountAddress"],
        onDelete = ForeignKey.CASCADE)],
    primaryKeys = ["hash", "accountAddress"]
)
class TransactionLocal(
    @ColumnInfo(index = true) val accountAddress: String,
    val hash: String,
    val token: Token.Type,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val feeInPlanks: BigInteger?,
    val status: Transaction.Status,
    val source: TransactionSource,
    val networkType: Node.NetworkType = token.networkType
)

enum class TransactionSource {
    BLOCKCHAIN, SUBSCAN, APP
}