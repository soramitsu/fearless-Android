package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.karura

import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import kotlinx.android.parcel.Parcelize
import java.math.BigDecimal

val KARURA_BONUS_MULTIPLIER = 0.05.toBigDecimal() // 5%

@Parcelize
class KaruraBonusPayload(
    val referralCode: String,
    private val rewardRate: BigDecimal
) : BonusPayload {

    override fun calculateBonus(amount: BigDecimal): BigDecimal {
        return amount * rewardRate * KARURA_BONUS_MULTIPLIER
    }
}
