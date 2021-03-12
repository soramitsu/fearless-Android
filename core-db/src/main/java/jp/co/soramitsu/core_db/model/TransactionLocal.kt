package jp.co.soramitsu.core_db.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import jp.co.soramitsu.core.model.Node
import java.math.BigDecimal
import java.math.BigInteger

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountLocal::class,
            parentColumns = ["address"],
            childColumns = ["accountAddress"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["hash", "accountAddress"]
)
class TransactionLocal(
    @ColumnInfo(index = true) val accountAddress: String,
    val hash: String,
    val token: TokenLocal.Type,
    val senderAddress: String,
    val recipientAddress: String,
    val amount: BigDecimal,
    val date: Long,
    val feeInPlanks: BigInteger?,
    val status: Status,
    val source: Source,
    val networkType: Node.NetworkType
) {
    enum class Source {
        BLOCKCHAIN, SUBSCAN, APP
    }

    enum class Status {
        PENDING, COMPLETED, FAILED
    }
}
