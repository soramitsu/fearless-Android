package jp.co.soramitsu.feature_staking_api.domain.model

import java.math.BigDecimal
import java.math.BigInteger

typealias Commission = BigDecimal
class ValidatorPrefs(val commission: Commission, val blocked: Boolean)

interface Identity {
    val display: String?
    val legal: String?
    val web: String?
    val riot: String?
    val email: String?
    val pgpFingerprint: String?
    val image: String?
    val twitter: String?
}

class RootIdentity(
    override val display: String?,
    override val legal: String?,
    override val web: String?,
    override val riot: String?,
    override val email: String?,
    override val pgpFingerprint: String?,
    override val image: String?,
    override val twitter: String?
) : Identity

class ChildIdentity(
    childName: String?,
    parentIdentity: Identity
) : Identity by parentIdentity {

    override val display: String = "${parentIdentity.display} / $childName"
}

class SuperOf(
    val parentIdHex: String,
    val childName: String?
)

class Validator(
    val slashed: Boolean,
    val accountIdHex: String,
    val totalStake: BigInteger,
    val ownStake: BigInteger,
    val nominatorStakes: List<IndividualExposure>,
    val prefs: ValidatorPrefs,
    val identity: Identity?,
    val apy: BigDecimal
)
