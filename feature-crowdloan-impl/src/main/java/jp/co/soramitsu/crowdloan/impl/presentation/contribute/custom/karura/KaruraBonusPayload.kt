package jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.karura

import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.referral.ReferralCodePayload
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.parcelize.Parcelize
import java.math.BigDecimal

val KARURA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class KaruraBonusPayload(
    override val referralCode: String,
    val chainId: ChainId,
    private val rewardRate: BigDecimal?
) : ReferralCodePayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal? {
        return rewardRate?.let { amount * rewardRate * KARURA_BONUS_MULTIPLIER }
    }
}
