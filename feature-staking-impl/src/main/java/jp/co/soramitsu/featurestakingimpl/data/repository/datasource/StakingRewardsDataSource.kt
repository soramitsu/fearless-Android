package jp.co.soramitsu.featurestakingimpl.data.repository.datasource

import jp.co.soramitsu.featurestakingimpl.domain.model.TotalReward
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface StakingRewardsDataSource {
    suspend fun totalRewardsFlow(accountAddress: String): Flow<TotalReward>

    suspend fun sync(chainId: ChainId, accountAddress: String)
}
