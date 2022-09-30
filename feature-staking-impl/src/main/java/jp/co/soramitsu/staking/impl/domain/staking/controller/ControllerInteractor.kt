package jp.co.soramitsu.staking.impl.domain.staking.controller

import jp.co.soramitsu.account.api.extrinsic.ExtrinsicService
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setController
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.state.chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigInteger

class ControllerInteractor(
    private val extrinsicService: ExtrinsicService,
    private val sharedStakingSate: StakingSharedState
) {
    suspend fun estimateFee(controllerAccountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            val chain = sharedStakingSate.chain()

            extrinsicService.estimateFee(chain) {
                setController(chain.multiAddressOf(controllerAccountAddress))
            }
        }
    }

    suspend fun setController(stashAccountAddress: String, controllerAccountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val chain = sharedStakingSate.chain()
            val accountId = chain.accountIdOf(stashAccountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                setController(chain.multiAddressOf(controllerAccountAddress))
            }
        }
    }
}
