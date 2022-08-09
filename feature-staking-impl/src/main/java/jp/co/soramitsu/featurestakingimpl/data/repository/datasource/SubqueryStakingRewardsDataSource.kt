package jp.co.soramitsu.featurestakingimpl.data.repository.datasource

import jp.co.soramitsu.coredb.dao.StakingTotalRewardDao
import jp.co.soramitsu.coredb.model.TotalRewardLocal
import jp.co.soramitsu.featurestakingimpl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.featurestakingimpl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.featurestakingimpl.data.network.subquery.StakingApi
import jp.co.soramitsu.featurestakingimpl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.featurestakingimpl.domain.model.TotalReward
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
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

        val totalReward = mapSubqueryHistoryToTotalReward(
            stakingApi.getSumReward(
                stakingUrl,
                StakingSumRewardRequest(accountAddress = accountAddress)
            )
        )

        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}
