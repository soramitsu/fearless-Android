package jp.co.soramitsu.feature_staking_impl.domain.setup

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_staking_impl.domain.model.StashSetup
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import java.math.BigDecimal
import java.math.BigInteger

class MaxFeeEstimator(
    private val substrateCalls: SubstrateCalls,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun estimateMaxSetupStakingFee(
        tokenType: Token.Type,
        controllerAddress: String
    ): BigDecimal = estimateMaxSetupStakingFee(
        tokenType,
        fakeStashSetup(controllerAddress),
        fakeAmount(),
        fakeNominations()
    )

    suspend fun estimateMaxSetupStakingFee(
        tokenType: Token.Type,
        stashSetup: StashSetup,
        amount: BigInteger,
        nominations: List<MultiAddress>,
    ): BigDecimal {
        val extrinsicBuilder = with(extrinsicBuilderFactory) { create(stashSetup.controllerAddress, fakeKeypairProvider()) }

        val extrinsic = extrinsicBuilder.apply {
            if (stashSetup.alreadyHasStash.not()) {
                bond(MultiAddress.Id(stashSetup.controllerAddress.toAccountId()), amount, stashSetup.rewardDestination)
            }

            nominate(nominations)
        }.build()

        return tokenType.amountFromPlanks(substrateCalls.getExtrinsicFee(extrinsic))
    }

    private fun fakeStashSetup(controllerAddress: String): StashSetup {
        return StashSetup(fakeRewardDestination(), controllerAddress, alreadyHasStash = false)
    }

    private fun fakeRewardDestination() = RewardDestination.Payout(fakeAccountId())

    private fun fakeNominations() = MutableList(16) { MultiAddress.Id(fakeAccountId()) }

    private fun fakeAmount() = 1_000_000_000.toBigInteger()

    private fun fakeAccountId() = ByteArray(32)
}
