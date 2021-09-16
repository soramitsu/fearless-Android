package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapSubqueryHistoryToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapTotalRewardLocalToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subquery.request.StakingSumRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.repository.subqueryFearlessApiPath
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
) : StakingRewardsDataSource {

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingTotalRewardDao.observeTotalRewards(accountAddress)
            .filterNotNull()
            .map(::mapTotalRewardLocalToTotalReward)
    }

    override suspend fun sync(accountAddress: String) {
        val subqueryPath = accountAddress.networkType().subqueryFearlessApiPath()

        val totalReward = mapSubqueryHistoryToTotalReward(
            stakingApi.getSumReward(
                subqueryPath,
                StakingSumRewardRequest(accountAddress = accountAddress)
            )
        )

        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalReward))
    }
}
