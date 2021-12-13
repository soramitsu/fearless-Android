package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay

import jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

@Parcelize
class InterlayBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId,
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = null
}
