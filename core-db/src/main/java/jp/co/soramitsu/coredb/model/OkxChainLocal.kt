package jp.co.soramitsu.coredb.model

import androidx.room.Entity

@Entity(
    tableName = "okx_chains",
    primaryKeys = ["id", "dexTokenApproveAddress"],
)
data class OkxChainLocal(
    val id: String,
    val dexTokenApproveAddress: String
)

@Entity(
    tableName = "okx_tokens",
    primaryKeys = ["chainId", "tokenContractAddress"],
)
data class OkxTokenLocal(
    val chainId: String,
    val tokenContractAddress: String
)
