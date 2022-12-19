package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.flow.Flow

abstract class StakingScenarioRepository {
    abstract suspend fun stakingStateFlow(
        chain: Chain,
        chainAsset: Chain.Asset,
        accountId: AccountId
    ): Flow<StakingState>
}
