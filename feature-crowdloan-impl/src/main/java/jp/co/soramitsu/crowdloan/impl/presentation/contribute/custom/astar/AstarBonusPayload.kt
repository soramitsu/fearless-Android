package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar

import jp.co.soramitsu.crowdloan.api.data.network.blockhain.binding.ParaId
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

@Parcelize
class AstarBonusPayload(
    override val referralCode: String,
    val chainId: ChainId,
    val parachainId: ParaId,
    private val rewardRate: BigDecimal?,
    private val bonusRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? = when {
        rewardRate == null || bonusRate == null -> null
        else -> amount * rewardRate * bonusRate
    }
}
