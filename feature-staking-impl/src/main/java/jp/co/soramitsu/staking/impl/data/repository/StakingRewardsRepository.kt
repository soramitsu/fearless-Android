package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.staking.impl.data.repository.datasource.StakingRewardsDataSource
import jp.co.soramitsu.staking.impl.domain.model.TotalReward
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

class StakingRewardsRepository(
    private val stakingRewardsDataSource: StakingRewardsDataSource
) {

    fun totalRewardFlow(accountAddress: String): Flow<TotalReward> {
        return stakingRewardsDataSource.totalRewardsFlow(accountAddress)
    }

    suspend fun sync(chainId: ChainId, accountAddress: String) {
        stakingRewardsDataSource.sync(chainId, accountAddress)
    }
}
