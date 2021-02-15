package jp.co.soramitsu.core.model

data class Network(
    val type: Node.NetworkType,
    val defaultNode: Node
) {
    val name = type.readableName
}