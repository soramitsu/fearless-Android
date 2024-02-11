package jp.co.soramitsu.coredb.model.chain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

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
data class ChainAssetLocal(
    val id: String,
    val name: String?,
    val symbol: String,
    val chainId: String,
    val icon: String,
    val priceId: String?,
    val staking: String,
    val precision: Int,
    val purchaseProviders: String?,
    val isUtility: Boolean?,
    val type: String?,
    val currencyId: String?,
    val existentialDeposit: String?,
    val color: String?,
    val isNative: Boolean?,
    val ethereumType: String?,
    val priceProvider: String?
)
