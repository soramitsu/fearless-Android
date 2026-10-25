package jp.co.soramitsu.core.runtime.models.responses

import java.math.BigInteger

data class QuoteResponse(
    val amount: BigInteger,
    val route: List<String>? = null
)
