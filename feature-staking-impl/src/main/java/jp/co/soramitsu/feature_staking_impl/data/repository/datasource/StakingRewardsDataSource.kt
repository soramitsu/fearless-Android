package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.feature_staking_impl.domain.model.TotalReward
import kotlinx.coroutines.flow.Flow

interface StakingRewardsDataSource {
    suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward>

    suspend fun sync(accountAddress: String)
}
