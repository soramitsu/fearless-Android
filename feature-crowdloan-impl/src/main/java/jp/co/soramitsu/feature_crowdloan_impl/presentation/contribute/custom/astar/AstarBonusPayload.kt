package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize

@Parcelize
class AstarBonusPayload(
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
