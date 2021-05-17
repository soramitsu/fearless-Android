package jp.co.soramitsu.feature_staking_impl.domain.payout

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.payoutStakers
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class PayoutInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimatePayoutFee(accountAddress: String, payouts: List<Payout>): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                payouts.forEach {
                    payoutStakers(it.era, it.validatorAddress.toAccountId())
                }
            }
        }
    }

    suspend fun makePayouts(payload: MakePayoutPayload): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(payload.originAddress) {
                payload.payoutStakersCalls.forEach {
                    payoutStakers(it.era, it.validatorAddress.toAccountId())
                }
            }
        }
    }
}
