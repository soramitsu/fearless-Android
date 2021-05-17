package jp.co.soramitsu.feature_staking_impl.domain.validations.payout

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal
import java.math.BigInteger

class MakePayoutPayload(
    val originAddress: String,
    val fee: BigDecimal,
    val totalReward: BigDecimal,
    val tokenType: Token.Type,
    val payoutStakersCalls: List<PayoutStakersPayload>
) {
    data class PayoutStakersPayload(val era: BigInteger, val validatorAddress: String)
}
