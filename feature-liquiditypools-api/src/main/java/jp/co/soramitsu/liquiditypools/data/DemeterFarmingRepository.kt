package jp.co.soramitsu.liquiditypools.data

import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingBasicPool
import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool
import jp.co.soramitsu.liquiditypools.domain.DemeterMutationAction
import java.math.BigDecimal

interface DemeterFarmingRepository {
    suspend fun mutationCapabilityReason(chainId: String, action: DemeterMutationAction): String?
    suspend fun getFarmedPools(chainId: String): List<DemeterFarmingPool>?
    suspend fun getAvailablePools(chainId: String): List<DemeterFarmingBasicPool>
    suspend fun refresh(chainId: String)
    suspend fun deposit(
        chainId: String,
        pool: DemeterFarmingBasicPool,
        amount: BigDecimal
    ): Result<String>
    suspend fun withdraw(
        chainId: String,
        pool: DemeterFarmingPool,
        amount: BigDecimal
    ): Result<String>
    suspend fun claimRewards(chainId: String, pool: DemeterFarmingPool): Result<String>
}
