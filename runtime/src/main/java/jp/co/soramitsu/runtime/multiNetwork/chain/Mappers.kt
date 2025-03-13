package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.runtime.mappers.mapRemoteToModel
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainAssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainExternalApiRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_BASED_OPTION = "ethereumBased"
private const val ETHEREUM_OPTION = "ethereum"
private const val CHAINLINK_PROVIDER_OPTION = "chainlinkProvider"
private const val CROWDLOAN_OPTION = "crowdloans"
private const val TESTNET_OPTION = "testnet"
private const val NOMINATION_POOL_OPTION = "poolStaking"
private const val NFT_OPTION = "nft"
private const val USES_APP_ID_OPTION = "checkAppId"
private const val REMOTE_ASSETS_OPTION = "remoteAssets"

private fun mapSectionTypeRemoteToSectionType(section: String) = when (section) {
    "subquery" -> Chain.ExternalApi.Section.Type.SUBQUERY
    "subsquid" -> Chain.ExternalApi.Section.Type.SUBSQUID
    "giantsquid" -> Chain.ExternalApi.Section.Type.GIANTSQUID
    "github" -> Chain.ExternalApi.Section.Type.GITHUB
    "soraSubquery", "sora" -> Chain.ExternalApi.Section.Type.SORA
    "etherscan" -> Chain.ExternalApi.Section.Type.ETHERSCAN
    "oklink" -> Chain.ExternalApi.Section.Type.OKLINK
    "blockscout" -> Chain.ExternalApi.Section.Type.BLOCKSCOUT
    "reef" -> Chain.ExternalApi.Section.Type.REEF
    "klaytn" -> Chain.ExternalApi.Section.Type.KLAYTN
    "fire" -> Chain.ExternalApi.Section.Type.FIRE
    "vicscan" -> Chain.ExternalApi.Section.Type.VICSCAN
    "zchain" -> Chain.ExternalApi.Section.Type.ZCHAINS
    "ton" -> Chain.ExternalApi.Section.Type.TON
    else -> Chain.ExternalApi.Section.Type.UNKNOWN
}

private fun mapExplorerTypeRemoteToExplorerType(explorer: String) = when (explorer) {
    "subscan" -> Chain.Explorer.Type.SUBSCAN
    "etherscan" -> Chain.Explorer.Type.ETHERSCAN
    "oklink" -> Chain.Explorer.Type.OKLINK
    "okx explorer" -> Chain.Explorer.Type.OKLINK
    "zeta" -> Chain.Explorer.Type.ZETA
    "reef" -> Chain.Explorer.Type.REEF
    "klaytn" -> Chain.Explorer.Type.KLAYTN
    "tonviewer" -> Chain.Explorer.Type.TONVIEWER
    else -> Chain.Explorer.Type.UNKNOWN
}

private fun mapSectionTypeToSectionTypeLocal(sectionType: Chain.ExternalApi.Section.Type): String = sectionType.name
private fun mapSectionTypeLocalToSectionType(sectionType: String): Chain.ExternalApi.Section.Type = runCatching {
    enumValueOf<Chain.ExternalApi.Section.Type>(sectionType)
}.getOrDefault(Chain.ExternalApi.Section.Type.UNKNOWN)

private fun mapExplorerTypeLocalToExplorerType(explorerType: String): Chain.Explorer.Type = runCatching {
    enumValueOf<Chain.Explorer.Type>(explorerType)
}.getOrDefault(Chain.Explorer.Type.UNKNOWN)

private fun mapStakingStringToStakingType(stakingString: String?): Asset.StakingType {
    return when (stakingString) {
        null -> Asset.StakingType.UNSUPPORTED
        "relaychain" -> Asset.StakingType.RELAYCHAIN
        "parachain" -> Asset.StakingType.PARACHAIN
        else -> Asset.StakingType.UNSUPPORTED
    }
}

private fun mapAssetTypeStringToAssetType(chainAsset: ChainAssetRemote): ChainAssetType {
    return if (chainAsset.ethereumType != null) {
        mapEthereumType(chainAsset.ethereumType)
    } else if (chainAsset.tonType != null) {
        mapTonType(chainAsset.tonType)
    } else if (chainAsset.type != null) {
        mapSubstrateType(chainAsset.type)
    } else ChainAssetType.Normal
}

