package jp.co.soramitsu.runtime.multiNetwork.chain.model

data class Chain(
    val id: String,
    val name: String,
    val assets: List<Asset>,
    val nodes: List<Node>,
    val icon: String,
    val addressPrefix: Int,
    val types: Types?,
    val isEthereumBased: Boolean,
    val isTestNet: Boolean,
    val parentId: String?,
) {

    data class Types(
        val url: String,
        val overridesCommon: Boolean,
    )

    data class Asset(
        val id: Int,
        val symbol: String,
        val precision: Int,
        val name: String?
    )

    data class Node(
        val url: String,
        val name: String
    )
}
