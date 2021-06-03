package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardLocalToStakingReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardRemoteToLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.sumRewards
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.repository.SubscanPagedSynchronizer
import jp.co.soramitsu.feature_staking_impl.data.repository.subscanCollectionFetcher
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StakingRewardsSubscanDataSourceImpl(
    private val stakingRewardDao: StakingRewardDao,
    private val subscanPagedSynchronizer: SubscanPagedSynchronizer,
    private val stakingApi: StakingApi,
) : StakingRewardsDataSource {

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingRewardDao.observeRewards(accountAddress)
            .mapList(::mapStakingRewardLocalToStakingReward)
            .map(::sumRewards)
    }

    override suspend fun sync(accountAddress: String) {
        withContext(Dispatchers.IO) {
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
    }
}
