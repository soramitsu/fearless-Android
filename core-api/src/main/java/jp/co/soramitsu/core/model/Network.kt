package jp.co.soramitsu.core.model

data class Network(
    val type: Node.NetworkType
) {
    val name = type.readableName
}
