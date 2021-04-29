package jp.co.soramitsu.feature_staking_impl.domain.staking.redeem

import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.withdrawUnbonded
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.extrinsic.FeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class RedeemInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService,
    private val stakingRepository: StakingRepository,
) {

    suspend fun estimateFee(accountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(accountAddress) {
                withdrawUnbonded(getSlashingSpansNumber(accountAddress))
            }
        }
    }

    suspend fun redeem(stash: StakingState.Stash, asset: Asset): Result<RedeemConsequences> {
        return withContext(Dispatchers.IO) {
            val controllerAddress = stash.controllerAddress

            extrinsicService.submitExtrinsic(controllerAddress) {
                withdrawUnbonded(getSlashingSpansNumber(controllerAddress))
            }.map {
                RedeemConsequences(
                    willKillStash = asset.redeemable == asset.locked
                )
            }
        }
    }

    private suspend fun getSlashingSpansNumber(accountAddress: String): BigInteger {
        val slashingSpans = stakingRepository.getSlashingSpan(accountAddress.toAccountId())

        return slashingSpans?.let {
            val totalSpans = it.prior.size + 1 //  all from prior + one for lastNonZeroSlash

            totalSpans.toBigInteger()
        } ?: BigInteger.ZERO
    }
}
