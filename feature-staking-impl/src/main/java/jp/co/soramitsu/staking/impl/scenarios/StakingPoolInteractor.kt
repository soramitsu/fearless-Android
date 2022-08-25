package jp.co.soramitsu.staking.impl.scenarios

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource

class StakingPoolInteractor(private val api: StakingPoolApi, private val dataSource: StakingPoolDataSource) {
    suspend fun getMinToJoinPool(chainId: ChainId): BigInteger {
        return dataSource.minJoinBond(chainId)
    }

    suspend fun getMinToCreate(chainId: ChainId): BigInteger {
        return dataSource.minCreateBond(chainId)
    }

    suspend fun getExistingPools(chainId: ChainId): BigInteger {
        return dataSource.existingPools(chainId)
    }

    suspend fun getPossiblePools(chainId: ChainId): BigInteger {
        return dataSource.maxPools(chainId) ?: BigInteger.ZERO
    }

    suspend fun getMaxMembersInPool(chainId: ChainId): BigInteger {
        return dataSource.maxMembersInPool(chainId) ?: BigInteger.ZERO
    }

    suspend fun getMaxPoolsMembers(chainId: ChainId): BigInteger {
        return dataSource.maxPoolMembers(chainId) ?: BigInteger.ZERO
    }

    suspend fun getPoolMembers(chainId: ChainId, accountId: AccountId): PoolMember? {
        return dataSource.poolMembers(chainId, accountId)
    }
}
