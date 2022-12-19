package jp.co.soramitsu.staking.impl.domain.rewards

import jp.co.soramitsu.staking.api.domain.model.IndividualExposure
import java.math.BigDecimal
import java.math.BigInteger

class RewardCalculationTarget(
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val nominatorStakes: List<IndividualExposure>,
    val commission: BigDecimal
)
