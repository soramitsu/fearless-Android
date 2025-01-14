package jp.co.soramitsu.coredb.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chain_nodes",
    primaryKeys = ["chainId", "url"],
    foreignKeys = [
        ForeignKey(
            entity = ChainLocal::class,
            parentColumns = ["id"],
            childColumns = ["chainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chainId"])
    ]
)
data class ChainNodeLocal(
    val chainId: String,
    val url: String,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ChainNodeLocal

        if (chainId != other.chainId) return false
        if (url != other.url) return false
        if (name != other.name) return false
        if (isDefault != other.isDefault) return false

        return true
    }

    override fun hashCode(): Int {
        var result = chainId.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + isDefault.hashCode()
        return result
    }
}
