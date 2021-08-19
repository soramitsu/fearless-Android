package jp.co.soramitsu.core_db.model.chain

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chains")
class ChainLocal(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val icon: String,
    @Embedded
    val types: TypesConfig?,
    val prefix: Int,
    val isEthereumBased: Boolean,
    val isTestNet: Boolean,
) {
    class TypesConfig(
        val url: String,
        val overridesCommon: Boolean
    )
}
