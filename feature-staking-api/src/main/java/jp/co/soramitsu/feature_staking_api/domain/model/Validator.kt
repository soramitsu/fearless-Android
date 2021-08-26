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
    override val twitter: String?,
) : Identity

class ChildIdentity(
    childName: String?,
    val parentIdentity: Identity,
) : Identity by parentIdentity {

    override val display: String = "${parentIdentity.display} / ${childName.orEmpty()}"
}

class SuperOf(
    val parentIdHex: String,
    val childName: String?,
)

class Validator(
    val address: String,
    val slashed: Boolean,
    val accountIdHex: String,
    val prefs: ValidatorPrefs?,
    val electedInfo: ElectedInfo?,
    val identity: Identity?,
) {

    class ElectedInfo(
        val totalStake: BigInteger,
        val ownStake: BigInteger,
        val nominatorStakes: List<IndividualExposure>,
        val apy: BigDecimal,
        val maxNominators: Int,
        val isOversubscribed: Boolean
    )
}
