package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam

import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize

val MOONBEAM_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class MoonbeamBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? =
        rewardRate?.let { amount * rewardRate * MOONBEAM_BONUS_MULTIPLIER }
}
