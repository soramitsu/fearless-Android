package jp.co.soramitsu.staking.impl.domain.rewards

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.staking.api.domain.model.IndividualExposure

class RewardCalculationTarget(
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val nominatorStakes: List<IndividualExposure>,
    val commission: BigDecimal
)
