package jp.co.soramitsu.featurestakingimpl.domain.staking.rebond

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featureaccountapi.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.featurestakingapi.data.StakingSharedState
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RebondInteractor(
    private val extrinsicService: ExtrinsicService,
    private val sharedStakingSate: StakingSharedState
) {

    suspend fun estimateFee(formExtrinsic: suspend ExtrinsicBuilder.() -> Unit): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = sharedStakingSate.chain()

            extrinsicService.estimateFee(chain) {
                formExtrinsic.invoke(this)
            }
        }
    }

    suspend fun rebond(stashState: StakingState, formExtrinsic: suspend ExtrinsicBuilder.() -> Unit): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.chain, stashState.executionAddressId) {
                formExtrinsic.invoke(this)
            }
        }
    }
}
