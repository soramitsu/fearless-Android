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
            UNSUPPORTED, RELAYCHAIN
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
