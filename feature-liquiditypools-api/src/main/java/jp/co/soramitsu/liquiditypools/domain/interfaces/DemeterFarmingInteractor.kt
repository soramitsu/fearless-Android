package jp.co.soramitsu.liquiditypools.domain.interfaces

import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import java.math.BigDecimal

interface DemeterFarmingInteractor {
    suspend fun mutationCapabilityReason(chainId: ChainId, action: DemeterMutationAction): String?
    suspend fun getFarmedPools(chainId: ChainId): List<DemeterFarmingPool>?
    suspend fun getAvailablePools(chainId: ChainId): List<DemeterFarmingBasicPool>
    suspend fun refresh(chainId: ChainId)
    suspend fun deposit(chainId: ChainId, pool: DemeterFarmingBasicPool, amount: BigDecimal): Result<String>
    suspend fun withdraw(chainId: ChainId, pool: DemeterFarmingPool, amount: BigDecimal): Result<String>
    suspend fun claimRewards(chainId: ChainId, pool: DemeterFarmingPool): Result<String>
}
