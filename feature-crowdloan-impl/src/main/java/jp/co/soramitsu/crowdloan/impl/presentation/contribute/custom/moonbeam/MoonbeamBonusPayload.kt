package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.moonbeam

import java.math.BigDecimal
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize

@Parcelize
class MoonbeamBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId,
    private val rewardRate: BigDecimal?,
    private val bonusRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = when {
        rewardRate == null || bonusRate == null -> null
        else -> amount * rewardRate * bonusRate
    }
}