private fun mapEthereumType(type: String): ChainAssetType {
    return when (type) {
        "erc20" -> ChainAssetType.ERC20
        "bep20" -> ChainAssetType.BEP20
        "normal" -> ChainAssetType.Normal
        else -> ChainAssetType.Unknown
    }
}

private fun mapTonType(type: String): ChainAssetType {
    return when (type) {
        "normal" -> ChainAssetType.Normal
        else -> ChainAssetType.Unknown
    }
}

private fun mapSubstrateType(type: String): ChainAssetType {
    return when (type) {
        "normal" -> ChainAssetType.Normal
        "ormlChain" -> ChainAssetType.OrmlChain
        "ormlAsset" -> ChainAssetType.OrmlAsset
        "foreignAsset" -> ChainAssetType.ForeignAsset
        "stableAssetPoolToken" -> ChainAssetType.StableAssetPoolToken
        "liquidCrowdloan" -> ChainAssetType.LiquidCrowdloan
        "vToken" -> ChainAssetType.VToken
        "vsToken" -> ChainAssetType.VSToken
        "stable" -> ChainAssetType.Stable
        "equilibrium" -> ChainAssetType.Equilibrium
        "soraAsset" -> ChainAssetType.SoraAsset
        "assets" -> ChainAssetType.Assets
        "assetId" -> ChainAssetType.AssetId
        "token2" -> ChainAssetType.Token2
        "xcm" -> ChainAssetType.Xcm
        "erc20" -> ChainAssetType.ERC20
        "bep20" -> ChainAssetType.BEP20
        else -> ChainAssetType.Unknown
    }
}

private fun mapStakingTypeToLocal(stakingType: Asset.StakingType): String = stakingType.name
private fun mapStakingTypeFromLocal(stakingTypeLocal: String): Asset.StakingType = enumValueOf(stakingTypeLocal)

private fun ChainExternalApiRemote.Explorer.toExplorer() = Chain.Explorer(
    type = mapExplorerTypeRemoteToExplorerType(type),
    types = types,
    url = url
)

private fun mapSectionRemoteToSection(sectionRemote: ChainExternalApiRemote.Section?) =
    sectionRemote?.let {
        Chain.ExternalApi.Section(
            type = mapSectionTypeRemoteToSectionType(sectionRemote.type),
            url = sectionRemote.url
        )
    }

private fun mapSectionLocalToSection(sectionLocal: ChainLocal.ExternalApi.Section?) =
    sectionLocal?.let {
        Chain.ExternalApi.Section(
            type = mapSectionTypeLocalToSectionType(sectionLocal.type),
            url = sectionLocal.url
        )
    }

private fun mapSectionToSectionLocal(sectionLocal: Chain.ExternalApi.Section?) = sectionLocal?.let {
    ChainLocal.ExternalApi.Section(
        type = mapSectionTypeToSectionTypeLocal(sectionLocal.type),
        url = sectionLocal.url
    )
}

private const val DEFAULT_PRECISION = 10

