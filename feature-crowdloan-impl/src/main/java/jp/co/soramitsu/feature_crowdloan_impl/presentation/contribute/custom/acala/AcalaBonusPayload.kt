package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala

import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize

enum class AcalaContributionType {
    DirectDOT, LcDOT
}

@Parcelize
class AcalaBonusPayload(
    override val referralCode: String,
    private val rewardRate: BigDecimal?,
    val email: String?,
    val agreeReceiveEmail: Boolean?,
    var contributionType: AcalaContributionType?,
    val parachainId: ParaId,
    val baseUrl: String,
    private val bonusRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = when {
        rewardRate == null || bonusRate == null -> null
        else -> amount * rewardRate * bonusRate
    }
}
