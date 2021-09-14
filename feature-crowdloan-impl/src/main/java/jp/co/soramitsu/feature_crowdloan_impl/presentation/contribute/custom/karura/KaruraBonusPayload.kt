package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val KARURA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class KaruraBonusPayload(
    override val referralCode: String,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * KARURA_BONUS_MULTIPLIER }
    }
}
