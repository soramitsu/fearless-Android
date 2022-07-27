package jp.co.soramitsu.feature_staking_impl.domain.staking.redeem

import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import java.math.BigInteger
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RedeemInteractor(
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateFee(
        stashState: StakingState,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            extrinsicService.estimateFee(stashState.chain) {
                formExtrinsic.invoke(this)
            }
        }
    }

    suspend fun redeem(
        stashState: StakingState,
        asset: Asset,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit,
    ): Result<RedeemConsequences> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.chain, stashState.executionAddressId) {
                formExtrinsic.invoke(this)
            }.map {
                RedeemConsequences(
                    willKillStash = asset.redeemable == asset.locked
                )
            }
        }
    }
}
