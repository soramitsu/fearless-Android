package jp.co.soramitsu.feature_crowdloan_impl.data.network.api.acala

import java.math.BigInteger

class AcalaTransferRequest(
    val address: String,
    val amount: BigInteger,
    val referral: String?,
    val email: String?,
    val receiveEmail: Boolean?,
)
