package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import com.google.gson.Gson
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapStakingSubquerySumRewardResponseToAmount
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.StakingApi
import jp.co.soramitsu.feature_staking_impl.data.network.subscan.request.StakingSumRewardRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigInteger

class StakingRewardsDataSourceImpl(
    private val preferences: Preferences,
    private val jsonMapper: Gson,
    private val stakingApi: StakingApi,
) : StakingRewardsDataSource {
    companion object {
        private fun getPrefsTotalRewardsKey(accountAddress: String) = "total_rewards_$accountAddress"
    }

    private val totalRewardsFlow = createTotalRewardsFlow()

    private fun createTotalRewardsFlow(accountAddress: String): MutableSharedFlow<BigInteger> {
        val flow = MutableSharedFlow<BigInteger>()

        async {
            if (preferences.contains(getPrefsTotalRewardsKey(accountAddress))) {
                flow.emit(retrieveTotalRewardsFromStorage(accountAddress))
            }
        }

        return flow
    }

    private suspend fun retrieveTotalRewardsFromStorage(accountAddress: String): BigInteger = withContext(Dispatchers.Default) {
        val raw = preferences.getString(getPrefsTotalRewardsKey(accountAddress))
            ?: throw IllegalArgumentException("No total rewards")

        jsonMapper.fromJson(raw, BigInteger::class.java)
    }

    private suspend fun saveTotalRewardsToStorage(accountAddress: String, totalRewards: BigInteger) = withContext(Dispatchers.IO) {
        preferences.putString(accountAddress, totalRewards.toString())
    }

    override suspend fun totalRewardsFlow() = totalRewardsFlow

    private inline fun async(crossinline action: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.Default) {
            action()
        }
    }

    private fun sync(accountAddress: String) {
        async {
            val totalReward = mapStakingSubquerySumRewardResponseToAmount(stakingApi.getSumReward("sum-reward", StakingSumRewardRequest(accountAddress = accountAddress)))
            saveTotalRewardsToStorage(accountAddress, totalReward)
            totalRewardsFlow.emit(totalReward)
        }
    }
}
