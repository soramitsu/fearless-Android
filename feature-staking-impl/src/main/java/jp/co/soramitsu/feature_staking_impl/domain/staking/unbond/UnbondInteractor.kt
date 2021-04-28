package jp.co.soramitsu.feature_staking_impl.domain.staking.unbond

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.unbond
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class UnbondInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService,
) {

    suspend fun estimateFee(accountAddress: String, amount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                unbond(amount)
            }
        }
    }

    suspend fun unbond(stashState: StakingState.Stash, amount: BigInteger): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.controllerAddress) {
                unbond(amount)
            }
        }
    }
}
