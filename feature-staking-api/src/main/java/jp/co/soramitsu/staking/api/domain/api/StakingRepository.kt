package jp.co.soramitsu.staking.api.domain.api

import java.math.BigInteger
import jp.co.soramitsu.common.data.network.runtime.binding.AccountInfo
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface StakingRepository {
    suspend fun getTotalIssuance(chainId: ChainId): BigInteger

    suspend fun getAccountInfo(chainId: ChainId, accountId: AccountId): AccountInfo

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>>
}
