package jp.co.soramitsu.coredb.model

import androidx.room.Entity

@Entity(tableName = "ton_connection", primaryKeys = ["metaId", "url"])
data class TonConnectionLocal(
    val metaId: Long,
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String
)

