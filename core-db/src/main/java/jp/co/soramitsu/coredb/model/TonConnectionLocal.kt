package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import jp.co.soramitsu.common.data.network.ton.DappRemote

@Entity(tableName = "ton_connection", primaryKeys = ["clientId", "url"])
data class TonConnectionLocal(
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String
)
