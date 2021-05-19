package jp.co.soramitsu.feature_staking_impl.data.repository.datasource

import kotlinx.coroutines.flow.MutableSharedFlow
import java.math.BigInteger

interface StakingRewardsDataSource {
    suspend fun totalRewardsFlow() : MutableSharedFlow<BigInteger>
}
