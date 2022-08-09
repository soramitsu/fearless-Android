package jp.co.soramitsu.featurestakingimpl.domain.staking.unbond

import java.math.BigInteger
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.featureaccountapi.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurestakingimpl.domain.model.Unbonding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UnbondInteractor(
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateFee(
        stashState: StakingState,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            extrinsicService.estimateFee(stashState.chain) {
                formExtrinsic.invoke(this)
            }
        }
    }

    suspend fun unbond(
        stashState: StakingState,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            extrinsicService.submitExtrinsic(stashState.chain, stashState.executionAddressId) {
                formExtrinsic.invoke(this)
            }
        }
    }

    // unbondings are always going from the oldest to newest so last in the list will be the newest one
    fun newestUnbondingAmount(unbondings: List<Unbonding>) = unbondings.last().amount

    fun allUnbondingsAmount(unbondings: List<Unbonding>): BigInteger = unbondings.sumByBigInteger(Unbonding::amount)
}
