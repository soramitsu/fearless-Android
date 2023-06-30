package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainNode
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainExternalApiRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_OPTION = "ethereumBased"
private const val CROWDLOAN_OPTION = "crowdloans"
private const val TESTNET_OPTION = "testnet"
private const val NOMINATION_POOL_OPTION = "poolStaking"

private fun mapSectionTypeRemoteToSectionType(section: String) = when (section) {
    "subquery" -> Chain.ExternalApi.Section.Type.SUBQUERY
    "subsquid" -> Chain.ExternalApi.Section.Type.SUBSQUID
    "giantsquid" -> Chain.ExternalApi.Section.Type.GIANTSQUID
    "github" -> Chain.ExternalApi.Section.Type.GITHUB
    "sora" -> Chain.ExternalApi.Section.Type.SORA
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

private fun mapStakingStringToStakingType(stakingString: String?): Asset.StakingType {
    return when (stakingString) {
        null -> Asset.StakingType.UNSUPPORTED
        "relaychain" -> Asset.StakingType.RELAYCHAIN
        "parachain" -> Asset.StakingType.PARACHAIN
        else -> Asset.StakingType.UNSUPPORTED
    }
}

private fun mapStakingTypeToLocal(stakingType: Asset.StakingType): String = stakingType.name
private fun mapStakingTypeFromLocal(stakingTypeLocal: String): Asset.StakingType = enumValueOf(stakingTypeLocal)

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
        parentId = this.parentId,
        name = this.name,
        minSupportedVersion = this.minSupportedVersion,
        assets = assets.orEmpty(),
        nodes = nodes.orEmpty(),
        explorers = explorers.orEmpty(),
        icon = this.icon.orEmpty(),
        externalApi = externalApi,
        addressPrefix = this.addressPrefix,
        isEthereumBased = ETHEREUM_OPTION in optionsOrEmpty,
        isTestNet = TESTNET_OPTION in optionsOrEmpty,
        hasCrowdloans = CROWDLOAN_OPTION in optionsOrEmpty,
        supportStakingPool = NOMINATION_POOL_OPTION in optionsOrEmpty
    )
}

private fun ChainRemote.assetConfigs(): List<Asset>? {
    return assets?.mapNotNull { chainAsset ->
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
                priceProviders = chainAsset.purchaseProviders,
                supportStakingPool = NOMINATION_POOL_OPTION in this.options.orEmpty(),
                isUtility = chainAsset.isUtility ?: false,
                type = ChainAssetType.from(chainAsset.type),
                currencyId = chainAsset.currencyId,
                existentialDeposit = chainAsset.existentialDeposit,
                color = chainAsset.color,
                isNative = chainAsset.isNative
            )
        }
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
            priceProviders = mapToList(it.priceProviders),
            chainName = chainLocal.chain.name,
            chainIcon = chainLocal.chain.icon,
            isTestNet = chainLocal.chain.isTestNet,
            supportStakingPool = chainLocal.chain.supportStakingPool,
            isUtility = it.isUtility ?: false,
            type = it.type?.let { ChainAssetType.valueOf(it) },
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit,
            color = it.color,
            isNative = it.isNative
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
            name = it.name,
            symbol = it.symbol,
            icon = it.iconUrl,
            precision = it.precision,
            chainId = chain.id,
            priceId = it.priceId,
            staking = mapStakingTypeToLocal(it.staking),
            priceProviders = it.priceProviders?.let { Gson().toJson(it) },
            isUtility = it.isUtility,
            type = it.type?.name,
            currencyId = it.currencyId,
            existentialDeposit = it.existentialDeposit,
            color = it.color,
            isNative = it.isNative
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
