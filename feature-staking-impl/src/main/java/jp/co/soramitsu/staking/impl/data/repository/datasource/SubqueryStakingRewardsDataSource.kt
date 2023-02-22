package jp.co.soramitsu.staking.impl.data.repository.datasource

import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.staking.impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.staking.impl.data.network.subquery.request.SubsquidRewardAmountRequest
import jp.co.soramitsu.staking.impl.domain.model.TotalReward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
    private val chainRegistry: ChainRegistry
) : StakingRewardsDataSource {

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingTotalRewardDao.observeTotalRewards(accountAddress)
            .filterNotNull()
            .map(::mapTotalRewardLocalToTotalReward)
    }

    override suspend fun sync(chainId: ChainId, accountAddress: String) {
        val chain = chainRegistry.getChain(chainId)
        val stakingUrl = chain.externalApi?.staking?.url
        val stakingType = chain.externalApi?.staking?.type

        return when {
            stakingUrl == null -> throw Exception("Pending rewards for this network is not supported yet")
            stakingType == Chain.ExternalApi.Section.Type.SUBQUERY -> {
                syncSubquery(stakingUrl, accountAddress)
            }
            stakingType == Chain.ExternalApi.Section.Type.SUBSQUID -> {
                syncSubsquid(stakingUrl, accountAddress)
            }
            else -> throw Exception("Pending rewards for this network is not supported yet")
        }
    }

    private suspend fun syncSubsquid(stakingUrl: String, accountAddress: String) {
        val rewards = stakingApi.getRewardAmounts(stakingUrl, SubsquidRewardAmountRequest(accountAddress))
        val totalReward = rewards.data.rewards.sumByBigInteger { it.amount.orZero() }
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }

    private suspend fun syncSubquery(stakingUrl: String, accountAddress: String) {
        val r = stakingApi.getSumReward(
            stakingUrl,
            StakingSumRewardRequest(accountAddress = accountAddress)
        )

        val totalReward = mapSubqueryHistoryToTotalReward(
            r
        )
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}
