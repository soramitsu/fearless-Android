package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize

val ACALA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class AcalaBonusPayload(
    override val referralCode: String,
    private val rewardRate: BigDecimal?,
    val email: String?,
    val agreeReceiveEmail: Boolean?,
    var contributionType: Int?,
    val parachainId: ParaId,
    val baseUrl: String,
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * ACALA_BONUS_MULTIPLIER }
    }
}
