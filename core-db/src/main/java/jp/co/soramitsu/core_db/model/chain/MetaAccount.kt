package jp.co.soramitsu.core_db.model.chain

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import jp.co.soramitsu.core.model.CryptoType

@Entity(tableName = "meta_accounts")
class MetaAccountLocal(
    val substratePublicKey: ByteArray,
    val substrateCryptoType: CryptoType,
    val ethereumPublicKey: ByteArray?,
    val name: String,
    val isSelected: Boolean,
) {

    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}

@Entity(
    tableName = "chain_accounts",
    foreignKeys = [
        ForeignKey(
            parentColumns = ["id"],
            childColumns = ["chainId"],
            entity = ChainLocal::class
        ),
        ForeignKey(
            parentColumns = ["id"],
            childColumns = ["metaId"],
            entity = MetaAccountLocal::class,
            onDelete = ForeignKey.CASCADE
        ),
    ],
    indices = [
        Index(value = ["metaId", "chainId"], unique = true)
    ],
    primaryKeys = ["metaId", "chainId"]
)
class ChainAccountLocal(
    val metaId: Long,
    val chainId: String,
    val publicKey: ByteArray,
    val cryptoType: CryptoType,
)

class JoinedMetaAccountInfo(
    @Embedded
    val metaAccount: MetaAccountLocal,

    @Relation(parentColumn = "id", entityColumn = "metaId", entity = ChainAccountLocal::class)
    val chainAccounts: List<ChainAccountLocal>,
)
