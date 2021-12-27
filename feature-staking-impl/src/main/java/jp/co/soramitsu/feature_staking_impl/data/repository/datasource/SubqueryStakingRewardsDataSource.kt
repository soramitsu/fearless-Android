package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
    private val chainRegistry: ChainRegistry,
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
            throw Exception("${chain.name} accounts don't temporary support fetching pending rewards")
        }

        val totalReward = mapSubqueryHistoryToTotalReward(
            stakingApi.getSumReward(
                stakingUrl,
                StakingSumRewardRequest(accountAddress = accountAddress)
            )
        )

        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}
