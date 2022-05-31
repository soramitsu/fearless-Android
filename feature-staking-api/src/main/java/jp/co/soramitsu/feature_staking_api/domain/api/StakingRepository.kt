package jp.co.soramitsu.feature_staking_api.domain.api

import java.math.BigInteger
import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface StakingRepository {
    suspend fun getTotalIssuance(chainId: ChainId): BigInteger

    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>>
}
