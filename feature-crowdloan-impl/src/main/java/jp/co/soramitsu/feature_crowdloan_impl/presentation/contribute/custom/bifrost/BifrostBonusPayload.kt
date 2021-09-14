package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.bifrost

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val BIFROST_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class BifrostBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * BIFROST_BONUS_MULTIPLIER }
    }
}
