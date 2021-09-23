package jp.co.soramitsu.runtime.multiNetwork.chain.model

typealias ChainId = String

data class Chain(
    val id: ChainId,
    val name: String,
    val assets: List<Asset>,
    val nodes: List<Node>,
    val externalApi: ExternalApi?,
    val icon: String,
    val addressPrefix: Int,
    val types: Types?,
    val isEthereumBased: Boolean,
    val isTestNet: Boolean,
    val parentId: String?,
) {

    val assetsBySymbol = assets.associateBy(Asset::symbol)
    val assetsById = assets.associateBy(Asset::id)

    data class Types(
        val url: String,
        val overridesCommon: Boolean,
    )

    data class Asset(
        val id: Int,
        val priceId: String?,
        val chainId: ChainId,
        val symbol: String,
        val precision: Int,
        val name: String?,
    )

    data class Node(
        val url: String,
        val name: String,
    )

    data class ExternalApi(
        val staking: Section?,
        val history: Section?
    ) {
        data class Section(val type: Type, val url: String) {
            enum class Type {
                SUBQUERY, UNKNOWN
            }
        }
    }
}

enum class TypesUsage {
    BASE, OWN, BOTH,
}
