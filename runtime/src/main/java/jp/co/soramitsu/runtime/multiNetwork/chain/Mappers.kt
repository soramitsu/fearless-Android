package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainExternalApiRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_OPTION = "ethereumBased"
private const val CROWDLOAN_OPTION = "crowdloans"
private const val TESTNET_OPTION = "testnet"
private const val NOMINATION_POOL_OPTION = "poolStaking"

private fun mapSectionTypeRemoteToSectionType(section: String) = when (section) {
    "subquery" -> Chain.ExternalApi.Section.Type.SUBQUERY
    "github" -> Chain.ExternalApi.Section.Type.GITHUB
    else -> Chain.ExternalApi.Section.Type.UNKNOWN
}

private fun mapExplorerTypeRemoteToExplorerType(explorer: String) = when (explorer) {
    "polkascan" -> Chain.Explorer.Type.POLKASCAN
    "subscan" -> Chain.Explorer.Type.SUBSCAN
    else -> Chain.Explorer.Type.UNKNOWN
}

private fun mapSectionTypeToSectionTypeLocal(sectionType: Chain.ExternalApi.Section.Type): String = sectionType.name
private fun mapSectionTypeLocalToSectionType(sectionType: String): Chain.ExternalApi.Section.Type = enumValueOf(sectionType)
private fun mapExplorerTypeLocalToExplorerType(explorerType: String): Chain.Explorer.Type = enumValueOf(explorerType)

private fun mapStakingStringToStakingType(stakingString: String?): Chain.Asset.StakingType {
    return when (stakingString) {
        null -> Chain.Asset.StakingType.UNSUPPORTED
        "relaychain" -> Chain.Asset.StakingType.RELAYCHAIN
        "parachain" -> Chain.Asset.StakingType.PARACHAIN
        else -> Chain.Asset.StakingType.UNSUPPORTED
    }
}

private fun mapStakingTypeToLocal(stakingType: Chain.Asset.StakingType): String = stakingType.name
private fun mapStakingTypeFromLocal(stakingTypeLocal: String): Chain.Asset.StakingType = enumValueOf(stakingTypeLocal)

private fun ChainExternalApiRemote.Explorer.toExplorer() = Chain.Explorer(
    type = mapExplorerTypeRemoteToExplorerType(type),
    types = types,
    url = url
)

private fun mapSectionRemoteToSection(sectionRemote: ChainExternalApiRemote.Section?) = sectionRemote?.let {
    Chain.ExternalApi.Section(
        type = mapSectionTypeRemoteToSectionType(sectionRemote.type),
        url = sectionRemote.url
    )
}

