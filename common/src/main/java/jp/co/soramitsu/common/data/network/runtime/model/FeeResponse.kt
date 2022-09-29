package jp.co.soramitsu.common.data.network.runtime.model

import java.math.BigInteger

class FeeResponse(
    val partialFee: BigInteger
    // todo research why it is not working on westend
//    val weight: BigInteger
)
