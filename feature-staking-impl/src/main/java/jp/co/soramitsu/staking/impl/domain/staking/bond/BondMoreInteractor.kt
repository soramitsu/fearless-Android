package jp.co.soramitsu.staking.impl.domain.staking.bond

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BondMoreInteractor(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState
) {

    suspend fun estimateFee(
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()

            extrinsicService.estimateFee(chain) {
                formExtrinsic.invoke(this)
            }
        }
    }

    suspend fun bondMore(
        accountAddress: String,
        formExtrinsic: suspend ExtrinsicBuilder.() -> Unit
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                formExtrinsic.invoke(this)
            }
        }
    }
}
