package jp.co.soramitsu.feature_account_api.domain.model

data class User(
    val address: String,
    val username: String,
    val publicKey: String,
    val cryptoType: CryptoType,
    val networkType: Node.NetworkType
)