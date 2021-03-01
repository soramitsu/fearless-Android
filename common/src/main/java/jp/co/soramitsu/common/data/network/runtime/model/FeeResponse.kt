package jp.co.soramitsu.common.data.network.runtime.model

import java.math.BigInteger

class FeeResponse(
    val partialFee: BigInteger,
    val weight: Long
)