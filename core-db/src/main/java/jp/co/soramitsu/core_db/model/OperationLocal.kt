package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import java.math.BigInteger

@Entity(
    tableName = "operations",
    primaryKeys = ["hash", "address"]
)
data class OperationLocal(
    val hash: String,
    val address: String,
    val time: Long,
    val tokenType: TokenLocal.Type,
    val status: Status,
    val source: Source,
    val operationType: OperationType, // Reward/Transfer/Extrinsic
    val type: String? = null,
    val call: String? = null,
    val amount: BigInteger? = null,
    val sender: String? = null,
    val receiver: String? = null,
    val fee: BigInteger? = null,
    val isReward: Boolean? = null,
    val era: Int? = null,
    val validator: String? = null,
    val success: Boolean? = null
) {
    enum class OperationType {
        EXTRINSIC, TRANSFER, REWARD
    }

    enum class Source {
        BLOCKCHAIN, SUBQUERY, APP
    }

    enum class Status {
        PENDING, COMPLETED, FAILED
    }
}
