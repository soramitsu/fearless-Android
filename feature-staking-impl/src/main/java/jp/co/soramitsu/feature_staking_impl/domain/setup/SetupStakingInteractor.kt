package jp.co.soramitsu.feature_staking_impl.domain.setup

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.data.extrinsic.ExtrinsicService
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class BondPayload(
    val amount: BigDecimal,
    val rewardDestination: RewardDestination
)

class SetupStakingInteractor(
    private val feeEstimator: FeeEstimator,
    private val extrinsicService: ExtrinsicService
) {

    suspend fun estimateMaxSetupStakingFee(
        tokenType: Token.Type,
        controllerAddress: String
    ): BigDecimal = calculateSetupStakingFee(
        tokenType,
        controllerAddress,
        fakeNominations(),
        fakeBondPayload()
    )

    suspend fun calculateSetupStakingFee(
        tokenType: Token.Type,
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?
    ): BigDecimal {
        val feeInPlanks = feeEstimator.estimateFee(controllerAddress) {
            formExtrinsic(tokenType, controllerAddress, validatorAccountIds, bondPayload)
        }

        return tokenType.amountFromPlanks(feeInPlanks)
    }

    suspend fun setupStaking(
        chainAsset: Token.Type,
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?
    ) = withContext(Dispatchers.Default) {
        runCatching {
            extrinsicService.submitExtrinsic(controllerAddress) {
                formExtrinsic(chainAsset, controllerAddress, validatorAccountIds, bondPayload)
            }
        }
    }

    private fun ExtrinsicBuilder.formExtrinsic(
        tokenType: Token.Type,
        controllerAddress: String,
        validatorAccountIds: List<String>,
        bondPayload: BondPayload?,
    ) {
        val targets = validatorAccountIds.map { MultiAddress.Id(it.fromHex()) }

        bondPayload?.let {
            val amountInPlanks = tokenType.planksFromAmount(it.amount)

            bond(MultiAddress.Id(controllerAddress.toAccountId()), amountInPlanks, it.rewardDestination)
        }

        nominate(targets)
    }

    private fun fakeRewardDestination() = RewardDestination.Payout(fakeAccountId())

    private fun fakeNominations() = MutableList(16) { fakeAccountId().toHexString() }

    private fun fakeBondPayload() = BondPayload(fakeAmount(), fakeRewardDestination())

    private fun fakeAmount() = 1_000_000_000.toBigDecimal()

    private fun fakeAccountId() = ByteArray(32)
}
