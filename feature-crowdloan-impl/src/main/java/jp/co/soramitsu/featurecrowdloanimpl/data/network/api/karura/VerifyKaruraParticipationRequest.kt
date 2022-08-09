package jp.co.soramitsu.featurecrowdloanimpl.data.network.api.karura

import java.math.BigInteger

class VerifyKaruraParticipationRequest(
    val address: String,
    val amount: BigInteger,
    val referral: String,
    val signature: String
)
