package jp.co.soramitsu.feature_staking_impl.domain.setup

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

class BondPayload(
    val amount: BigDecimal,
    val rewardDestination: RewardDestination,
)

class SetupStakingInteractor(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState,
) {

    suspend fun estimateMaxSetupStakingFee(controllerAddress: String): BigInteger {
        return calculateSetupStakingFee(
            controllerAddress,
            fakeNominations(),
            fakeBondPayload()
        )
    }

    suspend fun calculateSetupStakingFee(
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?,
    ): BigInteger {
        val (chain, chainAsset) = stakingSharedState.assetWithChainWithChain.first()

        return extrinsicService.estimateFee(chain) {
            formExtrinsic(chain, chainAsset, controllerAddress, validatorAccountIds, bondPayload)
        }
    }

    suspend fun setupStaking(
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?,
    ) = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = stakingSharedState.assetWithChainWithChain.first()
        val accountId = chain.accountIdOf(controllerAddress)

        runCatching {
            extrinsicService.submitExtrinsic(chain, accountId) {
                formExtrinsic(chain, chainAsset, controllerAddress, validatorAccountIds, bondPayload)
            }
        }
    }

    private fun ExtrinsicBuilder.formExtrinsic(
        chain: Chain,
        chainAsset: Chain.Asset,
        controllerAddress: String,
        validatorAccountIdsHex: List<String>,
        bondPayload: BondPayload?,
    ) {
        val validatorsIds = validatorAccountIdsHex.map(String::fromHex)
        val targets = validatorsIds.map(chain::multiAddressOf)

        bondPayload?.let {
            val amountInPlanks = chainAsset.planksFromAmount(it.amount)

            bond(chain.multiAddressOf(controllerAddress), amountInPlanks, it.rewardDestination)
        }

        nominate(targets)
    }

    private fun fakeRewardDestination() = RewardDestination.Payout(fakeAccountId())

    private fun fakeNominations() = MutableList(16) { fakeAccountId().toHexString() }

    private fun fakeBondPayload() = BondPayload(fakeAmount(), fakeRewardDestination())

    private fun fakeAmount() = 1_000_000_000.toBigDecimal()

    private fun fakeAccountId() = ByteArray(32)
}
