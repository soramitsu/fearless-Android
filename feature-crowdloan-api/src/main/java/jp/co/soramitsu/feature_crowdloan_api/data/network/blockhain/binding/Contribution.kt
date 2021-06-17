package jp.co.soramitsu.feature_crowdloan_api.data.network.blockhain.binding

import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.bindString
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import java.math.BigInteger

class Contribution(
    val amount: BigInteger,
    val memo: String
)

fun bindContribution(scale: String, runtime: RuntimeSnapshot): Contribution {
    val type = runtime.typeRegistry["(BalanceOf, Vec<u8>)"] ?: incompatible()

    val dynamicInstance = type.fromHex(runtime, scale).cast<List<*>>()

    return Contribution(
        amount = bindNumber(dynamicInstance[0]),
        memo = bindString(dynamicInstance[1])
    )
}
