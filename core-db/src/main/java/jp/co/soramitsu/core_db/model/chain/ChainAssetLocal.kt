package jp.co.soramitsu.core_db.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.Locale

@Entity(
    tableName = "chain_assets",
    primaryKeys = ["chainId", "id"],
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
class ChainAssetLocal(
    val id: String,
    val chainId: String,
    val name: String,
    val icon: String,
    val priceId: String?,
    val staking: String,
    val precision: Int,
    val priceProviders: String?,
    val nativeChainId: String?
) {
    val symbol: String
        get() = id.toUpperCase(Locale.ROOT)
}
