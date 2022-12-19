package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.mapChainLocalToChain

class ChainInteractor(
    private val chainDao: ChainDao
) {
    fun getChainsFlow() = chainDao.joinChainInfoFlow().mapList { mapChainLocalToChain(it) }
}
