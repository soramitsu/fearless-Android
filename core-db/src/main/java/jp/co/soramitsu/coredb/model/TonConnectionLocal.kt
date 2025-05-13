package jp.co.soramitsu.coredb.model

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "ton_connection",
    primaryKeys = ["metaId", "url", "source"],
    foreignKeys = [
        ForeignKey(
            entity = MetaAccountLocal::class,
            parentColumns = ["id"],
            childColumns = ["metaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TonConnectionLocal(
    val metaId: Long,
    val clientId: String,
    val name: String,
    val icon: String,
    val url: String,
    val source: ConnectionSource
)

enum class ConnectionSource {
    QR, WEB
}

