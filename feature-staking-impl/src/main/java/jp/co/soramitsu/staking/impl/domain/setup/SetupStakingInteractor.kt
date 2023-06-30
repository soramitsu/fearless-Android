package jp.co.soramitsu.staking.impl.domain.setup

import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.ext.accountIdOf
import jp.co.soramitsu.runtime.ext.multiAddressOf
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.api.domain.model.Collator
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.bondSora
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.delegate
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.staking.impl.data.network.blockhain.calls.nominateSora
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger

class BondPayload(
    val amount: BigDecimal,
    val rewardDestination: RewardDestination
)

class SetupStakingInteractor(
    private val extrinsicService: ExtrinsicService,
    private val stakingSharedState: StakingSharedState
) {

    suspend fun estimateMaxSetupStakingFee(controllerAddress: String): BigInteger {
        return calculateSetupStakingFee(
            controllerAddress,
            fakeNominations(),
            fakeBondPayload()
        )
    }

    suspend fun estimateParachainFee(): BigInteger {
        val (chain, asset) = stakingSharedState.assetWithChain.first()
        return extrinsicService.estimateFee(chain) {
            val eth = fakeEthereumAddress()
            val fakeAmountInPlanks = asset.planksFromAmount(fakeAmount())

            delegate(eth, fakeAmountInPlanks, BigInteger.ZERO, BigInteger.ZERO)
        }
    }

    suspend fun estimateFinalParachainFee(selectedCollator: Collator, amountInPlanks: BigInteger, delegationCount: Int): BigInteger {
        val (chain, asset) = stakingSharedState.assetWithChain.first()

        return extrinsicService.estimateFee(chain) {
            delegate(selectedCollator.address.fromHex(), amountInPlanks, selectedCollator.delegationCount, delegationCount.toBigInteger())
        }
    }

    suspend fun calculateSetupStakingFee(
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?
    ): BigInteger {
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()

        return extrinsicService.estimateFee(chain) {
            formExtrinsic(chain, chainAsset, controllerAddress, validatorAccountIds, bondPayload)
        }
    }

    suspend fun setupStaking(
        selectedCollator: Collator,
        amountInPlanks: BigInteger,
        delegationCount: Int,
        accountAddress: String
    ) = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()

        val accountId = chain.accountIdOf(accountAddress)

        runCatching {
            extrinsicService.submitExtrinsic(chain, accountId) {
                delegate(selectedCollator.address.fromHex(), amountInPlanks, selectedCollator.delegationCount, delegationCount.toBigInteger())
            }
        }
    }

    suspend fun setupStaking(
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?
    ) = withContext(Dispatchers.Default) {
        val (chain, chainAsset) = stakingSharedState.assetWithChain.first()
        val accountId = chain.accountIdOf(controllerAddress)

        runCatching {
            extrinsicService.submitExtrinsic(chain, accountId) {
                formExtrinsic(chain, chainAsset, controllerAddress, validatorAccountIds, bondPayload)
            }
        }
    }

    private fun ExtrinsicBuilder.formExtrinsic(
        chain: Chain,
        chainAsset: Asset,
        controllerAddress: String,
        validatorAccountIdsHex: List<String>,
        bondPayload: BondPayload?
    ) {
        val validatorsIds = validatorAccountIdsHex.map(String::fromHex)

        when (chainAsset.syntheticStakingType()) {
            SyntheticStakingType.TERNOA,
            SyntheticStakingType.DEFAULT -> {
                val targets = validatorsIds.map(chain::multiAddressOf)

                bondPayload?.let {
                    val amountInPlanks = chainAsset.planksFromAmount(it.amount)

                    bond(chain.multiAddressOf(controllerAddress), amountInPlanks, it.rewardDestination)
                }

                nominate(targets)
            }
            SyntheticStakingType.SORA -> {
                bondPayload?.let {
                    val amountInPlanks = chainAsset.planksFromAmount(it.amount)
                    bondSora(controllerAddress.toAccountId(), amountInPlanks, it.rewardDestination)
                }

                nominateSora(validatorsIds)
            }
        }
    }

    private fun fakeRewardDestination() = RewardDestination.Payout(fakeAccountId())

    private fun fakeNominations() = MutableList(16) { fakeAccountId().toHexString() }

    private fun fakeBondPayload() = BondPayload(fakeAmount(), fakeRewardDestination())

    private fun fakeAmount() = 1_000_000_000.toBigDecimal()

    private fun fakeAccountId() = ByteArray(32)

    private fun fakeEthereumAddress() = ByteArray(20)
}
