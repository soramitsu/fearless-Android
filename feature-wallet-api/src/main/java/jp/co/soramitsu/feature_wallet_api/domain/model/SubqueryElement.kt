package jp.co.soramitsu.feature_wallet_api.domain.model

import java.math.BigInteger

data class SubqueryElement(
    val hash : String,
    val address: String,
    val accountName: String?,
    val operation: String,
    val amount: BigInteger,
    val time: Long,
    val tokenType: Token.Type,
    val extra: String? = null
)
