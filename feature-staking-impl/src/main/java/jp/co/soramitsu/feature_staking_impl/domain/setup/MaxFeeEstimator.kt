package jp.co.soramitsu.feature_staking_impl.domain.setup

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import java.math.BigDecimal
import java.math.BigInteger

class MaxFeeEstimator(
    private val substrateCalls: SubstrateCalls,
    private val accountRepository: AccountRepository,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun estimateMaxSetupStakingFee(
        originAddress: String,
        tokenType: Token.Type,
        skipBond: Boolean,
        amount: BigInteger = fakeAmount(),
        rewardDestination: RewardDestination = fakeRewardDestination(),
        nominations: List<MultiAddress> = fakeTargets(),
    ): BigDecimal {
        val account = accountRepository.getAccount(originAddress)

        val extrinsicBuilder = with(extrinsicBuilderFactory) { create(account, fakeKeypairProvider()) }

        val extrinsic = extrinsicBuilder.apply {
            if (!skipBond) {
                bond(MultiAddress.Id(originAddress.toAccountId()), amount, rewardDestination)
            }

            nominate(nominations)
        }.build()

        return tokenType.amountFromPlanks(substrateCalls.getExtrinsicFee(extrinsic))
    }

    private fun fakeRewardDestination() = RewardDestination.Payout(fakeAccountId())

    private fun fakeTargets() = MutableList(16) { MultiAddress.Id(fakeAccountId()) }

    private fun fakeAmount() = 1_000_000_000.toBigInteger()

    private fun fakeAccountId() = ByteArray(32)
}
