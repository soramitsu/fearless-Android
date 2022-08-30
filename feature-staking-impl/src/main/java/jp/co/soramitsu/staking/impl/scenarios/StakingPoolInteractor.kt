package jp.co.soramitsu.staking.impl.scenarios

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.api.domain.model.NominationPool
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.model.PoolMember
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolApi
import jp.co.soramitsu.staking.impl.data.repository.StakingPoolDataSource
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class StakingPoolInteractor(
    private val api: StakingPoolApi,
    private val dataSource: StakingPoolDataSource,
    private val stakingInteractor: StakingInteractor,
    private val accountRepository: AccountRepository
) {

    fun stakingStateFlow(): Flow<StakingState> {
        return stakingInteractor.selectedChainFlow().filter { it.supportStakingPool }.flatMapConcat { chain ->
            val accountId = accountRepository.getSelectedMetaAccount().accountId(chain) ?: error("cannot find accountId")
            stakingPoolStateFlow(chain, accountId)
        }
    }

    private fun stakingPoolStateFlow(chain: Chain, accountId: AccountId): Flow<StakingState> {
        return observeCurrentPool(chain.id, accountId).map {
            it ?: return@map StakingState.Pool.None(chain, accountId)

            when (it) {
                null -> StakingState.Pool.None(chain, accountId)
                else -> StakingState.Pool.Member(chain, accountId, it)
            }
        }.runCatching { this }.getOrDefault(emptyFlow())
    }

    fun observeCurrentPool(
        chainId: ChainId,
        accountId: AccountId
    ): Flow<NominationPool?> {
        return dataSource.observePoolMembers(chainId, accountId).flatMapConcat { poolMember ->
            poolMember ?: return@flatMapConcat flowOf(null)
            dataSource.observePool(chainId, poolMember.poolId).map { bondedPool ->
                bondedPool ?: return@map null
                val name = dataSource.getPoolMetadata(chainId, poolMember.poolId)
                val unbondingEras = poolMember.unbondingEras.map { jp.co.soramitsu.staking.api.domain.model.PoolUnbonding(it.era, it.amount) }
                NominationPool(
                    poolMember.poolId,
                    name,
                    poolMember.points,
                    poolMember.lastRecordedRewardCounter,
                    bondedPool.state,
                    BigInteger.ZERO,
                    unbondingEras,
                    bondedPool.memberCounter,
                    bondedPool.depositor,
                    bondedPool.root,
                    bondedPool.nominator,
                    bondedPool.stateToggler
                )
            }
        }
    }

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

    suspend fun estimateJoinFee(amount: BigInteger): BigInteger {
        return api.estimateJoinFee(amount, BigInteger.ZERO)
    }
}
