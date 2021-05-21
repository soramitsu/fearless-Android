package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core_db.dao.StakingRewardDao
import jp.co.soramitsu.core_db.model.TotalRewardLocal
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingSubquerySumRewardResponseToAmount
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingSumRewardRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRewardsDataSourceImpl(
    private val stakingApi: StakingApi,
    private val stakingRewardDao: StakingRewardDao,
) : StakingRewardsDataSource {

    private val totalRewardsFlow = MutableSharedFlow<BigInteger>()

    private suspend fun saveTotalRewardsToStorage(accountAddress: String, totalRewards: BigInteger) = withContext(Dispatchers.IO) {
        stakingRewardDao.insert(TotalRewardLocal(accountAddress, totalRewards))
    }

    override suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalRewardLocal> {
        return stakingRewardDao.observeTotalRewards(accountAddress)
    }

    suspend fun sync(accountAddress: String) {
        val totalReward = mapStakingSubquerySumRewardResponseToAmount(stakingApi.getSumReward("sum-reward", StakingSumRewardRequest(accountAddress = accountAddress)))
        saveTotalRewardsToStorage(accountAddress, totalReward)
        totalRewardsFlow.emit(totalReward)
    }
}
