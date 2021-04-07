package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardLocalToStakingReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardRemoteToLocal
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.domain.model.StakingReward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StakingRewardsRepository(
    private val stakingApi: StakingApi,
    private val stakingRewardDao: StakingRewardDao,
    private val subscanPagedSynchronizer: SubscanPagedSynchronizer,
) {

    suspend fun syncTotalRewards(accountAddress: String) = withContext(Dispatchers.IO) {
        val networkType = accountAddress.networkType()
        val subDomain = networkType.subscanSubDomain()

        val rewardsInDatabase = stakingRewardDao.rewardCount(accountAddress)

        subscanPagedSynchronizer.sync(
            alreadySavedItems = rewardsInDatabase,
            pageFetcher = subscanCollectionFetcher { page, row ->
                val request = StakingRewardRequest(page, accountAddress, row)

                stakingApi.getRewardsHistory(subDomain, request)
            },
            pageCacher = { rewardsRemote ->
                val rewardsLocal = rewardsRemote.map { mapStakingRewardRemoteToLocal(it, accountAddress) }

                stakingRewardDao.insert(rewardsLocal)
            }
        )
    }

    fun stakingRewardsFlow(accountAddress: String): Flow<List<StakingReward>> {
        return stakingRewardDao.observeRewards(accountAddress)
            .mapList(::mapStakingRewardLocalToStakingReward)
    }
}
