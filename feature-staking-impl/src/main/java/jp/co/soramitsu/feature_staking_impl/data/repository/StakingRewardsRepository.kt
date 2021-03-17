package jp.co.soramitsu.feature_staking_impl.data.repository

import jp.co.soramitsu.common.data.network.HttpExceptionHandler
import jp.co.soramitsu.common.data.network.subscan.subscanSubDomain
import jp.co.soramitsu.common.utils.mapList
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.core_db.model.StakingRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardLocalToStakingReward
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingRewardRemoteToLocal
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingRewardsApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingRewardRequest
import jp.co.soramitsu.feature_staking_impl.domain.model.StakingReward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class StakingRewardsRepository(
    private val stakingRewardsApi: StakingRewardsApi,
    private val stakingRewardDao: StakingRewardDao,
    private val httpExceptionHandler: HttpExceptionHandler
) {

    suspend fun syncTotalRewards(accountAddress: String) = withContext(Dispatchers.IO) {
        val networkType = accountAddress.networkType()
        val subDomain = networkType.subscanSubDomain()

        var currentPage = 0

        do {
            val request = StakingRewardRequest(currentPage, accountAddress)

            val rewardsRemote = httpExceptionHandler.wrap {
                stakingRewardsApi.getTransactionHistory(subDomain, request).content?.rewards
            }

            val rewardsLocal = rewardsRemote?.map { mapStakingRewardRemoteToLocal(it, accountAddress) }

            currentPage++
        } while (
            rewardsLocal != null &&
            stakingRewardDao.upsert(rewardsLocal) == StakingRewardDao.UpsertStatus.Ok &&
            mayHaveNextPage(rewardsLocal)
        )
    }

    fun stakingRewardsFlow(accountAddress: String): Flow<List<StakingReward>> {
        return stakingRewardDao.observeRewards(accountAddress)
            .mapList(::mapStakingRewardLocalToStakingReward)
    }

    private fun mayHaveNextPage(rewards: List<StakingRewardLocal>): Boolean {
        return rewards.size == StakingRewardRequest.ROW_MAX
    }
}
