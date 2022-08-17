package jp.co.soramitsu.featurestakingimpl.data.repository

import jp.co.soramitsu.featurestakingimpl.data.repository.datasource.StakingRewardsDataSource
import jp.co.soramitsu.featurestakingimpl.domain.model.TotalReward
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

class StakingRewardsRepository(
    private val stakingRewardsDataSource: StakingRewardsDataSource
) {

    suspend fun totalRewardFlow(accountAddress: String): Flow<TotalReward> {
        return stakingRewardsDataSource.totalRewardsFlow(accountAddress)
    }

    suspend fun sync(chainId: ChainId, accountAddress: String) {
        stakingRewardsDataSource.sync(chainId, accountAddress)
    }
}
