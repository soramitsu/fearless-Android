package jp.co.soramitsu.staking.impl.domain.staking.rewardDestination

import java.math.BigInteger
import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setPayee
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChangeRewardDestinationInteractor(
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateFee(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination
    ): BigInteger = withContext(Dispatchers.IO) {
        extrinsicService.estimateFee(stashState.chain) {
            setPayee(rewardDestination)
        }
    }

    suspend fun changeRewardDestination(
        stashState: StakingState.Stash,
        rewardDestination: RewardDestination
    ): Result<String> = withContext(Dispatchers.IO) {
        extrinsicService.submitExtrinsic(stashState.chain, stashState.controllerId) {
            setPayee(rewardDestination)
        }
    }
}
