package jp.co.soramitsu.staking.impl.data.repository.datasource

import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.staking.impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.staking.impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.staking.impl.data.network.subquery.StakingApi
import jp.co.soramitsu.staking.impl.data.network.subquery.request.StakingSumRewardRequest
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
        if (stakingUrl == null || chain.externalApi?.staking?.type != Chain.ExternalApi.Section.Type.SUBQUERY) {
            throw Exception("Pending rewards for this network is not supported yet")
        }

        val r = stakingApi.getSumReward(
            stakingUrl,
            StakingSumRewardRequest(accountAddress = accountAddress)
        )

        hashCode()
        val totalReward = mapSubqueryHistoryToTotalReward(
            r
        )
        hashCode()
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}
