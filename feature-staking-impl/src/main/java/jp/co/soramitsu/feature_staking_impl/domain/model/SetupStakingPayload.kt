package jp.co.soramitsu.feature_staking_impl.domain.model

import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import java.math.BigDecimal

class SetupStakingPayload(
    val amount: BigDecimal,
    val token: Token,
    val accountAddress: String,
    val rewardDestination: RewardDestination
) {
    val amountInPlanks = token.planksFromAmount(amount)
}

sealed class RewardDestination {

    object Restake : RewardDestination()

    class Payout(val targetAccountId: AccountId) : RewardDestination()
}