package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.xcm_impl.domain.XcmEntitiesFetcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ChainInteractor(
    private val chainDao: ChainDao,
    private val xcmEntitiesFetcher: XcmEntitiesFetcher
) {
    fun getChainsFlow() = chainDao.joinChainInfoFlow().mapList { mapChainLocalToChain(it) }.map {
        it.sortedWith(chainDefaultSort())
    }

    fun getXcmChainsFlow(
        type: XcmChainType,
        originalChainId: String? = null,
        assetSymbol: String? = null
    ): Flow<List<Chain>> {
        return flow {
            val chains = when (type) {
                XcmChainType.Original -> {
                    xcmEntitiesFetcher.getAvailableOriginalChains(
                        assetSymbol = null,
                        destinationChainId = null
                    )
                }
                XcmChainType.Destination -> {
                    xcmEntitiesFetcher.getAvailableDestinationChains(
                        assetSymbol = assetSymbol,
                        originalChainId = originalChainId
                    )
                }
            }.map { it as Chain }
            emit(chains)
        }
    }

    private fun chainDefaultSort() = compareBy<Chain> { it.isTestNet }
        .thenByDescending { it.parentId == null }
        .thenByDescending { polkadotChainId in listOf(it.id, it.parentId) }
}
