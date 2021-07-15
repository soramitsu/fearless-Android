package jp.co.soramitsu.core_db.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigInteger

@Entity(tableName = "subqueryentity")
class SubqueryHistoryModel(
    val hash: String,
    val address: String,
    val operation: String?,
    val amount: BigInteger,
    val time: Long,
    val tokenType: TokenLocal.Type,
    val isIncome: Boolean,
    val displayAddress: String? = null, //Only to display. For instance for transfer we need to display another address
    val call: String? = null
) {
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

}
