package jp.co.soramitsu.common.data.network.runtime.binding

import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import java.math.BigInteger

typealias BlockNumber = BigInteger

typealias BlockHash = String

fun bindBlockNumber(scale: String, runtime: RuntimeSnapshot): BlockNumber {
    val type = runtime.typeRegistry["u32"] ?: incompatible()

    val dynamicInstance = type.fromHexOrIncompatible(scale, runtime)

    return bindNumber(dynamicInstance)
}
