package jp.co.soramitsu.core_db.dao

import jp.co.soramitsu.core_db.model.chain.ChainAssetLocal
import jp.co.soramitsu.core_db.model.chain.ChainLocal
import jp.co.soramitsu.core_db.model.chain.ChainNodeLocal
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo

fun createTestChain(
    id: String,
    name: String = id,
    nodesCount: Int = 3,
): JoinedChainInfo {
    val chain = chainOf(id, name)
    val nodes = with(chain) {
        (1..nodesCount).map {
            nodeOf("link${it}")
        }
    }
    val assets = with(chain) {
        listOf(
            assetOf(0, symbol = "A"),
            assetOf(1, symbol = "B")
        )
    }

    return JoinedChainInfo(chain, nodes, assets)
}

fun chainOf(
    id: String,
    name: String = id,
) = ChainLocal(
    id = id,
    parentId = null,
    name = name,
    icon = "Test",
    types = null,
    prefix = 0,
    isTestNet = false,
    isEthereumBased = false,
    externalApi = null
)

fun ChainLocal.nodeOf(
    link: String,
) = ChainNodeLocal(
    name = "Test",
    url = link,
    chainId = id
)

fun ChainLocal.assetOf(
    assetId: Int,
    symbol: String,
) = ChainAssetLocal(
    name = "Test",
    chainId = id,
    symbol = symbol,
    id = assetId,
    precision = 10,
    priceId = null
)

suspend fun ChainDao.addChain(joinedChainInfo: JoinedChainInfo) {
    update(removed = emptyList(), newOrUpdated = listOf(joinedChainInfo))
}
