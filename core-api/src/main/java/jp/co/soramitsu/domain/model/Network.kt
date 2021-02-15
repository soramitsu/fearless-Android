package jp.co.soramitsu.domain.model

data class Network(
    val type: Node.NetworkType,
    val defaultNode: Node
) {
    val name = type.readableName
}