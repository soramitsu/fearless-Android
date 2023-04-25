package jp.co.soramitsu.coredb.model.chain

import androidx.room.Entity

@Entity(
    tableName = "chain_types",
    primaryKeys = ["chainId"]
)
data class ChainTypesLocal(
    val chainId: String,
    val typesConfig: String
)
