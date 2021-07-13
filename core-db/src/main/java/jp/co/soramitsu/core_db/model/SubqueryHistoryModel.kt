package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity(tableName = "subqueryentity")
class SubqueryHistoryModel(
    val hash: String,
    val address: String,
    val operation: String,
    val amount: BigInteger,
    val time: Long,
    val tokenType: Int
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

}
