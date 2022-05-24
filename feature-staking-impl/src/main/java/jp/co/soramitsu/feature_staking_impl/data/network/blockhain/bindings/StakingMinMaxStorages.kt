package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.fromHexOrIncompatible
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHexOrNull
import java.math.BigInteger

fun bindMinBond(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(scale, runtimeSnapshot, type)
}

fun bindMaxNominators(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(scale, runtimeSnapshot, type)
}

fun bindNominatorsCount(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(scale, runtimeSnapshot, type)
}

fun bindRound(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("ParachainStaking", "Round")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return bindNumber(decoded.getTyped("current"))
}

fun bindSelectedCandidates(
    scale: String,
    runtime: RuntimeSnapshot
): List<ByteArray> {
    val returnType = runtime.metadata.storageReturnType("ParachainStaking", "SelectedCandidates")
    @Suppress("UNCHECKED_CAST")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? List<ByteArray> ?: incompatible()

    return decoded
}

private fun bindNumber(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(type.fromHexOrIncompatible(scale, runtimeSnapshot))
}
