package jp.co.soramitsu.runtime.multiNetwork.chain.model

import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.domain.AppVersion

typealias ChainId = String

const val polkadotChainId = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
const val kusamaChainId = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
const val westendChainId = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
const val moonriverChainId = "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b"
const val rococoChainId = "aaf2cd1b74b5f726895921259421b534124726263982522174147046b8827897"

const val kitsugiChainId = "9af9a64e6e4da8e3073901c3ff0cc4c3aad9563786d89daf6ad820b6e14a0b8b"
const val interlayChainId = "bf88efe70e9e0e916416e8bed61f2b45717f517d7f3523e33c7b001e5ffcbc72"

data class Chain(
    val id: ChainId,
    val name: String,
    val minSupportedVersion: String?,
    val assets: List<Asset>,
    val nodes: List<Node>,
    val explorers: List<Explorer>,
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

    val isSupported: Boolean
        get() = AppVersion.isSupported(minSupportedVersion)

    data class Types(
        val url: String,
        val overridesCommon: Boolean,
    )

    data class Asset(
        val id: String,
        val name: String,
        val iconUrl: String,
        val chainId: ChainId,
        val nativeChainId: ChainId?,
        val chainName: String?,
        val chainIcon: String?,
        val isTestNet: Boolean?,
        val priceId: String?,
        val precision: Int,
        val staking: StakingType,
        val priceProviders: List<String>?
    ) {

        enum class StakingType {
            UNSUPPORTED, RELAYCHAIN, PARACHAIN
        }

        val symbol: String
            get() = id.uppercase()

        val isNative: Boolean
            get() = nativeChainId == null || nativeChainId == chainId

        val chainToSymbol = chainId to symbol
    }

    data class Node(
        val url: String,
        val name: String,
        val isActive: Boolean,
        val isDefault: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Node

            if (url != other.url) return false
            if (name != other.name) return false
            if (isDefault != other.isDefault) return false

            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + name.hashCode()
            result = 31 * result + isDefault.hashCode()
            return result
        }
    }

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

    data class Explorer(val type: Type, val types: List<String>, val url: String) {
        enum class Type {
            POLKASCAN, SUBSCAN, UNKNOWN;

            val capitalizedName: String = name.lowercase().replaceFirstChar { it.titlecase() }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Chain

        if (id != other.id) return false
        if (name != other.name) return false
        if (minSupportedVersion != other.minSupportedVersion) return false
        if (assets != other.assets) return false
        if (explorers != other.explorers) return false
        if (externalApi != other.externalApi) return false
        if (icon != other.icon) return false
        if (addressPrefix != other.addressPrefix) return false
        if (types != other.types) return false
        if (isEthereumBased != other.isEthereumBased) return false
        if (isTestNet != other.isTestNet) return false
        if (hasCrowdloans != other.hasCrowdloans) return false
        if (parentId != other.parentId) return false
        if (assetsBySymbol != other.assetsBySymbol) return false
        if (assetsById != other.assetsById) return false

        // custom comparison logic
        val defaultNodes = nodes.filter { it.isDefault }
        val otherDefaultNodes = other.nodes.filter { it.isDefault }
        if (defaultNodes.size != otherDefaultNodes.size) return false

        val equalsWithoutActive = defaultNodes.map { it.name to it.url } == otherDefaultNodes.map { it.name to it.url }
        if (!equalsWithoutActive) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (minSupportedVersion?.hashCode() ?: 0)
        result = 31 * result + assets.hashCode()
        result = 31 * result + explorers.hashCode()
        result = 31 * result + nodes.map { it.name to it.url }.hashCode()
        result = 31 * result + (externalApi?.hashCode() ?: 0)
        result = 31 * result + icon.hashCode()
        result = 31 * result + addressPrefix
        result = 31 * result + (types?.hashCode() ?: 0)
        result = 31 * result + isEthereumBased.hashCode()
        result = 31 * result + isTestNet.hashCode()
        result = 31 * result + hasCrowdloans.hashCode()
        result = 31 * result + (parentId?.hashCode() ?: 0)
        result = 31 * result + assetsBySymbol.hashCode()
        result = 31 * result + assetsById.hashCode()
        return result
    }
}

fun Chain.updateNodesActive(localVersion: Chain): Chain = when (val activeNode = localVersion.nodes.firstOrNull { it.isActive }) {
    null -> this
    else -> copy(nodes = nodes.map { it.copy(isActive = it.url == activeNode.url && it.name == activeNode.name) })
}

fun List<Chain.Explorer>.getSupportedExplorers(type: BlockExplorerUrlBuilder.Type, value: String) = mapNotNull {
    BlockExplorerUrlBuilder(it.url, it.types).build(type, value)?.let { url ->
        it.type to url
    }
}.toMap()

fun ChainId.isPolkadotOrKusama() = this in listOf(polkadotChainId, kusamaChainId)

fun ChainId.isOrml() = this in listOf(kitsugiChainId, interlayChainId) // todo rework, probably asset's parameter

enum class TypesUsage {
    BASE, OWN, BOTH,
}
