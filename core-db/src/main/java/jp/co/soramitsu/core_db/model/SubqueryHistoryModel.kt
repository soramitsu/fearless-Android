package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity(
    tableName = "subqueryentity",
    primaryKeys = ["hash", "address"]
)
class SubqueryHistoryModel(
    val hash: String,
    val address: String,
    val time: Long,
    val tokenType: TokenLocal.Type,
    val type: String? = null, // maybe the same as module
    val call: String? = null,
    val amount: BigInteger? = null,
    val sender: String? = null,
    val receiver: String? = null,
    val fee: BigInteger? = null,
    val isReward: Boolean? = null,
    val era: Int? = null,
    val validator: String? = null,
    val success: Boolean? = null
)
