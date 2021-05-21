package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import jp.co.soramitsu.core_db.model.TotalRewardLocal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.math.BigInteger

interface StakingRewardsDataSource {
    suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalRewardLocal>
}
