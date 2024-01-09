package jp.co.soramitsu.staking.impl.domain.staking.controller

import java.math.BigInteger
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.state.SingleAssetSharedState
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setController
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.setControllerSora
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ControllerInteractor(
    private val extrinsicService: ExtrinsicService,
    private val sharedStakingSate: StakingSharedState,
    private val stakingInteractor: StakingInteractor
) {
    suspend fun isControllerFeatureDeprecated(chainId: ChainId?): Boolean {
        val nonNullChainId = chainId.takeIf { it.isNullOrEmpty().not() } ?: sharedStakingSate.assetWithChain.first().chain.id

        val metadata = stakingInteractor.getChainMetadata(nonNullChainId)
        return metadata.staking().calls?.get("set_controller")?.arguments?.isEmpty() == true
    }

    suspend fun estimateFee(controllerAccountAddress: String, chainId: ChainId? = null): BigInteger {
        return withContext(Dispatchers.IO) {
            val (chain, asset) = if (chainId.isNullOrEmpty()) {
                sharedStakingSate.assetWithChain.first()
            } else {
                val chain = stakingInteractor.getChain(chainId)
                SingleAssetSharedState.AssetWithChain(chain, requireNotNull(chain.utilityAsset))
            }
            extrinsicService.estimateFee(chain) {
                if (isControllerFeatureDeprecated(chain.id)) {
                    setController()
                } else {
                    when (asset.syntheticStakingType()) {
                        SyntheticStakingType.TERNOA,
                        SyntheticStakingType.DEFAULT -> setController(chain.multiAddressOf(controllerAccountAddress))

                        SyntheticStakingType.SORA -> setControllerSora(controllerAccountAddress.toAccountId())
                    }
                }
            }
        }
    }

    suspend fun setController(stashAccountAddress: String, controllerAccountAddress: String, chainId: ChainId? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            val (chain, asset) = if (chainId.isNullOrEmpty()) {
                sharedStakingSate.assetWithChain.first()
            } else {
                val chain = stakingInteractor.getChain(chainId)
                SingleAssetSharedState.AssetWithChain(chain, requireNotNull(chain.utilityAsset))
            }
            val accountId = chain.accountIdOf(stashAccountAddress)

            extrinsicService.submitExtrinsic(chain, accountId) {
                if (isControllerFeatureDeprecated(chain.id)) {
                    setController()
                } else {
                    when (asset.syntheticStakingType()) {
                        SyntheticStakingType.TERNOA,
                        SyntheticStakingType.DEFAULT -> setController(chain.multiAddressOf(controllerAccountAddress))

                        SyntheticStakingType.SORA -> setControllerSora(controllerAccountAddress.toAccountId())
                    }
                }
            }
        }
    }
}
