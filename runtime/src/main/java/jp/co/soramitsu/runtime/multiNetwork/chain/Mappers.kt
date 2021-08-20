package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote

private const val ETHEREUM_OPTION = "ethereumBased"
private const val TESTNET_OPTION = "testnet"

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
            id = it.assetId,
            symbol = it.symbol,
            precision = it.precision,
            name = it.name
        )
    }

    val types = chainRemote.types?.let {
        Chain.Types(
            url = it.url,
            overridesCommon = it.overridesCommon
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
            name = it.name
        )
    }

    val types = chainLocal.chain.types?.let {
        Chain.Types(
            url = it.url,
            overridesCommon = it.overridesCommon
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
            name = it.name
        )
    }

    val types = chain.types?.let {
        ChainLocal.TypesConfig(
            url = it.url,
            overridesCommon = it.overridesCommon
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
