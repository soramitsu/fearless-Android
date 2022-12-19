package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala

import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

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
    val chainId: ChainId,
    val parachainId: ParaId,
    val baseUrl: String,
    private val bonusRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = when {
        rewardRate == null || bonusRate == null -> null
        else -> amount * rewardRate * bonusRate
    }
}
