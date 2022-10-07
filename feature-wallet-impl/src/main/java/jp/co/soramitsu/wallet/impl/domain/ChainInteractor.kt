package jp.co.soramitsu.wallet.impl.domain

import jp.co.soramitsu.coredb.dao.ChainDao

class ChainInteractor(
    private val chainDao: ChainDao
) {
    fun getChainsFlow() = chainDao.joinChainInfoFlow()
}
