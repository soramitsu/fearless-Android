package jp.co.soramitsu.feature_staking_impl.scenarios

import java.math.BigInteger
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.model.NetworkInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow

interface StakingScenarioInteractor {

    suspend fun observeNetworkInfoState(): Flow<NetworkInfo>

    suspend fun getStakingStateFlow(): Flow<StakingState>
    suspend fun getMinimumStake(chainId: ChainId): BigInteger
}
