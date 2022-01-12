package jp.co.soramitsu.common.data.network.runtime.binding

import java.math.BigInteger
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot

typealias BlockNumber = BigInteger

typealias BlockHash = String

fun bindBlockNumber(scale: String, runtime: RuntimeSnapshot): BlockNumber {
    val type = runtime.typeRegistry["u32"] ?: incompatible()

    val dynamicInstance = type.fromHexOrIncompatible(scale, runtime)

    return bindNumber(dynamicInstance)
}
