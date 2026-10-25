package jp.co.soramitsu.liquiditypools.impl.domain

import jp.co.soramitsu.liquiditypools.data.DemeterFarmingRepository
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.interfaces.DemeterFarmingInteractor
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigDecimal

class DemeterFarmingInteractorImpl(
    private val demeterFarmingRepository: DemeterFarmingRepository,
) : DemeterFarmingInteractor {

    override suspend fun mutationCapabilityReason(chainId: ChainId, action: DemeterMutationAction): String? =
        demeterFarmingRepository.mutationCapabilityReason(chainId, action)

    override suspend fun getFarmedPools(chainId: ChainId): List<DemeterFarmingPool>? {
        return demeterFarmingRepository.getFarmedPools(chainId)
    }

    override suspend fun getAvailablePools(chainId: ChainId) = demeterFarmingRepository.getAvailablePools(chainId)

    override suspend fun refresh(chainId: ChainId) = demeterFarmingRepository.refresh(chainId)

    override suspend fun deposit(
        chainId: ChainId,
        pool: DemeterFarmingBasicPool,
        amount: BigDecimal
    ) = demeterFarmingRepository.deposit(chainId, pool, amount)

    override suspend fun withdraw(
        chainId: ChainId,
        pool: DemeterFarmingPool,
        amount: BigDecimal
    ) = demeterFarmingRepository.withdraw(chainId, pool, amount)

    override suspend fun claimRewards(chainId: ChainId, pool: DemeterFarmingPool) =
        demeterFarmingRepository.claimRewards(chainId, pool)
}
