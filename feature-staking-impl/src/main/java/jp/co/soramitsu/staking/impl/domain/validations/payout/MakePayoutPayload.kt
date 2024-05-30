package jp.co.soramitsu.staking.impl.domain.validations.payout

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.wallet.impl.domain.model.Token

open class MakePayoutPayload(
    val originAddress: String,
    val fee: BigDecimal,
    val totalReward: BigDecimal,
    val token: Token,
    val payoutStakersCalls: List<PayoutStakersPayload>
) {
    data class PayoutStakersPayload(val era: BigInteger, val validatorAddress: String)
}

class SoraPayoutsPayload(
    originAddress: String,
    fee: BigDecimal,
    totalReward: BigDecimal,
    utilityToken: Token,
    val rewardToken: Token,
    payoutStakersCalls: List<PayoutStakersPayload>
): MakePayoutPayload(originAddress, fee, totalReward, utilityToken, payoutStakersCalls) {

}