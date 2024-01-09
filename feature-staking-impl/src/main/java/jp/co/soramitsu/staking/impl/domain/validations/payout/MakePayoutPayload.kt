package jp.co.soramitsu.staking.impl.domain.validations.payout

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal
import java.math.BigInteger

class MakePayoutPayload(
    val originAddress: String,
    val fee: BigDecimal,
    val totalReward: BigDecimal,
    val chainAsset: Asset,
    val payoutStakersCalls: List<PayoutStakersPayload>
) {
    data class PayoutStakersPayload(val era: BigInteger, val validatorAddress: String)
}
