package jp.co.soramitsu.feature_account_api.domain.model

data class Network(
    val networkType: Node.NetworkType,
    val defaultNode: Node
) {
    val name = networkType.readableName
}