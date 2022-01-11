package jp.co.soramitsu.feature_staking_impl.domain.staking.bond

import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bondMore
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class BondMoreInteractor(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState,
) {

    suspend fun estimateFee(amount: BigInteger): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()

            extrinsicService.estimateFee(chain) {
                bondMore(amount)
            }
        }
    }

    suspend fun bondMore(accountAddress: String, amount: BigInteger): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = stakingSharedState.chain()
            val accountId = chain.accountIdOf(accountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                bondMore(amount)
            }
        }
    }
}
