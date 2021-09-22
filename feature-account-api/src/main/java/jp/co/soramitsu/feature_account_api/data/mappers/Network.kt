package jp.co.soramitsu.feature_account_api.data.mappers

import jp.co.soramitsu.core.model.Network
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

fun stubNetwork(chainId: ChainId): Network {
    val networkType = Node.NetworkType.findByGenesis(chainId) ?: Node.NetworkType.POLKADOT

    return Network(networkType)
}
