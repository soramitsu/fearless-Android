package jp.co.soramitsu.liquiditypools.impl.domain

import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId

class DemeterFarmingInteractorImpl(
    private val demeterFarmingRepository: DemeterFarmingRepository,
) : DemeterFarmingInteractor {

    override suspend fun getFarmedPools(chainId: ChainId): List<DemeterFarmingPool>? {
        return demeterFarmingRepository.getFarmedPools(chainId)
    }
}
