package jp.co.soramitsu.fearless_utils.runtime.extrinsic

import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.callOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull

/**
 * Requires that [call] and every call nested in its arguments were resolved against [metadata]: the pallet and the
 * function of each are the very objects [metadata] holds under their names. A call encodes with the indices and
 * argument types of the metadata it was resolved against, so a call from other metadata would be signed with a
 * metadata hash that does not describe it.
 */
internal fun requireCallsOf(metadata: RuntimeMetadata, call: GenericCall.Instance) {
    val pending = ArrayDeque<Any?>()
    pending.addLast(call)
    while (pending.isNotEmpty()) {
        when (val value = pending.removeLast()) {
            is GenericCall.Instance -> {
                val module = metadata.moduleOrNull(value.module.name)
                require(module === value.module && module.callOrNull(value.function.name) === value.function) {
                    "The call ${value.module.name}.${value.function.name} was resolved against other runtime metadata"
                }
                value.arguments.values.forEach(pending::addLast)
            }
            is List<*> -> value.forEach(pending::addLast)
            is Array<*> -> value.forEach(pending::addLast)
            is Map<*, *> -> value.values.forEach(pending::addLast)
            is Struct.Instance -> value.mapping.values.forEach(pending::addLast)
            is DictEnum.Entry<*> -> pending.addLast(value.value)
        }
    }
}
