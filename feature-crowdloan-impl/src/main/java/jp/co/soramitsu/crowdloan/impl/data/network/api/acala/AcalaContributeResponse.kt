package jp.co.soramitsu.crowdloan.impl.data.network.api.acala

import java.math.BigInteger

class AcalaContributeResponse(
    val result: Boolean,
    val address: String,
    val email: String?,
    val referral: String?,
    val amount: BigInteger
)
