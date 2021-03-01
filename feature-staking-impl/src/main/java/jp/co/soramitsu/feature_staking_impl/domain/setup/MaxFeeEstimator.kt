package jp.co.soramitsu.feature_staking_impl.domain.setup

import jp.co.soramitsu.common.data.network.runtime.binding.MultiAddress
import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.bond
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.nominate
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.calls.setController
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.runtime.extrinsic.ExtrinsicBuilderFactory
import java.math.BigDecimal

class MaxFeeEstimator(
    private val substrateCalls: SubstrateCalls,
    private val accountRepository: AccountRepository,
    private val extrinsicBuilderFactory: ExtrinsicBuilderFactory
) {

    suspend fun estimateMaxSetupStakingFee(
        account: Account,
        tokenType: Token.Type
    ): BigDecimal {
        val extrinsicBuilder = with(extrinsicBuilderFactory) { create(account, fakeKeypairProvider()) }

        val extrinsic = extrinsicBuilder
            .setController(fakeAddress())
            .bond(fakeAddress(), fakeAmount(), RewardDestination.Payout(fakeAccountId()))
            .nominate(fakeTargets())
            .build()

        return tokenType.amountFromPlanks(substrateCalls.getExtrinsicFee(extrinsic))
    }

    suspend fun estimateMaxSetupStakingFee(
        originAddress: String,
        tokenType: Token.Type
    ): BigDecimal {
        val account = accountRepository.getAccount(originAddress)

        return estimateMaxSetupStakingFee(account, tokenType)
    }

    private fun fakeTargets() = MutableList(16) { MultiAddress.Id(fakeAccountId()) }

    private fun fakeAmount() = 1_000_000_000.toBigInteger()

    private fun fakeAccountId() = ByteArray(32)

    private fun fakeAddress() = MultiAddress.Id(fakeAccountId())
}