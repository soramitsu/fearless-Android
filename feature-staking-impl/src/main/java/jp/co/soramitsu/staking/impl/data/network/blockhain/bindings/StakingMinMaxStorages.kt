package jp.co.soramitsu.staking.impl.data.network.blockhain.bindings

import jp.co.soramitsu.common.data.network.runtime.binding.bindNumber
import jp.co.soramitsu.common.data.network.runtime.binding.getTyped
import jp.co.soramitsu.common.data.network.runtime.binding.incompatible
import jp.co.soramitsu.common.data.network.runtime.binding.requireType
import jp.co.soramitsu.common.data.network.runtime.binding.returnType
import jp.co.soramitsu.common.data.network.runtime.binding.storageReturnType
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.definitions.types.Type
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.shared_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.shared_utils.runtime.definitions.types.fromHexOrNull
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.staking.api.domain.model.DelegationAction
import jp.co.soramitsu.staking.api.domain.model.DelegationScheduledRequest
import jp.co.soramitsu.staking.api.domain.model.Round
import java.math.BigInteger

fun bindMinBond(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(scale, runtimeSnapshot, type)
}

fun bindMaxNominators(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger? {
    return bindNumberOrNull(scale, runtimeSnapshot, type)
}

fun bindNominatorsCount(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(scale, runtimeSnapshot, type)
}

fun bindRoundNumber(
    scale: String,
    runtime: RuntimeSnapshot
): BigInteger {
    val returnType = runtime.metadata.storageReturnType("ParachainStaking", "Round")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    return bindNumber(decoded.getTyped("current"))
}

fun bindRound(
    scale: String,
    runtime: RuntimeSnapshot
): Round {
    val returnType = runtime.metadata.storageReturnType("ParachainStaking", "Round")
    val decoded = returnType.fromHexOrNull(runtime, scale) as? Struct.Instance ?: incompatible()

    val current = bindNumber(decoded.getTyped("current"))
    val first = bindNumber(decoded.getTyped("first"))
    val length = bindNumber(decoded.getTyped("length"))

    return Round(
        current,
        first,
        length
    )
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

fun bindDelegationScheduledRequests(
    scale: String,
    runtime: RuntimeSnapshot
): List<DelegationScheduledRequest> {
    val type = runtime.metadata.parachainStaking().storage("DelegationScheduledRequests").returnType()

    val dynamicInstance = type.fromHexOrNull(runtime, scale)
    requireType<ArrayList<Struct.Instance>>(dynamicInstance)

    return dynamicInstance.map {
        val delegator = it.get<AccountId>("delegator") ?: incompatible()
        val whenExecutable = it.get<BigInteger>("whenExecutable").orZero()

        val actionName = it.getTyped<DictEnum.Entry<*>>("action").name
        val actionValue = it.getTyped<DictEnum.Entry<BigInteger>>("action").value

        DelegationScheduledRequest(
            delegator = delegator,
            whenExecutable = whenExecutable,
            action = DelegationAction.from(actionName),
            actionValue = actionValue
        )
    }
}

private fun bindNumber(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger {
    return bindNumber(type.fromHexOrNull(runtimeSnapshot, scale) ?: return scale.fromHex().fromUnsignedBytes())
}

private fun bindNumberOrNull(scale: String, runtimeSnapshot: RuntimeSnapshot, type: Type<*>): BigInteger? {
    return bindNumber(type.fromHexOrNull(runtimeSnapshot, scale) ?: return null)
}
