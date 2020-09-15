package jp.co.soramitsu.feature_account_api.domain.model

data class Node(
    val name: String,
    val networkType: NetworkType,
    val link: String,
    val isDefault: Boolean
) {
    enum class NetworkType(val readableName: String) {
        KUSAMA("Kusama"),
        POLKADOT("Polkadot"),
        WESTEND("Westend")
    }
}