package jp.co.soramitsu.staking.impl.domain.staking.rebond

import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

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
