package jp.co.soramitsu.feature_staking_impl.domain.staking.rebond

import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_api.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class RebondInteractor(
    private val extrinsicService: ExtrinsicService,
    private val sharedStakingSate: StakingSharedState,
    private val stakingScenarioInteractor: StakingScenarioInteractor
) {

    suspend fun estimateFee(amount: BigInteger, collatorAddress: String?): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = sharedStakingSate.chain()

            extrinsicService.estimateFee(chain) {
                stakingScenarioInteractor.rebond(this, amount, collatorAddress)
            }
        }
    }

    suspend fun rebond(stashState: StakingState, amount: BigInteger, collatorAddress: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.chain, stashState.executionAddressId) {
                stakingScenarioInteractor.rebond(this, amount, collatorAddress)
            }
        }
    }
}
