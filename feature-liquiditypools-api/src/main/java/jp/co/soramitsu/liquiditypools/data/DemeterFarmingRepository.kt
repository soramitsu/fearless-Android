package jp.co.soramitsu.liquiditypools.data

import jp.co.soramitsu.liquiditypools.domain.DemeterFarmingPool

interface DemeterFarmingRepository {
    suspend fun getFarmedPools(soraAccountAddress: String): List<DemeterFarmingPool>?
}