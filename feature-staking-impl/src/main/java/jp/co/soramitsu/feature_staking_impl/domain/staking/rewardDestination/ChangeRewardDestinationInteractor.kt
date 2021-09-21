package jp.co.soramitsu.feature_staking_impl.domain.staking.rewardDestination

import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.setPayee
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class ChangeRewardDestinationInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService,
) {

    suspend fun estimateFee(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination,
        token: Token,
    ): BigDecimal = withContext(Dispatchers.IO) {
        val feeInPlanks = feeEstimator.estimateFee(stashState.controllerAddress) {
            setPayee(rewardDestination)
        }

        token.amountFromPlanks(feeInPlanks)
    }

    suspend fun changeRewardDestination(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination,
    ): Result<String> = withContext(Dispatchers.IO) {
        extrinsicService.submitExtrinsic(stashState.controllerAddress) {
            setPayee(rewardDestination)
        }
    }
}
