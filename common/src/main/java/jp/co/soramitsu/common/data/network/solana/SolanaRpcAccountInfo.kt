package jp.co.soramitsu.common.data.network.solana

/** RPC account owner and at most 165 bytes of immutable base64 data for send preflight. */
data class SolanaRpcAccountInfo(
    val ownerProgram: String,
    val lamports: Long,
    val executable: Boolean,
    val dataBase64: String
)