fun ChainRemote.toChain(): Chain {
    val nodes = this.nodes?.mapIndexed { index, node ->
        ChainNode(
            url = node.url,
            name = node.name,
            isActive = index == 0,
            isDefault = true
        )
    }

    val externalApi = this.externalApi?.let { externalApi ->
        (externalApi.history ?: externalApi.staking ?: externalApi.crowdloans)?.let {
            Chain.ExternalApi(
                history = mapSectionRemoteToSection(externalApi.history),
                staking = mapSectionRemoteToSection(externalApi.staking),
                crowdloans = mapSectionRemoteToSection(externalApi.crowdloans)
            )
        }
    }

    val assets = this.assetConfigs()

    val explorers = this.externalApi?.explorers?.map { it.toExplorer() }

    val optionsOrEmpty = this.options.orEmpty()

    return Chain(
        id = this.chainId,
        rank = this.rank,
        parentId = this.parentId,
        name = this.name,
        minSupportedVersion = this.minSupportedVersion,
        assets = assets.orEmpty(),
        nodes = nodes.orEmpty(),
        explorers = explorers.orEmpty(),
        icon = this.icon.orEmpty(),
        externalApi = externalApi,
        addressPrefix = this.addressPrefix,
        isEthereumBased = ETHEREUM_OPTION in optionsOrEmpty || ETHEREUM_BASED_OPTION in optionsOrEmpty,
        isTestNet = TESTNET_OPTION in optionsOrEmpty,
        hasCrowdloans = CROWDLOAN_OPTION in optionsOrEmpty,
        supportStakingPool = NOMINATION_POOL_OPTION in optionsOrEmpty,
        isEthereumChain = ETHEREUM_OPTION in optionsOrEmpty,
        chainlinkProvider = CHAINLINK_PROVIDER_OPTION in optionsOrEmpty,
        supportNft = NFT_OPTION in optionsOrEmpty,
        paraId = this.paraId,
        isUsesAppId = USES_APP_ID_OPTION in optionsOrEmpty,
        identityChain = identityChain,
        remoteAssetsSource = when {
            REMOTE_ASSETS_OPTION in optionsOrEmpty -> Chain.RemoteAssetsSource.OnChain
            else -> null
        },
        ecosystem = Ecosystem.fromString(ecosystem),
        tonBridgeUrl = tonBridgeUrl
    )
}

private fun ChainRemote.assetConfigs(): List<Asset>? {
    return assets?.mapNotNull { chainAsset ->
        runCatching {
            chainAsset.id?.let {
                Asset(
                    id = chainAsset.id,
                    name = chainAsset.name,
                    symbol = chainAsset.symbol.orEmpty(),
                    iconUrl = chainAsset.icon.orEmpty(),
                    chainId = this.chainId,
                    chainName = this.name,
                    chainIcon = this.icon,
                    isTestNet = TESTNET_OPTION in this.options.orEmpty(),
                    priceId = chainAsset.priceId,
                    precision = chainAsset.precision ?: DEFAULT_PRECISION,
                    staking = mapStakingStringToStakingType(chainAsset.staking),
                    purchaseProviders = chainAsset.purchaseProviders,
                    supportStakingPool = NOMINATION_POOL_OPTION in this.options.orEmpty(),
                    isUtility = chainAsset.isUtility ?: false,
                    type = mapAssetTypeStringToAssetType(chainAsset),
                    currencyId = chainAsset.currencyId,
                    existentialDeposit = chainAsset.existentialDeposit,
                    color = chainAsset.color,
                    isNative = chainAsset.isNative,
                    priceProvider = chainAsset.priceProvider?.mapRemoteToModel(),
                    coinbaseUrl = chainAsset.coinbaseUrl
                )
            }
        }.getOrNull()
    }
}

fun mapNodeLocalToNode(nodeLocal: ChainNodeLocal) = ChainNode(
    url = nodeLocal.url,
    name = nodeLocal.name,
    isActive = nodeLocal.isActive,
    isDefault = nodeLocal.isDefault
)

