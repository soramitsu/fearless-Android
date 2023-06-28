package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.xcm.domain.XcmEntitiesFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChainInteractor(
    private val chainDao: ChainDao,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher
) {
    fun getChainsFlow() = chainDao.joinChainInfoFlow().mapList { mapChainLocalToChain(it) }.map {
        it.sortedWith(chainDefaultSort())
    }

    suspend fun getRawChainAssets(): List<Asset> {
        val localAssets = withContext(Dispatchers.IO) { chainDao.getAssetsConfigs() }
        val mapped = withContext(Dispatchers.Default) {
            localAssets.map {
                Asset(
                    id = it.id,
                    name = it.name,
                    symbol = it.symbol,
                    iconUrl = it.icon,
                    chainId = it.chainId,
                    priceId = it.priceId,
                    precision = it.precision,
                    staking = Asset.StakingType.UNSUPPORTED,
                    priceProviders = emptyList(),
                    chainName = "",
                    chainIcon = it.icon,
                    isTestNet = false,
                    supportStakingPool = false,
                    isUtility = it.isUtility ?: false,
                    type = it.type?.let { ChainAssetType.valueOf(it) },
                    currencyId = it.currencyId,
                    existentialDeposit = it.existentialDeposit,
                    color = it.color,
                    isNative = it.isNative
                )
            }.sortedWith(compareBy<Asset> { it.isTestNet }.thenByDescending { polkadotChainId in listOf(it.chainId) })
        }
        return mapped
    }

    fun getXcmChainIdsFlow(
        type: XcmChainType,
        originChainId: String? = null,
        assetSymbol: String? = null
    ): Flow<List<ChainId>> {
        return flow {
            val chainIds = when (type) {
                XcmChainType.Origin -> {
                    xcmEntitiesFetcher.getAvailableOriginChains(
                        assetSymbol = null,
                        destinationChainId = null
                    )
                }

                XcmChainType.Destination -> {
                    xcmEntitiesFetcher.getAvailableDestinationChains(
                        assetSymbol = assetSymbol,
                        originChainId = originChainId
                    )
                }
            }
            emit(chainIds)
        }
    }

    private fun chainDefaultSort() = compareBy<Chain> { it.isTestNet }
        .thenByDescending { it.parentId == null }
        .thenByDescending { polkadotChainId in listOf(it.id, it.parentId) }
}
