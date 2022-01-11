package jp.co.soramitsu.feature_staking_impl.domain.staking.rewardDestination

import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.setPayee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class ChangeRewardDestinationInteractor(
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateFee(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination,
    ): BigInteger = withContext(Dispatchers.IO) {
        extrinsicService.estimateFee(stashState.chain) {
            setPayee(rewardDestination)
        }
    }

    suspend fun changeRewardDestination(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination,
    ): Result<String> = withContext(Dispatchers.IO) {
        extrinsicService.submitExtrinsic(stashState.chain, stashState.controllerId) {
            setPayee(rewardDestination)
        }
    }
}
