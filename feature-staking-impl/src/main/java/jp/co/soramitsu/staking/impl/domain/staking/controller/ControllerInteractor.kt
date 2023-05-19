package jp.co.soramitsu.staking.impl.domain.staking.controller

import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setController
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setControllerSora
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigInteger

class ControllerInteractor(
    private val extrinsicService: ExtrinsicService,
    private val sharedStakingSate: StakingSharedState
) {
    suspend fun estimateFee(controllerAccountAddress: String): BigInteger {
        return withContext(Dispatchers.IO) {
            val (chain, asset) = sharedStakingSate.assetWithChain.first()

            extrinsicService.estimateFee(chain) {
                when (asset.syntheticStakingType()) {
                    SyntheticStakingType.DEFAULT -> setController(chain.multiAddressOf(controllerAccountAddress))
                    SyntheticStakingType.SORA -> setControllerSora(controllerAccountAddress.toAccountId())
                }
            }
        }
    }

    suspend fun setController(stashAccountAddress: String, controllerAccountAddress: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val (chain, asset) = sharedStakingSate.assetWithChain.first()
            val accountId = chain.accountIdOf(stashAccountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                when (asset.syntheticStakingType()) {
                    SyntheticStakingType.DEFAULT -> setController(chain.multiAddressOf(controllerAccountAddress))
                    SyntheticStakingType.SORA -> setControllerSora(controllerAccountAddress.toAccountId())
                }
            }
        }
    }
}