private fun mapSectionLocalToSection(sectionLocal: ChainLocal.ExternalApi.Section?) = sectionLocal?.let {
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

fun mapChainRemoteToChain(
    chainsRemote: List<ChainRemote>,
    assetsRemote: List<AssetRemote>
): List<Chain> {
    val assetsById = assetsRemote.filter { it.id != null }.associateBy { it.id }
    return chainsRemote.map { chainRemote ->
        val nodes = chainRemote.nodes?.mapIndexed { index, node ->
            Chain.Node(
                url = node.url,
                name = node.name,
                isActive = index == 0,
                isDefault = true
            )
        }

        val assets = chainRemote.assets?.mapNotNull { chainAsset ->
            chainAsset.assetId?.let {
                val assetRemote = assetsById[chainAsset.assetId]
                val assetNativeChain = chainsRemote.firstOrNull { it.chainId == assetRemote?.chainId }
                Chain.Asset(
                    id = chainAsset.assetId,
                    symbol = assetRemote?.symbol.orEmpty(),
                    displayName = assetRemote?.displayName,
                    name = assetNativeChain?.name.orEmpty(),
                    iconUrl = assetRemote?.icon ?: assetNativeChain?.icon.orEmpty(),
                    chainId = chainRemote.chainId,
                    chainName = chainRemote.name,
                    chainIcon = chainRemote.icon,
                    nativeChainId = assetNativeChain?.chainId,
                    isTestNet = TESTNET_OPTION in chainRemote.options.orEmpty(),
                    priceId = assetRemote?.priceId,
                    precision = assetRemote?.precision ?: DEFAULT_PRECISION,
                    staking = mapStakingStringToStakingType(chainAsset.staking),
                    priceProviders = chainAsset.purchaseProviders,
                    supportStakingPool = NOMINATION_POOL_OPTION in chainRemote.options.orEmpty(),
                    isUtility = chainAsset.isUtility,
                    type = ChainAssetType.from(chainAsset.type),
                    currencyId = assetRemote?.currencyId,
                    existentialDeposit = assetRemote?.existentialDeposit
                )
            }
        }

        val types = chainRemote.types?.let {
            Chain.Types(
                url = it.androidUrl,
                overridesCommon = it.overridesCommon
            )
        }

        val externalApi = chainRemote.externalApi?.let { externalApi ->
            (externalApi.history ?: externalApi.staking ?: externalApi.crowdloans)?.let {
                Chain.ExternalApi(
                    history = mapSectionRemoteToSection(externalApi.history),
                    staking = mapSectionRemoteToSection(externalApi.staking),
                    crowdloans = mapSectionRemoteToSection(externalApi.crowdloans)
                )
            }
        }

        val explorers = chainRemote.externalApi?.explorers?.map { it.toExplorer() }

        val optionsOrEmpty = chainRemote.options.orEmpty()

        Chain(
            id = chainRemote.chainId,
            parentId = chainRemote.parentId,
            name = chainRemote.name,
            minSupportedVersion = chainRemote.minSupportedVersion,
            assets = assets.orEmpty(),
            types = types,
            nodes = nodes.orEmpty(),
            explorers = explorers.orEmpty(),
            icon = chainRemote.icon.orEmpty(),
            externalApi = externalApi,
            addressPrefix = chainRemote.addressPrefix,
            isEthereumBased = ETHEREUM_OPTION in optionsOrEmpty,
            isTestNet = TESTNET_OPTION in optionsOrEmpty,
            hasCrowdloans = CROWDLOAN_OPTION in optionsOrEmpty,
            supportStakingPool = NOMINATION_POOL_OPTION in optionsOrEmpty
        )
    }
}

fun mapNodeLocalToNode(nodeLocal: ChainNodeLocal) = Chain.Node(
    url = nodeLocal.url,
    name = nodeLocal.name,
    isActive = nodeLocal.isActive,
    isDefault = nodeLocal.isDefault
)

fun mapChainLocalToChain(chainLocal: JoinedChainInfo): Chain {
    val nodes = chainLocal.nodes.map(::mapNodeLocalToNode)

    val assets = chainLocal.assets.map {
        Chain.Asset(
            id = it.id,
            symbol = it.symbol,
            displayName = it.displayName,
            name = it.name,
            iconUrl = it.icon,
            chainId = it.chainId,
            nativeChainId = it.nativeChainId,
            priceId = it.priceId,
            precision = it.precision,
            staking = mapStakingTypeFromLocal(it.staking),
            priceProviders = mapToList(it.priceProviders),
            chainName = chainLocal.chain.name,
            chainIcon = chainLocal.chain.icon,
            isTestNet = chainLocal.chain.isTestNet,
            supportStakingPool = chainLocal.chain.supportStakingPool,
            isUtility = it.isUtility ?: false,
            type = it.type?.let { ChainAssetType.valueOf(it) },
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit
        )
    }

    val types = chainLocal.chain.types?.let {
        Chain.Types(
            url = it.url,
            overridesCommon = it.overridesCommon
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
            parentId = parentId,
            name = name,
            minSupportedVersion = minSupportedVersion,
            assets = assets,
            types = types,
            nodes = nodes,
            explorers = explorers,
            icon = icon,
            externalApi = externalApi,
            addressPrefix = prefix,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans,
            supportStakingPool = supportStakingPool
        )
    }
}

fun mapChainToChainLocal(chain: Chain): JoinedChainInfo {
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
            symbol = it.symbol,
            displayName = it.displayName,
            icon = it.iconUrl,
            precision = it.precision,
            chainId = chain.id,
            name = it.name,
            priceId = it.priceId,
            staking = mapStakingTypeToLocal(it.staking),
            priceProviders = it.priceProviders?.let { Gson().toJson(it) },
            nativeChainId = it.nativeChainId,
            isUtility = it.isUtility,
            type = it.type?.name,
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit
        )
    }

    val types = chain.types?.let {
        ChainLocal.TypesConfig(
            url = it.url,
            overridesCommon = it.overridesCommon
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
            parentId = parentId,
            name = name,
            minSupportedVersion = minSupportedVersion,
            types = types,
            icon = icon,
            prefix = addressPrefix,
            externalApi = externalApi,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet,
            hasCrowdloans = hasCrowdloans,
            supportStakingPool = supportStakingPool
        )
    }

    return JoinedChainInfo(
        chain = chainLocal,
        nodes = nodes,
        assets = assets,
        explorers = explorers
    )
}

private fun mapToList(json: String?) = json?.let { Gson().fromJson<List<String>>(it, object : TypeToken<List<String>>() {}.type) }
