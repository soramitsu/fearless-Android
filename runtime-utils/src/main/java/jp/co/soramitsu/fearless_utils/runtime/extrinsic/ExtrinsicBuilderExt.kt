package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import java.math.BigInteger

fun ExtrinsicBuilder.transfer(recipientAccountId: ByteArray, amount: BigInteger): ExtrinsicBuilder {
    return call(
        moduleName = "Balances",
        callName = "transfer",
        arguments = mapOf(
            "dest" to DictEnum.Entry(
                name = "Id",
                value = recipientAccountId
            ),
            "value" to amount
        )
    )
}
