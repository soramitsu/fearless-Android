package jp.co.soramitsu.core.models

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum

typealias ChainId = String

const val SoraTestChainId = "3266816be9fa51b32cfea58d3e33ca77246bc9618595a4300e44c8856a8d8a17"
const val SoraMainChainId = "7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"

enum class CryptoType {
    SR25519,
    ED25519,
    ECDSA
}

enum class Ecosystem {
    Substrate,
    EthereumBased,
    Ethereum,
    Ton;

    companion object {
        fun fromString(value: String?): Ecosystem = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: Substrate
    }
}

enum class ChainAssetType {
    Normal,
    OrmlChain,
    OrmlAsset,
    ForeignAsset,
    StableAssetPoolToken,
    LiquidCrowdloan,
    VToken,
    VSToken,
    Stable,
    Equilibrium,
    SoraAsset,
    SoraUtilityAsset,
    Assets,
    AssetId,
    Token2,
    Xcm,
    ERC20,
    BEP20,
    Jetton,
    Unknown
}

data class ChainNode(
    val url: String,
    val name: String,
    val isActive: Boolean,
    val isDefault: Boolean
)

interface IChain {
    val id: ChainId
    val paraId: String?
    val assets: List<Asset>
    val nodes: List<ChainNode>
    val addressPrefix: Int
    val isEthereumBased: Boolean
    val parentId: String?
    val ecosystem: Ecosystem
}

data class ChainIdWithMetadata(
    val chainId: ChainId,
    val metadata: String?
)

data class Asset(
    val id: String,
    val name: String?,
    val symbol: String,
    val iconUrl: String,
    val chainId: ChainId,
    val chainName: String,
    val chainIcon: String?,
    val isTestNet: Boolean,
    val priceId: String?,
    val precision: Int,
    val staking: StakingType,
    val purchaseProviders: List<String>?,
    val supportStakingPool: Boolean,
    val isUtility: Boolean,
    val type: ChainAssetType?,
    val currencyId: String?,
    val existentialDeposit: String?,
    val color: String?,
    val isNative: Boolean?,
    val priceProvider: PriceProvider? = null,
    val coinbaseUrl: String? = null
) {
    val currency: Any?
        get() = currencyId?.let { parseCurrencyId(it) }

    val orderInStaking: Int
        get() = when {
            supportStakingPool -> 2
            staking == StakingType.RELAYCHAIN -> 0
            staking == StakingType.PARACHAIN -> 1
            else -> Int.MAX_VALUE
        }

    val typeExtra: ChainAssetType?
        get() = type

    enum class StakingType {
        RELAYCHAIN,
        PARACHAIN,
        UNSUPPORTED
    }

    data class PriceProvider(
        val id: String,
        val type: PriceProviderType,
        val precision: Int = 0
    )

    enum class PriceProviderType {
        Chainlink,
        Unknown
    }
}

private fun parseCurrencyId(currencyId: String): Any {
    return currencyId.toBigIntegerOrNull() ?: currencyId
}

sealed class MultiAddress(open val type: String, open val value: Any?) {
    data class Id(val accountId: ByteArray) : MultiAddress(TYPE_ID, accountId)
    data class Index(val index: Any?) : MultiAddress(TYPE_INDEX, index)
    data class Raw(val bytes: ByteArray) : MultiAddress(TYPE_RAW, bytes)
    data class Address32(val accountId: ByteArray) : MultiAddress(TYPE_ADDRESS32, accountId)
    data class Address20(val accountId: ByteArray) : MultiAddress(TYPE_ADDRESS20, accountId)

    companion object {
        const val TYPE_ID = "Id"
        const val TYPE_INDEX = "Index"
        const val TYPE_RAW = "Raw"
        const val TYPE_ADDRESS32 = "Address32"
        const val TYPE_ADDRESS20 = "Address20"
    }
}

fun bindMultiAddress(multiAddress: MultiAddress): DictEnum.Entry<Any?> {
    return when (multiAddress) {
        is MultiAddress.Id -> DictEnum.Entry(MultiAddress.TYPE_ID, multiAddress.accountId)
        is MultiAddress.Index -> DictEnum.Entry(MultiAddress.TYPE_INDEX, multiAddress.index)
        is MultiAddress.Raw -> DictEnum.Entry(MultiAddress.TYPE_RAW, multiAddress.bytes)
        is MultiAddress.Address32 -> DictEnum.Entry(MultiAddress.TYPE_ADDRESS32, multiAddress.accountId)
        is MultiAddress.Address20 -> DictEnum.Entry(MultiAddress.TYPE_ADDRESS20, multiAddress.accountId)
    }
}

fun ChainId.isSoraBasedChain(): Boolean = this == SoraMainChainId || this == SoraTestChainId

fun IChain.isSoraBasedChain(): Boolean = id.isSoraBasedChain()
