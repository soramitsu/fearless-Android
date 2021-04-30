package jp.co.soramitsu.feature_staking_impl.domain.staking.controller

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.setController
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class ControllerInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService
) {
    suspend fun estimateFee(stashAccountAddress: String, controllerAccountAddress: String): BigInteger {
        return withContext(Dispatchers.IO){
            feeEstimator.estimateFee(stashAccountAddress) {
                setController(MultiAddress.Id(controllerAccountAddress.toAccountId()))
            }
        }
    }

    suspend fun setController(stashAccountAddress: String, controllerAccountAddress: String): Result<String> {
        return withContext(Dispatchers.IO){
            extrinsicService.submitExtrinsic(stashAccountAddress) {
                setController(MultiAddress.Id(controllerAccountAddress.toAccountId()))
            }
        }
    }
}
