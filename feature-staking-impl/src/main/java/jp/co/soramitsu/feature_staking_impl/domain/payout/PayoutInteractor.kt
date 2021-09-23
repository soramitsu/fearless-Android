package jp.co.soramitsu.feature_staking_impl.domain.payout

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.model.Payout
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.payoutStakers
import jp.co.soramitsu.feature_staking_impl.domain.validations.payout.MakePayoutPayload
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class PayoutInteractor(
    private val stakingSharedState: StakingSharedState,
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimatePayoutFee(accountAddress: String, payouts: List<Payout>): BigInteger {
        return withContext(Dispatchers.IO) {
            extrinsicService.estimateFee(stakingSharedState.chain()) {
                payouts.forEach {
                    payoutStakers(it.era, it.validatorAddress.toAccountId())
                }
            }
        }
    }

    suspend fun makePayouts(payload: MakePayoutPayload): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(payload.originAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                payload.payoutStakersCalls.forEach {
                    payoutStakers(it.era, it.validatorAddress.toAccountId())
                }
            }
        }
    }
}
