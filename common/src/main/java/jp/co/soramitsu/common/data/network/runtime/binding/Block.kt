package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

typealias BlockNumber = BigInteger

fun bindBlockNumber(scale: String, runtime: RuntimeSnapshot): BlockNumber {
    val type = runtime.typeRegistry["BlockNumber"] ?: incompatible()

    val dynamicInstance = type.fromHexOrIncompatible(scale, runtime)

    return bindNumber(dynamicInstance)
}
