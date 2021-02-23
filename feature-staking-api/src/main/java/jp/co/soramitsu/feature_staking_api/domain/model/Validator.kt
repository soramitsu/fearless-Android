package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

typealias Commission = BigDecimal
typealias ValidatorPrefs = Commission

class Identity(
    val display: String?,
    val legal: String?,
    val web: String?,
    val riot: String?,
    val email: String?,
    val pgpFingerprint: String?,
    val image: String?,
    val twitter: String?
)

class Validator(
    val slashed: Boolean,
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val nominatorStakes: List<IndividualExposure>,
    val commission: BigDecimal,
    val identity: Identity?,
    val apy: BigDecimal
)