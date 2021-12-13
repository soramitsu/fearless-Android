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
    val hasCrowdloans: Boolean,
    val parentId: String?,
) {

    val assetsBySymbol = assets.associateBy(Asset::symbol)
    val assetsById = assets.associateBy(Asset::id)

    data class Types(
        val url: String,
        val overridesCommon: Boolean,
    )

    data class Asset(
        val id: String,
        val iconUrl: String,
        val priceId: String?,
        val chainId: ChainId,
        val symbol: String,
        val precision: Int,
        val staking: StakingType,
        val name: String,
        val priceProviders: List<String>?
    ) {

        enum class StakingType {
            UNSUPPORTED, RELAYCHAIN
        }

        val chainToSymbol = chainId to symbol
    }

    data class Node(
        val url: String,
        val name: String,
    )

    data class ExternalApi(
        val staking: Section?,
        val history: Section?,
        val crowdloans: Section?
    ) {
        data class Section(val type: Type, val url: String) {
            enum class Type {
                SUBQUERY, GITHUB, UNKNOWN
            }
        }
    }
}

enum class TypesUsage {
    BASE, OWN, BOTH,
}
