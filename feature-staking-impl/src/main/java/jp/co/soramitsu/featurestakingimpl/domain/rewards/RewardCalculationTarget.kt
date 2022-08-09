package jp.co.soramitsu.featurestakingimpl.domain.rewards

import jp.co.soramitsu.featurestakingapi.domain.model.IndividualExposure
import java.math.BigDecimal
import java.math.BigInteger

class RewardCalculationTarget(
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val nominatorStakes: List<IndividualExposure>,
    val commission: BigDecimal
)
