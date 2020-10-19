package jp.co.soramitsu.feature_account_api.domain.model

data class Network(
    val type: Node.NetworkType,
    val defaultNode: Node
) {
    val name = type.readableName
}