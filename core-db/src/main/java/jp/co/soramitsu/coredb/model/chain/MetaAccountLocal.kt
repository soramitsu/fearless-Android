package jp.co.soramitsu.coredb.model.chain

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import jp.co.soramitsu.core.models.CryptoType

@Entity(
    tableName = MetaAccountLocal.TABLE_NAME,
    indices = [
        Index(value = ["substrateAccountId"]),
        Index(value = ["ethereumAddress"])
    ]
)
class MetaAccountLocal(
    val substratePublicKey: ByteArray,
    val substrateCryptoType: CryptoType,
    val substrateAccountId: ByteArray,
    val ethereumPublicKey: ByteArray?,
    val ethereumAddress: ByteArray?,
    val name: String,
    val isSelected: Boolean,
    val position: Int,
    val isBackedUp: Boolean,
    val googleBackupAddress: String?
) {

    companion object Table {
        const val TABLE_NAME = "meta_accounts"

        object Column {
            const val SUBSTRATE_PUBKEY = "substratePublicKey"
            const val SUBSTRATE_CRYPTO_TYPE = "substrateCryptoType"
            const val SUBSTRATE_ACCOUNT_ID = "substrateAccountId"

            const val ETHEREUM_PUBKEY = "ethereumPublicKey"
            const val ETHEREUM_ADDRESS = "ethereumAddress"

            const val NAME = "name"
            const val IS_SELECTED = "isSelected"
            const val POSITION = "position"
            const val ID = "id"
        }
    }

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

@Entity(
    tableName = "chain_accounts",
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
    indices = [
        Index(value = ["chainId"]),
        Index(value = ["metaId"]),
        Index(value = ["accountId"])
    ],
    primaryKeys = ["metaId", "chainId"]
)
class ChainAccountLocal(
    val metaId: Long,
    val chainId: String,
    val publicKey: ByteArray,
    val accountId: ByteArray,
    val cryptoType: CryptoType,
    val name: String
)

interface JoinedMetaAccountInfo {

    val metaAccount: MetaAccountLocal

    val chainAccounts: List<ChainAccountLocal>

    val favoriteChains: List<FavoriteChainLocal>
}

class RelationJoinedMetaAccountInfo(
    @Embedded
    override val metaAccount: MetaAccountLocal,

    @Relation(parentColumn = "id", entityColumn = "metaId", entity = ChainAccountLocal::class)
    override val chainAccounts: List<ChainAccountLocal>,

    @Relation(parentColumn = "id", entityColumn = "metaId", entity = FavoriteChainLocal::class)
    override val favoriteChains: List<FavoriteChainLocal>
) : JoinedMetaAccountInfo

class MetaAccountPositionUpdate(
    val id: Long,
    val position: Int
)
