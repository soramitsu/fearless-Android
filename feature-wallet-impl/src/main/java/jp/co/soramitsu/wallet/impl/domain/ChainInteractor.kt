package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import kotlinx.coroutines.flow.map

class ChainInteractor(
    private val chainDao: ChainDao
) {
    fun getChainsFlow() = chainDao.joinChainInfoFlow().mapList { mapChainLocalToChain(it) }.map {
        it.sortedWith(chainDefaultSort())
    }

    private fun chainDefaultSort() = compareBy<Chain> { it.isTestNet }
        .thenByDescending { it.parentId == null }
        .thenByDescending { polkadotChainId in listOf(it.id, it.parentId) }
}
