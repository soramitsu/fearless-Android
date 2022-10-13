package jp.co.soramitsu.wallet.impl.domain.beacon

import java.math.BigInteger

interface WithAmount {

    val amount: BigInteger
}

sealed class SignableOperation(
    val module: String,
    val call: String,
    val args: Map<String, Any?>,
    val rawData: String
) {

    class Transfer(
        module: String,
        call: String,
        args: Map<String, Any?>,
        rawData: String,
        val destination: String,
        override val amount: BigInteger
    ) : SignableOperation(module, call, args, rawData), WithAmount

    class Other(
        module: String,
        call: String,
        args: Map<String, Any?>,
        rawData: String
    ) : SignableOperation(module, call, args, rawData)
}
