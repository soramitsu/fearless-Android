package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val ASTAR_BONUS_MULTIPLIER = 0.01.toBigDecimal() // 1%
val ASTAR_FRIEND_BONUS_MULTIPLIER = 0.10.toBigDecimal() // 10%

@Parcelize
class AstarBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * ASTAR_BONUS_MULTIPLIER }
    }
}
