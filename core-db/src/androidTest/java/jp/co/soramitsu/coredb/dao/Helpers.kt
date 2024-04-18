package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo

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
            assetOf("0", symbol = "A"),
            assetOf("1", symbol = "B")
        )
    }

    return JoinedChainInfo(chain, nodes, assets, emptyList())
}

fun chainOf(
    id: String,
    name: String = id,
) = ChainLocal(
    id = id,
    parentId = null,
    name = name,
    icon = "Test",
    prefix = 0,
    isTestNet = false,
    isEthereumBased = false,
    externalApi = null,
    hasCrowdloans = false,
    minSupportedVersion = "2.0.3",
    supportStakingPool = false,
    isEthereumChain = false,
    paraId = null,
    rank = null,
    isChainlinkProvider = false,
    supportNft = false
)

fun ChainLocal.nodeOf(
    link: String,
) = ChainNodeLocal(
    name = "Test",
    url = link,
    chainId = id,
    isActive = false,
    isDefault = true
)

fun ChainLocal.assetOf(
    assetId: String,
    symbol: String,
) = ChainAssetLocal(
    name = "Test",
    chainId = id,
    id = assetId,
    precision = 10,
    priceId = null,
    staking = "test",
    icon = "",
    purchaseProviders = null,
    symbol = symbol,
    isUtility = null,
    type = null,
    currencyId = null,
    existentialDeposit = null,
    color = null,
    isNative = null,
    ethereumType = null,
    priceProvider = null
)

suspend fun ChainDao.addChain(joinedChainInfo: JoinedChainInfo) {
    update(removed = emptyList(), newOrUpdated = listOf(joinedChainInfo))
}
