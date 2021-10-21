package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val ACALA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class AcalaBonusPayload(
    override val referralCode: String,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * ACALA_BONUS_MULTIPLIER }
    }
}
