package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val MOONBEAM_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class MoonbeamBonusPayload(
    override val referralCode: String,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * MOONBEAM_BONUS_MULTIPLIER }
    }
}
