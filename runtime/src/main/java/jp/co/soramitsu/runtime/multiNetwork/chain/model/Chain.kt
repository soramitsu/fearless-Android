package jp.co.soramitsu.runtime.multiNetwork.chain.model

import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.domain.AppVersion
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.IChain
import jp.co.soramitsu.core.models.Asset as CoreAsset

typealias ChainId = String

const val polkadotChainId = "91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3"
const val kusamaChainId = "b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"
const val westendChainId = "e143f23803ac50e8f6f8e62695d1ce9e4e1d68aa36c1cd2cfd15340213f3423e"
const val moonriverChainId = "401a1f9dca3da46f5c4091016c8a2f26dcea05865116b286f60f668207d1474b"
const val rococoChainId = "aaf2cd1b74b5f726895921259421b534124726263982522174147046b8827897"
const val soraTestChainId = "3266816be9fa51b32cfea58d3e33ca77246bc9618595a4300e44c8856a8d8a17"
const val soraKusamaChainId = "6d8d9f145c2177fa83512492cdd80a71e29f22473f4a8943a6292149ac319fb9"
const val soraMainChainId = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
const val ternoaChainId = "6859c81ca95ef624c9dfe4dc6e3381c33e5d6509e35e147092bfbc780f777c4e"
const val pendulumChainId = "5d3c298622d5634ed019bf61ea4b71655030015bde9beb0d6a24743714462c86"

const val ethereumChainId = "1"
const val BSCChainId = "38"
const val BSCTestnetChainId = "97"
const val sepoliaChainId = "11155111"
const val goerliChainId = "5"
const val polygonChainId = "137"
const val polygonTestnetChainId = "80001"

const val genshiroChainId = "9b8cefc0eb5c568b527998bdd76c184e2b76ae561be76e4667072230217ea243"

data class Chain(
    override val id: ChainId,
    val name: String,
    val minSupportedVersion: String?,
    override val assets: List<CoreAsset>,
    override val nodes: List<ChainNode>,
    val explorers: List<Explorer>,
    val externalApi: ExternalApi?,
    val icon: String,
    override val addressPrefix: Int,
    override val isEthereumBased: Boolean,
    val isTestNet: Boolean,
    val hasCrowdloans: Boolean,
    override val parentId: String?,
    val supportStakingPool: Boolean,
    val isEthereumChain: Boolean
) : IChain {
    val assetsById = assets.associateBy(CoreAsset::id)

    val isSupported: Boolean
        get() = AppVersion.isSupported(minSupportedVersion)

    data class ExternalApi(
        val staking: Section?,
        val history: Section?,
        val crowdloans: Section?
    ) {
        data class Section(val type: Type, val url: String) {
            enum class Type {
                SUBQUERY, SORA, SUBSQUID, GIANTSQUID, ETHERSCAN, UNKNOWN, GITHUB;

                fun isHistory() = this in listOf(SUBQUERY, SORA, SUBSQUID, GIANTSQUID, ETHERSCAN)
            }
        }
    }

    data class Explorer(val type: Type, val types: List<String>, val url: String) {
        enum class Type {
            POLKASCAN, SUBSCAN, ETHERSCAN, UNKNOWN;

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
        if (isEthereumBased != other.isEthereumBased) return false
        if (isTestNet != other.isTestNet) return false
        if (hasCrowdloans != other.hasCrowdloans) return false
        if (parentId != other.parentId) return false

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
        result = 31 * result + isEthereumBased.hashCode()
        result = 31 * result + isTestNet.hashCode()
        result = 31 * result + hasCrowdloans.hashCode()
        result = 31 * result + (parentId?.hashCode() ?: 0)
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

@Deprecated("Use defaultChainSort() to get Polkadot at first place", ReplaceWith("defaultChainSort()"))
fun ChainId.isPolkadotOrKusama() = this in listOf(polkadotChainId, kusamaChainId)

fun ChainId.defaultChainSort() = when (this) {
    polkadotChainId -> 1
    kusamaChainId -> 2
    else -> 3
}

fun List<Chain>.getWithToken(symbol: String, filter: Map<ChainId, List<String>>? = null): List<Chain> = filter { chain ->
    chain.assets.any { asset ->
        val allowAsset = when (filter) {
            null -> true
            else -> filter[chain.id]?.contains(asset.id) ?: false
        }
        asset.symbol == symbol && allowAsset
    }
}
