package jp.co.soramitsu.liquiditypools.domain.interfaces

import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

interface DemeterFarmingInteractor {
    suspend fun getFarmedPools(chainId: ChainId): List<DemeterFarmingPool>?
}
