package jp.co.soramitsu.feature_staking_impl.domain.staking.redeem

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

    suspend fun estimateFee(stakingState: StakingState.Stash): BigInteger {
        return withContext(Dispatchers.IO) {
            feeEstimator.estimateFee(stakingState.controllerAddress) {
                withdrawUnbonded(getSlashingSpansNumber(stakingState))
            }
        }
    }

    suspend fun redeem(stakingState: StakingState.Stash, asset: Asset): Result<RedeemConsequences> {
        return withContext(Dispatchers.IO) {
            val controllerAddress = stakingState.controllerAddress

            extrinsicService.submitExtrinsic(controllerAddress) {
                withdrawUnbonded(getSlashingSpansNumber(stakingState))
            }.map {
                RedeemConsequences(
                    willKillStash = asset.redeemable == asset.locked
                )
            }
        }
    }

    private suspend fun getSlashingSpansNumber(stakingState: StakingState.Stash): BigInteger {
        val slashingSpans = stakingRepository.getSlashingSpan(stakingState.stashId)

        return slashingSpans?.let {
            val totalSpans = it.prior.size + 1 //  all from prior + one for lastNonZeroSlash

            totalSpans.toBigInteger()
        } ?: BigInteger.ZERO
    }
}
