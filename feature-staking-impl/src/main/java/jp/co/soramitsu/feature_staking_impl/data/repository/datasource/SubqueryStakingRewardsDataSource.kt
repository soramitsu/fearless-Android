package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core_db.dao.StakingTotalRewardDao
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingSubquerySumRewardResponseToAmount
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingTotalRewardLocalToTotalReward
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingSumRewardRequest
import jp.co.soramitsu.feature_staking_impl.data.repository.getSubqueryTotalRewardsPath
import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigInteger

class SubqueryStakingRewardsDataSource(
    private val stakingApi: StakingApi,
    private val stakingTotalRewardDao: StakingTotalRewardDao,
) : StakingRewardsDataSource {

    private suspend fun saveTotalRewardsToStorage(accountAddress: String, totalRewards: BigInteger) = withContext(Dispatchers.IO) {
        stakingTotalRewardDao.insert(TotalRewardLocal(accountAddress, totalRewards))
    }

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward> {
        return stakingTotalRewardDao.observeTotalRewards(accountAddress)
            .filterNotNull()
            .map(::mapStakingTotalRewardLocalToTotalReward)
    }

    override suspend fun sync(accountAddress: String) {
        val subqueryPath = accountAddress.networkType().getSubqueryTotalRewardsPath() // We will be here only from KUSAMA or POLKADOT networks "when" branch

        val totalReward = mapStakingSubquerySumRewardResponseToAmount(
            stakingApi.getSumReward(
                subqueryPath,
                StakingSumRewardRequest(accountAddress = accountAddress)
            )
        ) ?: BigInteger.ZERO
        saveTotalRewardsToStorage(accountAddress, totalReward)
    }
}
