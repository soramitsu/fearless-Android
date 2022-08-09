package jp.co.soramitsu.featurestakingimpl.domain.staking.rewardDestination

import java.math.BigInteger
import jp.co.soramitsu.featureaccountapi.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.featurestakingapi.domain.model.RewardDestination
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurestakingimpl.data.network.blockhain.calls.setPayee
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
