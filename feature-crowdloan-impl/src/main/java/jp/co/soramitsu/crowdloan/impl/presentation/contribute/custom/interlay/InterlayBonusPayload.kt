package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.interlay

import java.math.BigDecimal
import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import kotlinx.parcelize.Parcelize

@Parcelize
class InterlayBonusPayload(
    override val referralCode: String,
    val parachainId: ParaId
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = null
}
