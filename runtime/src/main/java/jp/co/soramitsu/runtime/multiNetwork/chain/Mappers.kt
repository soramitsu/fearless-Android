package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_OPTION = "ethereumBased"
private const val TESTNET_OPTION = "testnet"

private fun mapSectionTypeRemoteToSectionType(section: String) = when (section) {
    "subquery" -> Chain.ExternalApi.Section.Type.SUBQUERY
    else -> Chain.ExternalApi.Section.Type.UNKNOWN
}

private fun mapSectionTypeToSectionTypeLocal(sectionType: Chain.ExternalApi.Section.Type): String = sectionType.name
private fun mapSectionTypeLocalToSectionType(sectionType: String): Chain.ExternalApi.Section.Type = enumValueOf(sectionType)

fun mapChainRemoteToChain(
    chainRemote: ChainRemote,
): Chain {
    val nodes = chainRemote.nodes.map {
        Chain.Node(
            url = it.url,
            name = it.name
        )
    }

    val assets = chainRemote.assets.map {
        Chain.Asset(
            chainId = chainRemote.chainId,
            id = it.assetId,
            symbol = it.symbol,
            precision = it.precision,
            name = it.name ?: chainRemote.name,
            priceId = it.priceId
        )
    }

    val types = chainRemote.types?.let {
        Chain.Types(
            url = it.url,
            overridesCommon = it.overridesCommon
        )
    }

    val externalApi = chainRemote.externalApi?.let { externalApi ->
        Chain.ExternalApi(
            history = externalApi.history?.let { section ->
                Chain.ExternalApi.Section(
                    type = mapSectionTypeRemoteToSectionType(section.type),
                    url = section.url
                )
            },
            staking = externalApi.staking?.let { section ->
                Chain.ExternalApi.Section(
                    type = mapSectionTypeRemoteToSectionType(section.type),
                    url = section.url
                )
            }
        )
    }

    return with(chainRemote) {
        val optionsOrEmpty = options.orEmpty()

        Chain(
            id = chainId,
            parentId = parentId,
            name = name,
            assets = assets,
            types = types,
            nodes = nodes,
            icon = icon,
            externalApi = externalApi,
            addressPrefix = addressPrefix,
            isEthereumBased = ETHEREUM_OPTION in optionsOrEmpty,
            isTestNet = TESTNET_OPTION in optionsOrEmpty
        )
    }
}

fun mapChainLocalToChain(chainLocal: JoinedChainInfo): Chain {
    val nodes = chainLocal.nodes.map {
        Chain.Node(
            url = it.url,
            name = it.name
        )
    }

    val assets = chainLocal.assets.map {
        Chain.Asset(
            id = it.id,
            symbol = it.symbol,
            precision = it.precision,
            name = it.name,
            chainId = it.chainId,
            priceId = it.priceId
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
            staking = externalApi.staking?.let { section ->
                Chain.ExternalApi.Section(
                    type = mapSectionTypeLocalToSectionType(section.type),
                    url = section.url
                )
            },
            history = externalApi.history?.let { section ->
                Chain.ExternalApi.Section(
                    type = mapSectionTypeLocalToSectionType(section.type),
                    url = section.url
                )
            }
        )
    }

    return with(chainLocal.chain) {
        Chain(
            id = id,
            parentId = parentId,
            name = name,
            assets = assets,
            types = types,
            nodes = nodes,
            icon = icon,
            externalApi = externalApi,
            addressPrefix = prefix,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet
        )
    }
}

fun mapChainToChainLocal(chain: Chain): JoinedChainInfo {
    val nodes = chain.nodes.map {
        ChainNodeLocal(
            url = it.url,
            name = it.name,
            chainId = chain.id
        )
    }

    val assets = chain.assets.map {
        ChainAssetLocal(
            id = it.id,
            symbol = it.symbol,
            precision = it.precision,
            chainId = chain.id,
            name = it.name,
            priceId = it.priceId
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
            staking = externalApi.staking?.let { section ->
                ChainLocal.ExternalApi.Section(
                    type = mapSectionTypeToSectionTypeLocal(section.type),
                    url = section.url
                )
            },
            history = externalApi.history?.let { section ->
                ChainLocal.ExternalApi.Section(
                    type = mapSectionTypeToSectionTypeLocal(section.type),
                    url = section.url
                )
            }
        )
    }

    val chainLocal = with(chain) {
        ChainLocal(
            id = id,
            parentId = parentId,
            name = name,
            types = types,
            icon = icon,
            prefix = addressPrefix,
            externalApi = externalApi,
            isEthereumBased = isEthereumBased,
            isTestNet = isTestNet
        )
    }

    return JoinedChainInfo(
        chain = chainLocal,
        nodes = nodes,
        assets = assets
    )
}
