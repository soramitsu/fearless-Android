package jp.co.soramitsu.common.data.network.runtime.model

import androidx.annotation.Keep
import java.math.BigInteger

@Keep
data class QuoteResponse(
    val amount: BigInteger,
    val fee: BigInteger,
)
