package jp.co.soramitsu.feature_account_api.domain.model

class Network(
    val name: String,
    val networkType: Node.NetworkType,
    val defaultNode: Node
)