fun mapChainLocalToChain(chainLocal: JoinedChainInfo): Chain {
    val nodes = chainLocal.nodes.map(::mapNodeLocalToNode)

    val assets = chainLocal.assets.map {
        Asset(
            id = it.id,
            name = it.name,
            symbol = it.symbol,
            iconUrl = it.icon,
            chainId = it.chainId,
            priceId = it.priceId,
            precision = it.precision,
            staking = mapStakingTypeFromLocal(it.staking),
            purchaseProviders = mapToList(it.purchaseProviders),
            chainName = chainLocal.chain.name,
            chainIcon = chainLocal.chain.icon,
            isTestNet = chainLocal.chain.isTestNet,
            supportStakingPool = chainLocal.chain.supportStakingPool,
            isUtility = it.isUtility ?: false,
            type = it.type?.let { runCatching { ChainAssetType.valueOf(it) }.getOrNull() },
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit,
            color = it.color,
            isNative = it.isNative,
            priceProvider = mapToPriceProvider(it.priceProvider),
            coinbaseUrl = it.coinbaseUrl
        )
    }

    val externalApi = chainLocal.chain.externalApi?.let { externalApi ->
        Chain.ExternalApi(
            staking = mapSectionLocalToSection(externalApi.staking),
            history = mapSectionLocalToSection(externalApi.history),
            crowdloans = mapSectionLocalToSection(externalApi.crowdloans)
        )
    }

    val explorers = chainLocal.explorers.map {
        Chain.Explorer(
            type = mapExplorerTypeLocalToExplorerType(it.type),
            types = mapToList(it.types).orEmpty(),
            url = it.url
        )
    }

    return with(chainLocal.chain) {
        Chain(
            id = id,
            rank = rank,
            parentId = parentId,
            name = name,
            minSupportedVersion = minSupportedVersion,
            assets = assets,
            nodes = nodes,
            explorers = explorers,
            icon = icon,
            externalApi = externalApi,
            addressPrefix = prefix,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans,
            supportStakingPool = supportStakingPool,
            isEthereumChain = isEthereumChain,
            paraId = paraId,
            chainlinkProvider = isChainlinkProvider,
            supportNft = supportNft,
            isUsesAppId = isUsesAppId,
            identityChain = identityChain,
            remoteAssetsSource = remoteAssetsSource?.let { Chain.RemoteAssetsSource.valueOf(it) },
            ecosystem = Ecosystem.fromString(ecosystem),
            tonBridgeUrl = tonBridgeUrl
        )
    }
}

fun mapChainToChainLocal(chain: Chain): JoinedChainInfo {
    val gson by lazy { Gson() }

    val nodes = chain.nodes.map {
        ChainNodeLocal(
            url = it.url,
            name = it.name,
            chainId = chain.id,
            isActive = it.isActive,
            isDefault = it.isDefault
        )
    }

    val assets = chain.assets.map {
        ChainAssetLocal(
            id = it.id,
            name = it.name,
            symbol = it.symbol,
            icon = it.iconUrl,
            precision = it.precision,
            chainId = chain.id,
            priceId = it.priceId,
            staking = mapStakingTypeToLocal(it.staking),
            purchaseProviders = it.purchaseProviders?.let { purchaseProvider -> gson.toJson(purchaseProvider) },
            isUtility = it.isUtility,
            type = it.type?.name,
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit,
            color = it.color,
            isNative = it.isNative,
            priceProvider = it.priceProvider?.let { priceProvider ->  gson.toJson(priceProvider) },
            coinbaseUrl = it.coinbaseUrl
        )
    }

    val externalApi = chain.externalApi?.let { externalApi ->
        ChainLocal.ExternalApi(
            staking = mapSectionToSectionLocal(externalApi.staking),
            history = mapSectionToSectionLocal(externalApi.history),
            crowdloans = mapSectionToSectionLocal(externalApi.crowdloans)
        )
    }

    val explorers = chain.explorers.map { explorer ->
        ChainExplorerLocal(
            chainId = chain.id,
            type = explorer.type.name,
            types = Gson().toJson(explorer.types),
            url = explorer.url
        )
    }

    val chainLocal = with(chain) {
        ChainLocal(
            id = id,
            rank = rank,
            parentId = parentId,
            name = name,
            minSupportedVersion = minSupportedVersion,
            icon = icon,
            prefix = addressPrefix,
            externalApi = externalApi,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans,
            supportStakingPool = supportStakingPool,
            isEthereumChain = isEthereumChain,
            paraId = paraId,
            isChainlinkProvider = chainlinkProvider,
            supportNft = supportNft,
            isUsesAppId = isUsesAppId,
            identityChain = identityChain,
            remoteAssetsSource = remoteAssetsSource?.name,
            ecosystem = ecosystem.name,
            androidMinAppVersion = null,
            tonBridgeUrl = tonBridgeUrl
        )
    }

    return JoinedChainInfo(
        chain = chainLocal,
        nodes = nodes,
        assets = assets,
        explorers = explorers
    )
}

private fun mapToList(json: String?) =
    json?.let { Gson().fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }

fun mapToPriceProvider(json: String?) =
    json?.let { Gson().fromJson<Asset.PriceProvider>(it, object : TypeToken<Asset.PriceProvider>() {}.type) }