package jp.co.soramitsu.coredb.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "favorite_chains",
    foreignKeys = [
        ForeignKey(
            parentColumns = ["id"],
            childColumns = ["chainId"],
            entity = ChainLocal::class,
            deferred = true
        ),
        ForeignKey(
            parentColumns = ["id"],
            childColumns = ["metaId"],
            entity = MetaAccountLocal::class,
            onDelete = ForeignKey.CASCADE
        )
    ],
    primaryKeys = ["metaId", "chainId"]
)
class FavoriteChainLocal(
    val metaId: Long,
    val chainId: String,
    val isFavorite: Boolean
)