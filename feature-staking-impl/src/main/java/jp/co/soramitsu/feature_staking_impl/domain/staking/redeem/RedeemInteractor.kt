package jp.co.soramitsu.feature_staking_impl.domain.staking.redeem

import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.withdrawUnbonded
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

private val STUB_SLASHING_SPANS = BigInteger.TEN

class RedeemInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService,
) {

    suspend fun estimateFee(accountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                withdrawUnbonded(STUB_SLASHING_SPANS)
            }
        }
    }

    suspend fun redeem(accountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(accountAddress) {
                // TODO replace STUB_SLASHING_SPANS with slashing spans call
                withdrawUnbonded(STUB_SLASHING_SPANS)
            }
        }
    }
}
